package com.logus.app.notify

import com.google.firebase.Firebase
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/**
 * 앱 안 알림 하나(users/{uid}/notifications/{id}, 서버 함수가 만든다).
 * type: captureReminder(촬영 알림)·newLog(구성원 기록)·memberJoined(새 구성원)·coins(코인)·
 *       recapConsent·recapPublished·recapRejected(DAY RECAP 탐색 공개)
 */
data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val journeyId: String?,
    val requestId: String?,
    val read: Boolean,
    val createdAtMillis: Long,
)

/**
 * 알림 목록(2026-11-09 팀 그림: 홈 오른쪽 위 종 → 알림 화면)에서 Firestore 와 이야기하는 곳.
 * 알림은 서버만 만들고, 앱은 내 알림을 읽고 읽음 표시(read = true)만 한다(보안 규칙).
 * 실시간 리스너 대신 한 번 읽기(get)·개수 세기(count)를 쓴다(비용 관리).
 */
class NotificationRepository {
    private val db = Firebase.firestore

    private fun collection(uid: String) = db.collection("users").document(uid).collection("notifications")

    /** 최근 알림 limit 개(새것부터) */
    suspend fun loadRecent(uid: String, limit: Long = LIST_LIMIT): List<AppNotification> =
        collection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .mapNotNull { it.toNotification() }

    /** 안 읽은 알림 수(홈 종 버튼 배지). 개수 세기라 문서를 내려받지 않는다(읽기 1번) */
    suspend fun unreadCount(uid: String): Int =
        collection(uid).whereEqualTo("read", false)
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
            .toInt()

    /** 알림 하나를 읽음으로 */
    suspend fun markRead(uid: String, id: String) {
        collection(uid).document(id).update("read", true).await()
    }

    /** "모두 읽음": 안 읽은 알림을 한 번에 읽음으로(한 번에 최대 500개) */
    suspend fun markAllRead(uid: String) {
        val unread = collection(uid).whereEqualTo("read", false).limit(500).get().await()
        if (unread.isEmpty) return
        val batch = db.batch()
        unread.documents.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }

    companion object {
        /** 알림 화면에 보여 주는 최근 알림 수 */
        const val LIST_LIMIT = 50L
    }
}

/** 알림 문서 → AppNotification. 꼭 있어야 하는 칸(종류·제목)이 없으면 null */
private fun DocumentSnapshot.toNotification(): AppNotification? {
    val type = getString("type") ?: return null
    val title = getString("title") ?: return null
    return AppNotification(
        id = id,
        type = type,
        title = title,
        body = getString("body").orEmpty(),
        journeyId = getString("journeyId"),
        requestId = getString("requestId"),
        read = getBoolean("read") ?: false,
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
    )
}
