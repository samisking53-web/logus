package com.logus.app.notify

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

/**
 * 촬영 알림을 받을 "이 폰" 등록 담당 (백엔드: Firestore + FCM)
 * users/{uid}/devices/{FCM 토큰} 에 토큰과 폰 시간대를 저장한다. 서버 함수 sendCaptureReminders 가
 * 이 시간대로 폰 시각 09~23시를 계산해서 알림을 보낸다. 보안 규칙: 본인만 읽고 쓰고 지운다.
 */
class DeviceRepository {
    private val db = Firebase.firestore

    /**
     * 이 폰을 등록한다(홈에 들어올 때마다·토큰이 바뀔 때). 여행 중 시간대가 바뀌면 다음에 앱을 열 때 새 시간대로 바뀐다.
     * token 을 넘기지 않으면 FCM 에서 지금 토큰을 받아 온다.
     */
    suspend fun register(uid: String, token: String? = null) {
        val fcmToken = token ?: Firebase.messaging.token.await()
        db.collection("users").document(uid)
            .collection("devices").document(fcmToken)
            .set(
                mapOf(
                    "token" to fcmToken,
                    "timeZone" to ZoneId.systemDefault().id,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    /** 로그아웃할 때: 이 폰을 지워서 다음에 이 폰으로 로그인한 다른 사람이 내 알림을 받지 않게 한다 */
    suspend fun unregister(uid: String) {
        val fcmToken = Firebase.messaging.token.await()
        db.collection("users").document(uid)
            .collection("devices").document(fcmToken)
            .delete()
            .await()
    }
}
