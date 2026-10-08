package com.logus.app.record

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date

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
     * 문서 만들기에 실패하면 먼저 올린 영상 파일을 지운다(쓰레기 파일이 남지 않게).
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
            throw e
        }
        return logRef.id
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
