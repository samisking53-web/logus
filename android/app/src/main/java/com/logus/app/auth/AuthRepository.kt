package com.logus.app.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.logus.app.R
import kotlinx.coroutines.tasks.await

/**
 * 로그인 담당 (구글 계정만 쓴다).
 * - 안드로이드 표준 로그인 창(Credential Manager)으로 구글 ID 토큰을 받아 Firebase에 넘긴다.
 *   제공업체가 웹과 같은 google.com 이라 같은 사람은 웹·안드로이드에서 같은 계정(uid)이 된다.
 * - 처음 로그인하면 Firestore users/{uid} 프로필 문서를 만든다(보안 규칙에 맞는 필드만).
 */
class AuthRepository {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** 구글 로그인. 기기에 있는 구글 계정 중 하나를 고르는 창이 뜬다. */
    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        // default_web_client_id 는 google-services.json 에서 자동으로 만들어지는 웹 클라이언트 ID다.
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential

        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "구글 로그인 결과를 읽을 수 없어요."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = requireNotNull(result.user) { "구글 로그인 결과에 사용자가 없어요." }
        ensureProfile(user)
        return user
    }

    /** 로그아웃: Firebase 로그인과 기기에 저장된 구글 로그인 선택 상태를 함께 지운다. */
    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * users/{uid} 문서가 없으면 만든다.
     * 보안 규칙(firestore.rules isValidUser)에 맞춰 nickname(1~20자)·photoURL(https 또는 null)·createdAt(서버 시각)만 넣는다.
     * coins 는 서버만 바꿀 수 있어서 넣지 않는다.
     * 실패해도(예: 보안 규칙을 아직 배포하지 않음) 로그인 자체는 유지하고 Logcat 에 경고만 남긴다.
     */
    private suspend fun ensureProfile(user: FirebaseUser) {
        try {
            createProfileIfMissing(user)
        } catch (e: Exception) {
            Log.w(TAG, "users/${user.uid} 프로필을 만들지 못했어요. Firestore 보안 규칙 배포를 확인해 주세요.", e)
        }
    }

    private suspend fun createProfileIfMissing(user: FirebaseUser) {
        val ref = db.collection("users").document(user.uid)
        if (ref.get().await().exists()) return

        val nickname = user.displayName?.trim()?.takeCodePoints(20)?.ifBlank { null } ?: DEFAULT_NICKNAME
        val photoUrl = user.photoUrl?.toString()
            ?.takeIf { it.startsWith("https://") && it.length <= 500 }

        ref.set(
            mapOf(
                "nickname" to nickname,
                "photoURL" to photoUrl,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    private companion object {
        const val DEFAULT_NICKNAME = "여행자"
        const val TAG = "AuthRepository"
    }
}

/** 이모지가 반으로 잘리지 않게 글자(코드 포인트) 단위로 자른다. */
private fun String.takeCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
