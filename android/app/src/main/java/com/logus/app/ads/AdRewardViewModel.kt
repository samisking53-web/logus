package com.logus.app.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.rewarded.RewardedAd
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** 광고 보고 코인 받기 팝업 상태 */
sealed interface AdRewardUiState {
    /** 팝업 닫힘 */
    data object Closed : AdRewardUiState

    /**
     * "광고 보고 코인 받을까요?" 팝업.
     * used: 오늘 받은 횟수(null 이면 읽는 중), busy: 광고 준비·코인 받는 중, message: 안내 글,
     * retryViewId: 광고는 끝까지 봤는데 인터넷 문제로 코인을 못 받았을 때 다시 받을 광고 ID("코인 다시 받기")
     */
    data class Ask(
        val used: Int?,
        val busy: Boolean = false,
        val message: String? = null,
        val retryViewId: String? = null,
    ) : AdRewardUiState

    /** 받은 결과: "+2 코인을 받았어요" · "오늘 N / 5회 사용" · 받은 뒤 보유 코인 */
    data class Done(val granted: Int, val used: Int, val coins: Long) : AdRewardUiState
}

/**
 * 홈(S01·S01-A) 보유 코인 상자를 누르면 여는 "광고 보고 코인 받기"(2026-11-11 요청).
 * 흐름: 팝업 열기(오늘 횟수 읽기 + 광고 미리 불러오기) → [광고 보기] → 광고를 끝까지 봄 → 서버 claimAdReward 로 2코인
 * → 결과 팝업([한 번 더 보기] [확인]). 하루 5번(10코인)까지. 홈의 보유 코인 숫자는 users/{uid} 를 지켜보고 있어서 저절로 바뀐다.
 */
class AdRewardViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AdRewardRepository()
    private val _state = MutableStateFlow<AdRewardUiState>(AdRewardUiState.Closed)
    val state: StateFlow<AdRewardUiState> = _state.asStateFlow()

    private var uid: String? = null
    /** 미리 불러 둔 광고(팝업을 열자마자 불러 두면 [광고 보기]를 눌렀을 때 덜 기다린다) */
    private var preload: Deferred<RewardedAd?>? = null
    /** 광고는 봤는데 코인을 못 받은 광고 ID. 팝업을 닫았다 다시 열어도 "코인 다시 받기"로 받을 수 있게 기억한다 */
    private var pendingViewId: String? = null

    fun open(uid: String) {
        this.uid = uid
        _state.value = AdRewardUiState.Ask(used = null, retryViewId = pendingViewId)
        viewModelScope.launch {
            val used = runCatching { repository.todayCount(uid) }
                .onFailure { Log.w(TAG, "오늘 광고 횟수 읽기 실패", it) }
                .getOrDefault(0)
            val current = _state.value
            if (current is AdRewardUiState.Ask && current.used == null) _state.value = current.copy(used = used)
        }
        if (pendingViewId == null) preloadAd()
    }

    fun close() {
        _state.value = AdRewardUiState.Closed
    }

    /** [광고 보기] / [한 번 더 보기] / [코인 다시 받기] */
    fun watch(activity: Activity) {
        val uid = uid ?: return
        val used = when (val current = _state.value) {
            is AdRewardUiState.Ask -> if (current.busy) return else current.used ?: 0
            is AdRewardUiState.Done -> current.used
            AdRewardUiState.Closed -> return
        }
        pendingViewId?.let { claim(it, used); return }
        if (used >= AdReward.DAILY_LIMIT) return
        _state.value = AdRewardUiState.Ask(used = used, busy = true)
        viewModelScope.launch {
            val ad = (preload ?: async { RewardedAds.load(getApplication()) }).await()
            preload = null
            if (ad == null) {
                _state.value = AdRewardUiState.Ask(used, message = "지금은 볼 수 있는 광고가 없어요. 잠시 뒤 다시 눌러 주세요.")
                return@launch
            }
            val viewId = UUID.randomUUID().toString()
            when (RewardedAds.show(activity, ad, uid, viewId)) {
                AdOutcome.EARNED -> claim(viewId, used)
                AdOutcome.CLOSED_EARLY -> {
                    _state.value = AdRewardUiState.Ask(used, message = "광고를 끝까지 보면 코인을 받을 수 있어요.")
                    preloadAd()
                }
                AdOutcome.FAILED -> {
                    _state.value = AdRewardUiState.Ask(used, message = "광고를 보여 주지 못했어요. 다시 눌러 주세요.")
                    preloadAd()
                }
            }
        }
    }

    /** 서버에서 코인 받기. 같은 viewId 는 서버가 한 번만 주므로 실패하면 같은 ID 로 다시 시도할 수 있다 */
    private fun claim(viewId: String, used: Int) {
        pendingViewId = viewId
        _state.value = AdRewardUiState.Ask(used = used, busy = true)
        viewModelScope.launch {
            runCatching { repository.claim(viewId) }
                .onSuccess { result ->
                    pendingViewId = null
                    _state.value = when {
                        result.granted > 0 -> AdRewardUiState.Done(result.granted, result.count, result.coins)
                        result.limitReached -> AdRewardUiState.Ask(result.count, message = LIMIT_MESSAGE)
                        else -> AdRewardUiState.Ask(result.count, message = "이미 코인을 받은 광고예요.")
                    }
                    if (result.count < AdReward.DAILY_LIMIT) preloadAd()
                }
                .onFailure {
                    Log.w(TAG, "광고 코인 받기 실패", it)
                    _state.value = AdRewardUiState.Ask(
                        used = used,
                        message = "코인을 받지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.",
                        retryViewId = viewId,
                    )
                }
        }
    }

    /** 광고를 미리 불러 둔다(불러오는 중이거나 이미 불러 둔 광고가 있으면 그대로, 실패했으면 다시) */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun preloadAd() {
        val current = preload
        if (current != null && current.isActive) return
        if (current != null && current.isCompleted && !current.isCancelled && current.getCompleted() != null) return
        preload = viewModelScope.async { RewardedAds.load(getApplication()) }
    }

    companion object {
        private const val TAG = "AdReward"
        const val LIMIT_MESSAGE = "오늘은 ${AdReward.DAILY_LIMIT}번 모두 받았어요. 내일 오전 9시에 다시 받을 수 있어요."
    }
}
