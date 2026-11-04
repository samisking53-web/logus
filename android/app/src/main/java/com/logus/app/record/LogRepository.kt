package com.logus.app.record

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/** 기록 보기(N01)에 보여 줄 기록 한 개 */
data class FeedLog(
    val id: String,
    val authorId: String,
    /** photo | video | text */
    val mediaType: String,
    /** Storage 경로 journeys/{journeyId}/{작성자 uid}/{파일 이름}(글 기록은 null) */
    val mediaPath: String?,
    /** 찍은 시각(밀리초, UTC) */
    val capturedAtMillis: Long,
    /** 장소 이름(위치 없이 올렸으면 null) */
    val placeName: String?,
    /** 기록 위치(위치 없이 올렸거나 (0,0)이면 null). 여정 상세 S16 추억 지도의 핀 */
    val location: GeoPoint? = null,
)

/** 기록 한 쪽(페이지): 기록들 + 다음 쪽을 읽을 때 이어서 읽을 마지막 문서(더 없으면 null) */
data class LogPage(val logs: List<FeedLog>, val last: DocumentSnapshot?)

/**
 * 기록(logs) 담당 (백엔드: Cloud Storage + Firestore)
 * 보안 규칙: 영상은 본인 폴더 journeys/{journeyId}/{내 uid}/ 에만 올릴 수 있고(영상 50MB 미만),
 * 기록 문서는 여정 구성원만, 위치·장소·공개 여부는 비워서(null·false) 만든다(위치는 saveLogLocation 함수로만 채운다).
 */
class LogRepository {
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    // 서버 함수는 서울 리전(asia-northeast3)에 있다
    private val functions = Firebase.functions("asia-northeast3")

