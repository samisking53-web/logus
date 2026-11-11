package com.logus.app.journeydetail

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await

/**
 * 프리미엄 추출(50코인, 워터마크 없음)의 Firebase 쪽(2026-11-11 팀 요청).
 * - 이날 열었는지: users/{uid}/premiumExports/{journeyId}_{date} 가 있으면 연 것(본인만 읽음, 쓰기는 서버만).
 * - 열기: 서버 함수 unlockPremiumExport(journeyId, date). 코인 빼기·열어 둔 기록·코인 내역을 서버가 한 번에 한다.
 *   이미 연 날이면 서버가 코인을 빼지 않는다.
 */
class PremiumExportRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val db: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = Firebase.functions("asia-northeast3"),
) {
    private fun uid(): String = auth.currentUser?.uid ?: error("로그인이 필요해요.")

    /** 이 여정의 이날(date = "YYYY-MM-DD") 프리미엄 추출을 이미 열었는지 */
    suspend fun isUnlocked(journeyId: String, date: String): Boolean =
        db.collection("users").document(uid()).collection("premiumExports").document("${journeyId}_$date")
            .get().await().exists()

    /** 지금 보유 코인(확인 팝업 "현재 보유 N 코인") */
    suspend fun myCoins(): Long =
        db.collection("users").document(uid()).get().await().getLong("coins") ?: 0L

    /** "50코인 사용": 서버가 코인을 빼고 이날을 열어 둔다. 남은 코인을 돌려준다 */
    suspend fun unlock(journeyId: String, date: String): Long {
        val result = functions.getHttpsCallable("unlockPremiumExport")
            .call(hashMapOf("journeyId" to journeyId, "date" to date))
            .await()
        val data = result.getData() as? Map<*, *>
        return (data?.get("coins") as? Number)?.toLong() ?: 0L
    }
}
