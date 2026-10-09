package com.logus.app.record

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
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
     * 돌려주는 값: 새 기록 ID
     */
    suspend fun uploadVideoLog(
        journeyId: String,
        uid: String,
        video: File,
        capturedAtMillis: Long,
        capturedTz: String,
        themes: List<String>,
    ): String {
        val logRef = db.collection("journeys").document(journeyId).collection("logs").document()
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
            runCatching { fileRef.delete().await() }
            if (thumbUploaded) runCatching { thumbRef.delete().await() }
            throw e
        }
        return logRef.id
    }

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
        val logs = snap.documents.mapNotNull { doc ->
            FeedLog(
                id = doc.id,
                authorId = doc.getString("authorId") ?: return@mapNotNull null,
                mediaType = doc.getString("mediaType") ?: return@mapNotNull null,
                mediaPath = doc.getString("mediaPath"),
                capturedAtMillis = doc.getTimestamp("capturedAt")?.toDate()?.time ?: return@mapNotNull null,
                placeName = doc.getString("placeName"),
            )
        }
        val last = if (snap.size() < limit) null else snap.documents.lastOrNull()
        return LogPage(logs, last)
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

    /**
     * 기록에 위치를 저장한다: 서버 함수 saveLogLocation 이 위치·장소 이름을 채우고 10코인을 준다
     * (같은 기록으로는 한 번만, 코인 내역 users/{uid}/coinLedger/{logId}). 탐색 공개(isPublic)는 아직 false.
     * 돌려주는 값: 이번에 받은 코인 수(이미 받았으면 0)
     */
    suspend fun saveLocation(journeyId: String, logId: String, place: PickedPlace): Int {
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
        return (data?.get("coinsGranted") as? Number)?.toInt() ?: 0
    }
}

/** 기록 보기 한 쪽에 읽는 기록 수 */
private const val PAGE_SIZE = 30L

private const val TAG = "LogRepository"

/** 영상 경로 journeys/…/{logId}.mp4 → 대표 화면(썸네일) 경로 journeys/…/{logId}.jpg */
internal fun thumbnailPath(videoPath: String): String = videoPath.substringBeforeLast('.') + ".jpg"

/**
 * 영상 첫 장면을 가로 최대 640px 로 줄인 JPEG(약 30~80KB). 영상이 작아도 기록 보기에서 빨리 보이도록 따로 만든다.
 * 안드로이드 기본 기능(MediaMetadataRetriever)이라 라이브러리가 필요 없다. 실패하면 null.
 */
private fun videoThumbnailJpeg(video: File): ByteArray? {
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