    /**
     * S05 "여정에 올리기": 영상 파일을 Storage 에 올리고 journeys/{journeyId}/logs/{logId} 문서를 만든다.
     * - 파일 경로: journeys/{journeyId}/{uid}/{logId}.mp4 (기록 ID 와 파일 이름을 맞춘다)
     * - themes: 테마 태그(# 없이, 최대 3개)
     * - 위치는 null 로 만든다(보안 규칙). 위치는 saveLocation(서버 함수 saveLogLocation)으로 따로 채운다.
     * - 대표 화면(썸네일): 영상 첫 장면을 줄여 같은 폴더에 {logId}.jpg 로 함께 올린다(N01 기록 보기에서 쓴다).
     *   썸네일만 실패하면 기록은 그대로 올리고, 기록 보기에서 보라 그림으로 대신 보여 준다.
     * 문서 만들기에 실패하면 먼저 올린 영상·썸네일 파일을 지운다(쓰레기 파일이 남지 않게).
     * logId: 미리 정한 기록 ID(newLogId). 오프라인이면 같은 ID 로 폰에 보관했다가 나중에 올려서, 같은 기록이 두 번 생기지 않는다.
     * resumed: 폰에 보관했던 기록을 다시 올리는 중(LogUploadWorker). 이미 다 올라가 있으면(지난번에 끝을 못 보고 앱이 꺼진 경우) 건너뛴다.
     * 돌려주는 값: 기록 ID
     */
    suspend fun uploadVideoLog(
        journeyId: String,
        uid: String,
        video: File,
        capturedAtMillis: Long,
        capturedTz: String,
        themes: List<String>,
        logId: String? = null,
        resumed: Boolean = false,
    ): String {
        val logs = db.collection("journeys").document(journeyId).collection("logs")
        val logRef = if (logId != null) logs.document(logId) else logs.document()
        if (resumed && logRef.get(Source.SERVER).await().exists()) return logRef.id
        val mediaPath = "journeys/$journeyId/$uid/${logRef.id}.mp4"
        val fileRef = storage.reference.child(mediaPath)
        val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
        fileRef.putFile(Uri.fromFile(video), metadata).await()
        val thumbRef = storage.reference.child(thumbnailPath(mediaPath))
        val thumbUploaded = runCatching {
            val jpeg = withContext(Dispatchers.IO) { videoThumbnailJpeg(video) } ?: error("첫 장면을 꺼내지 못했어요")
            val jpegMeta = StorageMetadata.Builder().setContentType("image/jpeg").build()
            thumbRef.putBytes(jpeg, jpegMeta).await()
        }.onFailure { Log.w(TAG, "썸네일 올리기 실패(기록은 계속 올린다)", it) }.isSuccess

        try {
            logRef.set(
                hashMapOf(
                    "authorId" to uid,
                    "mediaType" to "video",
                    "mediaPath" to mediaPath,
                    "body" to "",
                    "themes" to themes,
                    "taggedUids" to emptyList<String>(),
                    "capturedAt" to Timestamp(Date(capturedAtMillis)),
                    "capturedTz" to capturedTz,
                    "location" to null,
                    "placeName" to null,
                    "isPublic" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: Exception) {
            // 기다리다 그만둔 경우(2분 넘김·작업 중지)는 저장이 나중에 서버에 닿을 수 있으므로 파일을 지우지 않는다
            if (e is CancellationException) throw e
            // 다시 올리는 중인데 그사이 먼저 보낸 저장이 서버에 닿아 이미 기록이 있으면 성공으로 본다(파일도 지우지 않는다)
            if (resumed && runCatching { logRef.get(Source.SERVER).await().exists() }.getOrDefault(false)) return logRef.id
            runCatching { fileRef.delete().await() }
            if (thumbUploaded) runCatching { thumbRef.delete().await() }
            throw e
        }
        return logRef.id
    }

    /** 새 기록 ID 를 미리 정한다(인터넷이 없어도 폰에서 바로 만든다) */
    fun newLogId(journeyId: String): String =
        db.collection("journeys").document(journeyId).collection("logs").document().id

    /**
     * N01 기록 보기: 한 여정의 [from, to) 사이에 찍은 기록을 찍은 시각 순서로 limit 개씩 읽는다(비용 관리: 한 번에 다 읽지 않는다).
     * after 를 넘기면 그 문서 다음부터 이어서 읽는다(아래로 내리면 다음 쪽). 같은 칸(capturedAt)으로 범위·정렬해서 색인이 따로 필요 없다.
     */
    suspend fun loadLogs(journeyId: String, from: Date, to: Date, after: DocumentSnapshot?, limit: Long = PAGE_SIZE): LogPage {
        var query = db.collection("journeys").document(journeyId).collection("logs")
            .whereGreaterThanOrEqualTo("capturedAt", Timestamp(from))
            .whereLessThan("capturedAt", Timestamp(to))
            .orderBy("capturedAt", Query.Direction.ASCENDING)
            .limit(limit)
        if (after != null) query = query.startAfter(after)
        val snap = query.get().await()
        val logs = snap.documents.mapNotNull { it.toFeedLog() }
        val last = if (snap.size() < limit) null else snap.documents.lastOrNull()
        return LogPage(logs, last)
    }

    /**
     * S14 마이로그 여정 목록의 대표 사진: 이 여정에서 가장 먼저 찍은 기록 1개(없으면 null).
     * 한 개만 읽는다(비용 관리). 대표 화면 주소는 previewUrl 로 받는다.
     */
    suspend fun loadFirstLog(journeyId: String): FeedLog? =
        db.collection("journeys").document(journeyId).collection("logs")
            .orderBy("capturedAt", Query.Direction.ASCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toFeedLog()

    /**
     * 이 여정에 올라온 기록 수(모든 구성원, N03 저장 팝업의 "기록 N개").
     * Firestore 의 개수 세기(count)는 문서를 내려받지 않고 숫자만 받는다(읽기 비용: 기록 1000개당 1번).
     */
    suspend fun countLogs(journeyId: String): Int =
        db.collection("journeys").document(journeyId).collection("logs")
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
            .toInt()

    /**
     * N01 날짜 상자의 "기록 N개 · 내 기록 N개": 한 여정의 [from, to) 사이에 찍은 기록 수(authorId 를 넘기면 그 사람 것만).
     * 개수 세기(count)라 문서를 내려받지 않는다. authorId 로 거를 때는 색인(authorId + capturedAt, firestore.indexes.json)이 필요하다.
     */
    suspend fun countDayLogs(journeyId: String, from: Date, to: Date, authorId: String? = null): Int {
        var query: Query = db.collection("journeys").document(journeyId).collection("logs")
            .whereGreaterThanOrEqualTo("capturedAt", Timestamp(from))
            .whereLessThan("capturedAt", Timestamp(to))
        if (authorId != null) query = query.whereEqualTo("authorId", authorId)
        return query.count().get(AggregateSource.SERVER).await().count.toInt()
    }

    /**
     * 내 기록 삭제(N01 길게 누르기 → 삭제): 기록 문서를 먼저 지워 구성원 모두의 기록 보기·추억 지도·DAY RECAP 에서 빼고,
     * 영상과 대표 화면(썸네일) 파일을 지운다. 보안 규칙상 작성자만 지울 수 있다(남의 기록은 거부된다).
     * 파일 지우기가 실패해도 기록은 이미 사라졌으므로 넘어간다(로그만 남긴다). 받은 코인은 그대로 둔다(2026-11-02 팀 결정).
     */
    suspend fun deleteLog(journeyId: String, log: FeedLog) {
        db.collection("journeys").document(journeyId).collection("logs").document(log.id).delete().await()
        log.mediaPath?.let { path ->
            for (file in listOf(path, thumbnailPath(path))) {
                runCatching { storage.reference.child(file).delete().await() }
                    .onFailure { Log.w(TAG, "기록 파일 지우기 실패: $file", it) }
            }
            VideoCache.remove(path) // 이 폰에 남겨 둔 영상도 지운다
        }
    }

    /**
     * 기록의 대표 화면 주소(Storage 다운로드 주소). 사진은 그 사진, 영상은 같은 폴더의 {logId}.jpg 썸네일.
     * 보안 규칙상 여정 구성원만 받을 수 있다. 썸네일이 없으면(예전 기록) 예외가 난다 → 화면은 보라 그림으로 대신한다.
     */
    suspend fun previewUrl(log: FeedLog): String {
        val path = when (log.mediaType) {
            "photo" -> log.mediaPath
            "video" -> log.mediaPath?.let(::thumbnailPath)
            else -> null
        } ?: error("대표 화면이 없는 기록이에요")
        return storage.reference.child(path).downloadUrl.await().toString()
    }

    /** 영상 기록의 재생 주소(Storage 다운로드 주소). 보안 규칙상 여정 구성원만 받을 수 있다. 원본 영상 팝업·S17 DAY RECAP 재생·영상 추출이 쓴다 */
    suspend fun videoUrl(log: FeedLog): String {
        val path = log.mediaPath?.takeIf { log.mediaType == "video" } ?: error("영상이 없는 기록이에요")
        return storage.reference.child(path).downloadUrl.await().toString()
    }

    /**
     * 기록에 위치를 저장한다: 서버 함수 saveLogLocation 이 위치·장소 이름을 채우고 10코인을 준다
     * (같은 기록으로는 한 번만, 코인 내역 users/{uid}/coinLedger/{logId}). 탐색 공개(isPublic)는 아직 false.
     * 하루(UTC)에 받을 수 있는 코인은 50개까지라서(Coins.DAILY_LIMIT) 10코인보다 적게 받을 수 있다.
     * 돌려주는 값: 이번에 받은 코인 수(이미 받았으면 0)와 하루 제한 때문에 덜 받았는지
     */
    suspend fun saveLocation(journeyId: String, logId: String, place: PickedPlace): LocationReward {
        val result = functions.getHttpsCallable("saveLogLocation")
            .call(
                hashMapOf(
                    "journeyId" to journeyId,
                    "logId" to logId,
                    "location" to hashMapOf("lat" to place.point.lat, "lng" to place.point.lng),
                    "placeName" to place.name,
                    "isPublic" to false,
                ),
            )
            .await()
        val data = result.getData() as? Map<*, *>
        return LocationReward(
            coins = (data?.get("coinsGranted") as? Number)?.toInt() ?: 0,
            dailyLimitReached = data?.get("dailyLimitReached") == true,
        )
    }
}

/** 위치 저장으로 받은 코인. dailyLimitReached = 하루 50코인 제한 때문에 10코인보다 적게(0개일 수도) 받았다 */
data class LocationReward(val coins: Int, val dailyLimitReached: Boolean = false)

/** 기록 보기 한 쪽에 읽는 기록 수 */
private const val PAGE_SIZE = 30L

private const val TAG = "LogRepository"

/** logs 문서 → FeedLog. 꼭 있어야 하는 칸(작성자·종류·찍은 시각)이 없으면 null */
private fun DocumentSnapshot.toFeedLog(): FeedLog? {
    return FeedLog(
        id = id,
        authorId = getString("authorId") ?: return null,
        mediaType = getString("mediaType") ?: return null,
        mediaPath = getString("mediaPath"),
        capturedAtMillis = getTimestamp("capturedAt")?.toDate()?.time ?: return null,
        placeName = getString("placeName"),
        location = (get("location") as? Map<*, *>)?.let { map ->
            val lat = (map["lat"] as? Number)?.toDouble()
            val lng = (map["lng"] as? Number)?.toDouble()
            // 좌표 (0,0)은 결측으로 본다(CLAUDE.md 데이터 원칙)
            if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)) null else GeoPoint(lat, lng)
        },
    )
}

/** 영상 경로 journeys/…/{logId}.mp4 → 대표 화면(썸네일) 경로 journeys/…/{logId}.jpg */
internal fun thumbnailPath(videoPath: String): String = videoPath.substringBeforeLast('.') + ".jpg"

/**
 * 영상 첫 장면을 가로 최대 640px 로 줄인 JPEG(약 30~80KB). 영상이 작아도 기록 보기에서 빨리 보이도록 따로 만든다.
 * 안드로이드 기본 기능(MediaMetadataRetriever)이라 라이브러리가 필요 없다. 실패하면 null.
 */
internal fun videoThumbnailJpeg(video: File): ByteArray? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(video.absolutePath)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scaled = if (frame.width > THUMB_MAX_WIDTH) {
            Bitmap.createScaledBitmap(frame, THUMB_MAX_WIDTH, frame.height * THUMB_MAX_WIDTH / frame.width, true)
        } else {
            frame
        }
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private const val THUMB_MAX_WIDTH = 640
