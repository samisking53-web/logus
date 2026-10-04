package com.logus.app.journey

import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
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
                )
            }
            .firstOrNull { it.endDate >= today } // "YYYY-MM-DD" 글자는 사전 순서가 날짜 순서와 같다
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
