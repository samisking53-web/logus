package com.logus.app.home

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import com.logus.app.auth.ProfilePhoto
import kotlinx.coroutines.tasks.await

/**
 * S01-A 여정 카드 대표 사진 담당 (백엔드: Storage + Firestore)
 * 대표 사진은 "내 홈에서만" 보인다(2026-10-28 팀 결정): 사진 파일은 내 여정 폴더 journeys/{여정}/{내 uid}/cover_{시각}.jpg 에,
 * 경로는 내 멤버 문서 journeys/{여정}/members/{내 uid} 의 coverPath 에 저장한다(다른 구성원의 홈은 그대로).
 * 보안 규칙: Storage 는 본인 폴더에 이미지 10MB 미만만, Firestore 는 본인 멤버 문서의 coverPath 만(형식 확인) 고칠 수 있다.
 */
class CoverPhotoRepository {
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    /**
     * 폰 사진(uri)을 줄여서(긴 쪽 1024px JPEG) 올리고 내 멤버 문서의 coverPath 를 바꾼다. 돌려주는 값: 새 경로.
     * 문서를 고치지 못하면 방금 올린 파일을 지운다. 예전에 올린 내 대표 사진 파일은 성공한 뒤 지운다(지우기 실패는 무시).
     */
    suspend fun saveCover(context: Context, journeyId: String, uid: String, uri: Uri, oldPath: String?): String {
        val jpeg = ProfilePhoto.toJpeg(context, uri)
        val path = "journeys/$journeyId/$uid/cover_${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child(path)
        ref.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        try {
            db.collection("journeys").document(journeyId)
                .collection("members").document(uid)
                .update("coverPath", path)
                .await()
        } catch (e: Exception) {
            runCatching { ref.delete().await() }
            throw e
        }
        if (oldPath != null && oldPath != path && oldPath.startsWith("journeys/$journeyId/$uid/cover_")) {
            runCatching { storage.reference.child(oldPath).delete().await() }
                .onFailure { Log.w(TAG, "예전 대표 사진 지우기 실패", it) }
        }
        return path
    }

    /** 대표 사진 Storage 경로 → 내려받기 주소(여정 구성원만 받을 수 있다) */
    suspend fun downloadUrl(path: String): String =
        storage.reference.child(path).downloadUrl.await().toString()

    private companion object {
        const val TAG = "CoverPhotoRepository"
    }
}
