package com.logus.app.journey

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 새 여정 만들기 안에서 지금 보이는 화면 */
enum class NewJourneyStep {
    FORM, // S02 새 여정 만들기
    CALENDAR, // S03 여행 기간 선택
}

/** S02·S03 입력 상태 */
data class NewJourneyUiState(
    val step: NewJourneyStep = NewJourneyStep.FORM,
    val name: String = "",
    /** 도시 입력칸 글자 */
    val cityQuery: String = "",
    /** 추천 목록에서 고른 도시(고르지 않고 직접 입력만 했으면 null) */
    val selectedCity: City? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** 촬영 알림 간격(1·2·3시간) */
    val notifyHours: Int = 2,
    /** "촬영 알림 받지 않기"를 골랐는지 */
    val notifyOff: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** 도시 추천 목록(이미 고른 도시와 입력이 같으면 다시 보여 주지 않는다) */
    val suggestions: List<City>
        get() = if (selectedCity != null && selectedCity.name == cityQuery.trim()) emptyList() else Cities.search(cityQuery)

    /** 저장할 수 있는지: 이름 1~40자, 도시 1~60자, 기간 선택 완료 */
    val canSave: Boolean
        get() = name.trim().length in 1..NAME_MAX &&
            cityQuery.trim().length in 1..CITY_MAX &&
            startDate != null && endDate != null && !saving

    companion object {
        const val NAME_MAX = 40 // 서버 함수 createJourney 의 제한과 같다
        const val CITY_MAX = 60
    }
}

/**
 * S02 새 여정 만들기·S03 여행 기간 달력 상태 관리.
 * 저장하면 서버 함수 createJourney 가 여정을 만든다. 만든 뒤 홈으로 돌아가면, 시작일이 오늘인 여정은
 * 홈이 폰 카메라를 연다(미래 여정은 시작일에 앱을 열 때 연다. ui/MainScreen.kt 참고).
 */
class NewJourneyViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(NewJourneyUiState())
    val state: StateFlow<NewJourneyUiState> = _state.asStateFlow()

    /** 새로 만들기를 시작할 때 이전 입력을 지운다 */
    fun reset() {
        _state.value = NewJourneyUiState()
    }

    fun updateName(value: String) = update {
        if (value.length <= NewJourneyUiState.NAME_MAX) it.copy(name = value, error = null) else it
    }

    /** 도시 입력: 글자를 바꾸면 고른 도시는 풀린다(직접 입력한 글자 그대로 저장할 수도 있다) */
    fun updateCityQuery(value: String) = update {
        if (value.length <= NewJourneyUiState.CITY_MAX) {
            it.copy(cityQuery = value, selectedCity = it.selectedCity?.takeIf { c -> c.name == value.trim() }, error = null)
        } else {
            it
        }
    }

    fun selectCity(city: City) = update { it.copy(cityQuery = city.name, selectedCity = city, error = null) }

    fun clearCity() = update { it.copy(cityQuery = "", selectedCity = null) }

    fun openCalendar() = update { it.copy(step = NewJourneyStep.CALENDAR) }

    fun closeCalendar() = update { it.copy(step = NewJourneyStep.FORM) }

    /** S03 "기간 선택 완료" */
    fun confirmPeriod(start: LocalDate, end: LocalDate) = update {
        it.copy(startDate = start, endDate = end, step = NewJourneyStep.FORM, error = null)
    }

    fun selectNotifyHours(hours: Int) = update { it.copy(notifyHours = hours, notifyOff = false) }

    fun toggleNotifyOff() = update { it.copy(notifyOff = !it.notifyOff) }

    /**
     * 위쪽 "저장 →". 성공하면 onSaved(시작일)를 부른다.
     * 국가는 추천 목록에서 고른 경우에만 채운다(직접 입력한 도시는 국가를 모른다).
     */
    fun save(onSaved: (startDate: LocalDate) -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val start = s.startDate ?: return
        val end = s.endDate ?: return
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val city = s.selectedCity
                repository.createJourney(
                    name = s.name.trim(),
                    city = city?.name ?: s.cityQuery.trim(),
                    country = city?.country ?: "",
                    startDate = start.toString(), // "YYYY-MM-DD"
                    endDate = end.toString(),
                    notifyIntervalHours = if (s.notifyOff) null else s.notifyHours,
                )
                _state.value = _state.value.copy(saving = false)
                onSaved(start)
            } catch (e: Exception) {
                Log.w(TAG, "여정 만들기 실패 (createJourney 함수 배포 확인)", e)
                _state.value = _state.value.copy(saving = false, error = e.toKoreanMessage())
            }
        }
    }

    private inline fun update(change: (NewJourneyUiState) -> NewJourneyUiState) {
        val current = _state.value
        if (current.saving) return
        _state.value = change(current)
    }

    private companion object {
        const val TAG = "NewJourneyViewModel"
    }
}

/** 서버 함수 오류를 사용자에게 보여 줄 한국어 문구로 */
private fun Exception.toKoreanMessage(): String = when {
    this is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
    this is FirebaseFunctionsException -> when (code) {
        // 서버(functions/src/common.ts)가 보낸 한국어 문구를 그대로 보여 준다
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> message ?: "입력한 내용을 확인해 주세요."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "다시 로그인해 주세요."
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "인터넷 연결을 확인해 주세요."
        FirebaseFunctionsException.Code.NOT_FOUND -> "여정 만들기 기능이 아직 준비되지 않았어요. (서버 함수 배포 필요)"
        else -> "여정을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
    }
    else -> "여정을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
}
