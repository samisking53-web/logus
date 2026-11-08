package com.logus.app.explore

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
import com.logus.app.journey.Member
import com.logus.app.record.GeoPoint
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder

/** 장소 열람에 쓰는 코인(서버 unlockRecapPlaces 의 PLACE_VIEW_COINS 와 같아야 한다) */
const val PLACE_VIEW_COINS = 20

/** 한 번에 읽는 공개 리캡 수(비용 관리: 위아래로 넘기다 끝에 가까워지면 다음 묶음) */
const val EXPLORE_PAGE_SIZE = 10

/** 공개 리캡의 영상 한 칸(구성원 한 사람의 영상) */
data class PublicClip(
    val logId: String,
    val authorId: String,
    val videoPath: String,
    val thumbPath: String?,
    val capturedAtMillis: Long?,
)

/** 공개 리캡의 장면 하나(같은 시간대 구성원 영상들을 위·가운데·아래로 함께 재생) */
data class PublicScene(val label: String, val clips: List<PublicClip>)

/**
 * 탐색에 공개된 DAY RECAP(publicRecaps/{recapId}). 구성원 모두 동의해서 서버가 만든 공개 사본이다.
 * 좌표·장소 이름은 여기 없고 places 에 따로 있다(20코인을 쓴 사람과 구성원만 읽음).
 */
data class PublicRecap(
    val id: String,
    val journeyId: String,
    val journeyName: String,
    val city: String,
    val country: String?,
    /** 그날 "YYYY-MM-DD" */
    val date: String,
    val memberIds: List<String>,
    /** 영상에 나오는 사람(구성원 순서). 색은 이 순서로 MemberColors */
    val members: List<Member>,
    val scenes: List<PublicScene>,
    val themes: List<String>,
) {
    /** 목록에 보여 줄 대표 화면: 첫 장면 첫 칸 */
    val coverThumbPath: String? get() = scenes.firstOrNull()?.clips?.firstOrNull { it.thumbPath != null }?.thumbPath
    fun member(uid: String): Member? = members.firstOrNull { it.uid == uid }
    fun colorIndex(uid: String): Int = members.indexOfFirst { it.uid == uid }.coerceAtLeast(0)
}

/** 장소(places/{logId}): 좌표와 장소 이름 */
data class PublicPlace(val logId: String, val authorId: String, val location: GeoPoint?, val placeName: String?, val capturedAtMillis: Long?)

/** 좋아요·저장 목록 한 줄(모아보기) */
data class RecapMark(val recapId: String, val journeyName: String, val city: String, val date: String, val thumbPath: String?)

/** 좋아요(recapLikes) · 저장(recapSaves) */
enum class MarkKind(val collection: String, val label: String) {
    LIKE("recapLikes", "좋아요"),
    SAVE("recapSaves", "저장"),
}

/** 한 묶음 읽은 결과와 다음 묶음 시작점(없으면 끝) */
data class RecapBatch(val recaps: List<PublicRecap>, val next: DocumentSnapshot?)

/**
 * 탐색(S08·S09)·모아보기·장소 열람 데이터 담당(백엔드: Firestore·Storage·서버 함수).
 * 공개 사본 파일은 Storage public/ 아래에 있고 누구나 읽을 수 있어서, 파일마다 주소를 묻지 않고 바로 주소를 만든다.
 */
class ExploreRepository {
    private val db = Firebase.firestore
    private val functions = Firebase.functions("asia-northeast3")
    private val bucket = Firebase.storage.reference.bucket

    /** S08 공개 영상 피드: 최근 공개된 순서로 EXPLORE_PAGE_SIZE 개씩 */
    suspend fun loadFeed(after: DocumentSnapshot?): RecapBatch {
        var query = db.collection("publicRecaps")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(EXPLORE_PAGE_SIZE.toLong())
        if (after != null) query = query.startAfter(after)
        val snap = query.get().await()
        return RecapBatch(snap.documents.mapNotNull { it.toRecap() }, snap.documents.lastOrNull()?.takeIf { snap.size() >= EXPLORE_PAGE_SIZE })
    }

    /**
     * S09 검색: 지역(도시·나라)·여정 이름·테마 낱말 중 하나라도 맞는 공개 리캡(최근 공개 순).
     * "포르투 카페"처럼 띄어 쓰면 낱말마다 찾는다(소문자로 바꿔 서버가 만든 keywords 와 비교).
     */
    suspend fun search(text: String, after: DocumentSnapshot?): RecapBatch {
        val words = searchWords(text)
        if (words.isEmpty()) return RecapBatch(emptyList(), null)
        var query = db.collection("publicRecaps")
            .whereArrayContainsAny("keywords", words)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(EXPLORE_PAGE_SIZE.toLong())
        if (after != null) query = query.startAfter(after)
        val snap = query.get().await()
        return RecapBatch(snap.documents.mapNotNull { it.toRecap() }, snap.documents.lastOrNull()?.takeIf { snap.size() >= EXPLORE_PAGE_SIZE })
    }

    suspend fun loadRecap(recapId: String): PublicRecap? =
        db.collection("publicRecaps").document(recapId).get().await().toRecap()

