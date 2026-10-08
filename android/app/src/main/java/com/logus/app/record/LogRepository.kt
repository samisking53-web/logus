package com.logus.app.record

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
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

    /**
     * S05 "여정에 올리기": 영상 파일을 Storage 에 올리고 journeys/{journeyId}/logs/{logId} 문서를 만든다.
     * - 파일 경로: journeys/{journeyId}/{uid}/{logId}.mp4 (기록 ID 와 파일 이름을 맞춘다)
     * - themes: 테마 태그(# 없이, 최대 3개)
     * - 위치는 null 로 만든다. 위치와 함께 저장(+10코인)은 지도 팝업을 만든 뒤 saveLogLocation 함수로 채운다.
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
}
