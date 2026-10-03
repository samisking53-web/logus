package com.logus.app.auth

import android.app.Activity
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

/** Firestore users/{uid} 문서 (CLAUDE.md "데이터 모델") */
data class Profile(
    val nickname: String,
    val photoUrl: String?,
    val coins: Long,
)

/**
 * 로그인·회원가입 담당 (백엔드: Firebase)
 * - 로그인: 안드로이드 표준 로그인 창(Credential Manager)으로 구글 계정을 고르면, 받은 구글 ID 토큰을
 *   Firebase Authentication 에 넘긴다. Firebase 가 계정(uid)을 만들거나 기존 계정으로 로그인시킨다.
 * - 회원가입: Firestore users/{uid} 문서가 있으면 가입한 사람이다. 없으면 회원가입 화면에서 만든다.
 *   보안 규칙(firestore.rules)이 "본인 문서만, nickname·photoURL·createdAt 만" 쓸 수 있게 막는다.
 */
class AuthRepository {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** 구글 로그인. 폰에 있는 구글 계정 중 하나를 고르는 창이 뜬다. */
    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        // default_web_client_id: google-services.json 에서 자동으로 만들어지는 값이다.
        // 이름에 web 이 들어가지만 '웹 앱 등록'과는 상관없다. 구글 로그인을 켜면 Firebase 가 자동으로 만드는
        // 서버 확인용 ID 로, 안드로이드 구글 로그인에 꼭 필요하다.
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
        return requireNotNull(result.user) { "구글 로그인 결과에 사용자가 없어요." }
    }

    /** 가입한 사람이면 프로필을, 아직 가입 전이면 null 을 돌려준다. */
    suspend fun loadProfile(uid: String): Profile? {
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        return Profile(
            nickname = snap.getString("nickname") ?: "여행자",
            photoUrl = snap.getString("photoURL"),
            coins = snap.getLong("coins") ?: 0L,
        )
    }

    /**
     * 회원가입 완료: users/{uid} 문서를 만든다.
     * coins 는 서버(saveLogLocation 함수)만 바꿀 수 있어서 넣지 않는다.
     */
    suspend fun createProfile(uid: String, nickname: String, photoUrl: String?): Profile {
        db.collection("users").document(uid).set(
            mapOf(
                "nickname" to nickname,
                "photoURL" to photoUrl,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return Profile(nickname = nickname, photoUrl = photoUrl, coins = 0L)
    }

    /** 로그아웃: Firebase 로그인과 기기에 저장된 구글 계정 선택 상태를 함께 지운다. */
    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}

/** 구글 프로필 사진 주소가 보안 규칙(https, 500자 이하)에 맞을 때만 쓴다. */
fun safePhotoUrl(url: String?): String? =
    url?.takeIf { it.startsWith("https://") && it.length <= 500 }
