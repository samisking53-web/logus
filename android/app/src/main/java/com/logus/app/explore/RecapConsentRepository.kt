package com.logus.app.explore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** DAY RECAP 장면 하나: 시각("11:00")과 그 장면에 함께 나오는 기록 ID(위에서 아래 칸 순서, 1~4개) */
data class ConsentScene(val label: String, val logIds: List<String>)

/** 내가 답해야 하는 공개 동의 요청(recapPublishRequests/{journeyId}_{date}) */
data class ConsentRequest(
    val id: String,
    val journeyName: String,
    val city: String,
    val date: String,
    val requesterName: String,
    /** 동의해야 하는 사람 수 / 이미 동의한 사람 수 */
    val memberCount: Int,
    val approvedCount: Int,
)

/** 요청 결과: pending(다른 구성원 동의를 기다림) · published(탐색에 올라감) · rejected(누군가 동의하지 않음) */
data class ConsentResult(val status: String, val alreadyRequested: Boolean = false)

/**
 * DAY RECAP 탐색 공개 동의 데이터 담당.
 * 1) 구성원 한 사람이 requestPublish → 서버가 다른 구성원 모두에게 동의 요청 알림(FCM)을 보낸다.
 * 2) 알림을 누르거나 앱을 켜면 loadPending 으로 내가 아직 답하지 않은 요청을 찾아 동의 팝업을 띄운다.
 * 3) answer(동의/거절) → 모두 동의하면 서버가 공개 사본을 만들어 탐색(S08)에 올린다. 한 명이라도 거절하면 취소.
 */
class RecapConsentRepository {
    private val db = Firebase.firestore
    private val functions = Firebase.functions("asia-northeast3")

    /**
     * 그날 DAY RECAP 을 탐색에 올리자고 요청한다(요청한 사람은 동의한 것으로 본다).
     * DAY RECAP 화면의 "탐색에 자동 올리기" 버튼에서 부를 예정이다(장면은 DAY RECAP 과 같은 buildRecapScenes 결과).
     */
    suspend fun requestPublish(journeyId: String, date: String, scenes: List<ConsentScene>): ConsentResult {
        val result = functions.getHttpsCallable("requestRecapPublish")
            .withTimeout(5, TimeUnit.MINUTES)
            .call(
                hashMapOf(
                    "journeyId" to journeyId,
                    "date" to date,
                    "scenes" to scenes.map { hashMapOf("label" to it.label, "logIds" to it.logIds) },
                ),
            )
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<String, Any?>
        return ConsentResult(
            status = data?.get("status") as? String ?: "pending",
            alreadyRequested = data?.get("alreadyRequested") as? Boolean ?: false,
        )
    }

    /** 내가 아직 답하지 않은 공개 동의 요청(오래된 것부터) */
    suspend fun loadPending(uid: String): List<ConsentRequest> =
        db.collection("recapPublishRequests")
            .whereArrayContains("memberIds", uid)
            .whereEqualTo("status", "pending")
            .limit(10)
            .get().await()
            .documents
            .filter { doc ->
                @Suppress("UNCHECKED_CAST")
                val approvals = doc.get("approvals") as? Map<String, Any?>
                approvals?.get(uid) == null
            }
            .sortedBy { it.getTimestamp("createdAt")?.seconds ?: 0L }
            .map { doc ->
                @Suppress("UNCHECKED_CAST")
                val approvals = doc.get("approvals") as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val members = doc.get("memberIds") as? List<String>
                ConsentRequest(
                    id = doc.id,
                    journeyName = doc.getString("journeyName") ?: "여정",
                    city = doc.getString("city").orEmpty(),
                    date = doc.getString("date").orEmpty(),
                    requesterName = doc.getString("requesterName") ?: "구성원",
                    memberCount = members?.size ?: 0,
                    approvedCount = approvals?.values?.count { it == true } ?: 0,
                )
            }

    /** 동의(approve = true) 또는 거절. 모두 동의했으면 서버가 이때 탐색에 올린다(영상 복사로 조금 걸릴 수 있다) */
    suspend fun answer(requestId: String, approve: Boolean): ConsentResult {
        val result = functions.getHttpsCallable("answerRecapPublish")
            .withTimeout(5, TimeUnit.MINUTES)
            .call(hashMapOf("requestId" to requestId, "approve" to approve))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<String, Any?>
        return ConsentResult(status = data?.get("status") as? String ?: "pending")
    }
}
