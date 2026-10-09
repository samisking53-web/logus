package com.logus.app.journey

import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Firestore journeys/{journeyId} 문서 중 홈 화면에 필요한 값 (CLAUDE.md "데이터 모델") */
data class Journey(
    val id: String,
    val name: String,
    /** 현지 달력 날짜 "YYYY-MM-DD" */
    val startDate: String,
    val endDate: String,
    val memberIds: List<String>,
    val memberCount: Int,
    /** 여정을 만든 사람의 6자리 초대 코드. 여정을 만들 때 정해지고 바뀌지 않는다(다른 구성원은 자기 코드가 따로 있다: myInviteCode) */
    val inviteCode: String? = null,
    /** 여정을 만든 사람 uid. 이 사람의 촬영 알림 간격이 모든 구성원의 공통 알림이 된다 */
    val ownerId: String? = null,
    /** 여정 도시 이름(위치 확인 지도에서 GPS 를 못 쓸 때 시작 위치) */
    val city: String = "",
)

/** 새로 만든 여정: ID 와 6자리 초대 코드(여정이 끝날 때까지 쓸 수 있다) */
data class CreatedJourney(val journeyId: String, val inviteCode: String)

/** 초대 코드로 확인한 여정 요약(I01 초대 확인 화면). 아직 참여하기 전이다 */
data class InvitePreview(
    val code: String,
    val journeyName: String,
    val city: String,
    val startDate: String,
    val endDate: String,
    val memberCount: Int,
    val inviterName: String,
    /** 여정을 만든 사람의 촬영 알림 간격(1·2·3시간, 없으면 null) */
    val notifyIntervalHours: Int?,
    val alreadyMember: Boolean,
)

/** 여정 구성원 한 명(홈의 동그란 이름 표시용) */
data class Member(val uid: String, val nickname: String)

/**
 * 여정 데이터 담당 (백엔드: Firestore)
 * 보안 규칙: 여정은 memberIds 에 있는 사람만 읽을 수 있다. 그래서 목록을 읽을 때 꼭
 * "memberIds 에 내 uid 가 들어 있는 여정"으로 조건을 건다(조건이 없으면 규칙이 거부한다).
 */
class JourneyRepository {
    private val db = Firebase.firestore

    // 서버 함수는 서울 리전(asia-northeast3)에 있다(functions/src/globalOptions.ts)
    private val functions = Firebase.functions("asia-northeast3")

    /**
     * S02 새 여정 만들기: 서버 함수 createJourney 를 부른다.
     * 여정 문서·내 멤버 문서(알림 간격)·초대 요약을 서버가 한 번에 만든다(앱은 journeys 를 직접 만들 수 없다).
     * 날짜는 "YYYY-MM-DD", notifyIntervalHours 는 1·2·3 또는 null(촬영 알림 받지 않기).
     * 돌려주는 값: 새 여정 ID 와 6자리 초대 코드
     */
    suspend fun createJourney(
        name: String,
        city: String,
        country: String,
        startDate: String,
        endDate: String,
        notifyIntervalHours: Int?,
    ): CreatedJourney {
        val result = functions.getHttpsCallable("createJourney")
            .call(
                hashMapOf(
                    "name" to name,
                    "city" to city,
                    "country" to country,
                    "startDate" to startDate,
                    "endDate" to endDate,
                    "notifyIntervalHours" to notifyIntervalHours,
                ),
            )
            .await()
        val data = result.getData() as? Map<*, *>
        return CreatedJourney(
            journeyId = data?.get("journeyId") as? String ?: error("서버가 여정 ID를 돌려주지 않았어요."),
            inviteCode = data?.get("inviteCode") as? String ?: error("서버가 초대 코드를 돌려주지 않았어요."),
        )
    }

    /**
     * 초대 코드 확인: 서버 함수 previewInvite 를 부른다(참여는 하지 않는다).
     * 앱은 invites 를 직접 읽지 못해서(보안 규칙) 서버가 코드를 확인하고 요약만 돌려준다.
     * 없는 코드·만료·하루 입력 횟수 초과면 FirebaseFunctionsException 이 난다.
     */
    suspend fun previewInvite(code: String): InvitePreview {
        val result = functions.getHttpsCallable("previewInvite")
            .call(hashMapOf("inviteCode" to code))
            .await()
        val data = result.getData() as? Map<*, *> ?: error("서버 응답을 읽을 수 없어요.")
        return InvitePreview(
            code = code,
            journeyName = data["name"] as? String ?: "",
            city = data["city"] as? String ?: "",
            startDate = data["startDate"] as? String ?: "",
            endDate = data["endDate"] as? String ?: "",
            memberCount = (data["memberCount"] as? Number)?.toInt() ?: 1,
            inviterName = data["inviterName"] as? String ?: "친구",
            notifyIntervalHours = (data["notifyIntervalHours"] as? Number)?.toInt(),
            alreadyMember = data["alreadyMember"] as? Boolean ?: false,
        )
    }