    /** 장소(좌표·장소 이름). 열람(20코인)했거나 구성원일 때만 보안 규칙이 허락한다 */
    suspend fun loadPlaces(recapId: String): Map<String, PublicPlace> =
        db.collection("publicRecaps").document(recapId).collection("places").limit(100).get().await()
            .documents.associate { doc ->
                @Suppress("UNCHECKED_CAST")
                val loc = doc.get("location") as? Map<String, Any?>
                val lat = (loc?.get("lat") as? Number)?.toDouble()
                val lng = (loc?.get("lng") as? Number)?.toDouble()
                doc.id to PublicPlace(
                    logId = doc.id,
                    authorId = doc.getString("authorId").orEmpty(),
                    location = if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) GeoPoint(lat, lng) else null,
                    placeName = doc.getString("placeName"),
                    capturedAtMillis = doc.getTimestamp("capturedAt")?.toDate()?.time,
                )
            }

    /** 이 리캡의 장소를 이미 열람했는지(코인을 다시 쓰지 않게 팝업을 건너뛴다) */
    suspend fun isUnlocked(uid: String, recapId: String): Boolean =
        db.collection("users").document(uid).collection("placeUnlocks").document(recapId).get().await().exists()

    suspend fun myCoins(uid: String): Long =
        db.collection("users").document(uid).get().await().getLong("coins") ?: 0L

    /** "20코인 사용": 서버가 코인을 빼고 열람 기록을 만든다. 남은 코인을 돌려준다 */
    suspend fun unlockPlaces(recapId: String): Long {
        val result = functions.getHttpsCallable("unlockRecapPlaces")
            .call(hashMapOf("recapId" to recapId))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<String, Any?>
        return (data?.get("coins") as? Number)?.toLong() ?: 0L
    }

    /** 내가 좋아요·저장한 리캡 ID(피드에 채운 하트·책갈피를 그리려고 처음에 한 번 읽는다, 최근 200개) */
    suspend fun loadMarkedIds(uid: String, kind: MarkKind): Set<String> =
        db.collection("users").document(uid).collection(kind.collection)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
            .documents.map { it.id }.toSet()

    /** 모아보기 목록(최근에 누른 순서) */
    suspend fun loadMarks(uid: String, kind: MarkKind): List<RecapMark> =
        db.collection("users").document(uid).collection(kind.collection)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100).get().await()
            .documents.map {
                RecapMark(
                    recapId = it.id,
                    journeyName = it.getString("journeyName").orEmpty(),
                    city = it.getString("city").orEmpty(),
                    date = it.getString("date").orEmpty(),
                    thumbPath = it.getString("thumbPath"),
                )
            }

    /** 좋아요·저장 켜기(목록에 보여 줄 요약을 함께 저장) / 끄기 */
    suspend fun setMark(uid: String, kind: MarkKind, recap: PublicRecap, on: Boolean) {
        val ref = db.collection("users").document(uid).collection(kind.collection).document(recap.id)
        if (on) {
            ref.set(
                hashMapOf(
                    "journeyName" to recap.journeyName.take(100).ifEmpty { "여정" },
                    "city" to recap.city.take(100),
                    "date" to recap.date,
                    "thumbPath" to recap.coverThumbPath,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.delete().await()
        }
    }

    /** 공개 파일(public/...) 주소. 보안 규칙이 누구나 읽게 두어서 열쇠(token) 없이 바로 받을 수 있다 */
    fun publicUrl(path: String): String =
        "https://firebasestorage.googleapis.com/v0/b/$bucket/o/${URLEncoder.encode(path, "UTF-8")}?alt=media"

    private fun DocumentSnapshot.toRecap(): PublicRecap? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        val members = (get("members") as? List<Map<String, Any?>>).orEmpty().map {
            Member(
                uid = it["uid"] as? String ?: "",
                nickname = it["nickname"] as? String ?: "구성원",
                photoUrl = it["photoURL"] as? String,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val scenes = (get("scenes") as? List<Map<String, Any?>>).orEmpty().map { scene ->
            PublicScene(
                label = scene["label"] as? String ?: "",
                clips = (scene["clips"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { clip ->
                    val videoPath = clip["videoPath"] as? String ?: return@mapNotNull null
                    PublicClip(
                        logId = clip["logId"] as? String ?: "",
                        authorId = clip["authorId"] as? String ?: "",
                        videoPath = videoPath,
                        thumbPath = clip["thumbPath"] as? String,
                        capturedAtMillis = (clip["capturedAt"] as? Timestamp)?.toDate()?.time,
                    )
                },
            )
        }.filter { it.clips.isNotEmpty() }
        if (scenes.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        return PublicRecap(
            id = id,
            journeyId = getString("journeyId").orEmpty(),
            journeyName = getString("journeyName") ?: "여정",
            city = getString("city").orEmpty(),
            country = getString("country"),
            date = getString("date").orEmpty(),
            memberIds = (get("memberIds") as? List<String>).orEmpty(),
            members = members,
            scenes = scenes,
            themes = (get("themes") as? List<String>).orEmpty(),
        )
    }

    companion object {
        /** 검색어 → 낱말(소문자, 띄어쓰기·쉼표·# 로 나눔, 최대 10개) */
        fun searchWords(text: String): List<String> =
            text.lowercase().split(Regex("[\\s,·#]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
    }
}
