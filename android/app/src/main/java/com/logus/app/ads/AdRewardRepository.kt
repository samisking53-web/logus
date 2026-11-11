package com.logus.app.ads

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * claimAdReward 가 돌려준 값. granted: 이번에 받은 코인(0이면 못 받음), count: 오늘 받은 횟수(이번 것 포함),
 * coins: 받은 뒤 보유 코인, alreadyClaimed: 같은 광고로 이미 받음, limitReached: 오늘 5번을 다 씀
 */
data class AdClaim(
    val granted: Int,
    val count: Int,
    val coins: Long,
    val alreadyClaimed: Boolean,
    val limitReached: Boolean,
)

/**
 * 광고 보고 코인 받기의 Firebase 쪽(2026-11-11 요청).
 * - 오늘 받은 횟수: adDaily/{uid}(date = UTC "YYYY-MM-DD", count). 본인만 읽을 수 있다(보안 규칙). 날짜가 오늘이 아니면 0.
 * - 코인 받기: 서버 함수 claimAdReward(viewId). 하루 5번·2코인 확인과 코인 지급은 서버가 한다(앱은 코인을 직접 못 바꿈).
 */
class AdRewardRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = Firebase.functions("asia-northeast3"),
) {
    /** 오늘(UTC) 광고로 코인을 받은 횟수 */
    suspend fun todayCount(uid: String): Int {
        val snap = db.collection("adDaily").document(uid).get().await()
        return if (snap.getString("date") == utcToday()) (snap.getLong("count") ?: 0L).toInt() else 0
    }

    /** 광고를 끝까지 본 뒤 코인을 받는다. 같은 viewId 로 다시 불러도 서버가 한 번만 준다(인터넷이 끊겨 다시 시도할 때 안전) */
    suspend fun claim(viewId: String): AdClaim {
        val result = functions.getHttpsCallable("claimAdReward").call(hashMapOf("viewId" to viewId)).await()
        val data = result.getData() as? Map<*, *>
        return AdClaim(
            granted = (data?.get("granted") as? Number)?.toInt() ?: 0,
            count = (data?.get("count") as? Number)?.toInt() ?: 0,
            coins = (data?.get("coins") as? Number)?.toLong() ?: 0L,
            alreadyClaimed = data?.get("alreadyClaimed") == true,
            limitReached = data?.get("limitReached") == true,
        )
    }

    private fun utcToday(): String = LocalDate.now(ZoneOffset.UTC).toString()
}