    /**
     * I01 "초대 수락하기": 서버 함수 joinJourney 로 여정에 참여한다.
     * 서버가 memberIds·멤버 문서·인원수를 한 번에 고친다(앱은 memberIds 를 직접 고칠 수 없다).
     * 이미 구성원이면 서버가 아무것도 바꾸지 않고 성공으로 돌려준다.
     * 돌려주는 값: 참여한 여정 ID
     */
    suspend fun joinJourney(code: String): String {
        val result = functions.getHttpsCallable("joinJourney")
            .call(hashMapOf("inviteCode" to code))
            .await()
        val data = result.getData() as? Map<*, *>
        return data?.get("journeyId") as? String ?: error("서버가 여정 ID를 돌려주지 않았어요.")
    }

    /**
     * 이 여정에서 쓰는 "내" 초대 코드(구성원마다 다르고, 한 번 만들면 바뀌지 않는다).
     * 이 코드로 새 친구가 들어오면 나에게 30코인이 쌓인다(서버 joinJourney).
     * - 여정을 만든 사람: 여정 문서의 inviteCode
     * - 다른 구성원: 내 멤버 문서(members/{uid})의 inviteCode. 아직 없으면 서버 함수 getInviteCode 가 새로 만들어 준다
     *   (앱은 invites 를 직접 쓸 수 없어서 코드는 서버만 만든다)
     */
    suspend fun myInviteCode(journey: Journey, uid: String): String {
        if (journey.ownerId == uid) journey.inviteCode?.let { return it }
        val member = db.collection("journeys").document(journey.id)
            .collection("members").document(uid)
            .get()
            .await()
        member.getString("inviteCode")?.let { return it }
        val result = functions.getHttpsCallable("getInviteCode")
            .call(hashMapOf("journeyId" to journey.id))
            .await()
        val data = result.getData() as? Map<*, *>
        return data?.get("inviteCode") as? String ?: error("서버가 초대 코드를 돌려주지 않았어요.")
    }

    /** 여정 문서 한 개 읽기(내가 구성원인 여정만 읽을 수 있다). 없으면 null */
    suspend fun loadJourney(journeyId: String): Journey? {
        val doc = db.collection("journeys").document(journeyId).get().await()
        return Journey(
            id = doc.id,
            name = doc.getString("name") ?: return null,
            startDate = doc.getString("startDate") ?: return null,
            endDate = doc.getString("endDate") ?: return null,
            memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            memberCount = doc.getLong("memberCount")?.toInt() ?: 1,
            inviteCode = doc.getString("inviteCode"),
            ownerId = doc.getString("ownerId"),
            city = doc.getString("city").orEmpty(),
        )
    }

    /**
     * 오늘(today, "YYYY-MM-DD") 진행 중인 내 여정. 없으면 null.
     * 시작일이 오늘 이전인 내 여정을 최근 시작한 순서로 5개만 읽고(비용 관리: limit),
     * 그중 종료일이 오늘 이후인 첫 여정을 고른다.
     * 색인: firestore.indexes.json 의 journeys(memberIds 포함 + startDate 내림차순)을 쓴다.
     */
    suspend fun findOngoingJourney(uid: String, today: String): Journey? {
        val snap = db.collection("journeys")
            .whereArrayContains("memberIds", uid)
            .whereLessThanOrEqualTo("startDate", today)
            .orderBy("startDate", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .await()
        return snap.documents
            .mapNotNull { doc ->
                Journey(
                    id = doc.id,
                    name = doc.getString("name") ?: return@mapNotNull null,
                    startDate = doc.getString("startDate") ?: return@mapNotNull null,
                    endDate = doc.getString("endDate") ?: return@mapNotNull null,
                    memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    memberCount = doc.getLong("memberCount")?.toInt() ?: 1,
                    inviteCode = doc.getString("inviteCode"),
                    ownerId = doc.getString("ownerId"),
                    city = doc.getString("city").orEmpty(),
                )
            }
            .firstOrNull { it.endDate >= today } // "YYYY-MM-DD" 글자는 사전 순서가 날짜 순서와 같다
    }

    /**
     * 공통 촬영 알림 간격(1·2·3시간, 받지 않으면 null): 여정을 만든 사람의 멤버 문서(members/{ownerId})에서 읽는다.
     * 여정을 만든 사람이 정한 간격이 모든 구성원에게 똑같이 적용된다(스토리보드 6쪽).
     */
    suspend fun loadCommonNotifyInterval(journey: Journey): Int? {
        val ownerId = journey.ownerId ?: return null
        val snap = db.collection("journeys").document(journey.id)
            .collection("members").document(ownerId)
            .get()
            .await()
        return snap.getLong("notifyIntervalHours")?.toInt()
    }

    /** 구성원 닉네임(최대 limit 명). users/{uid} 를 한 명씩 동시에 읽는다. */
    suspend fun loadMembers(memberIds: List<String>, limit: Int = 4): List<Member> = coroutineScope {
        memberIds.take(limit)
            .map { uid ->
                async {
                    val snap = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
                    Member(uid = uid, nickname = snap?.getString("nickname") ?: "?")
                }
            }
            .awaitAll()
    }
}
