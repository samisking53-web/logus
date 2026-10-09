package com.logus.app.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import com.logus.app.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** 회원가입 때 고른 프로필 사진 */
sealed interface PhotoChoice {
    /** 구글 계정 프로필 사진(주소) */
    data class Google(val url: String) : PhotoChoice

    /** 앨범에서 고른 사진(가입 완료 때 줄여서 Storage 에 올린다) */
    data class Album(val uri: Uri) : PhotoChoice

    /** 기본 프로필 그림 */
    data object Default : PhotoChoice
}

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
 * - 회원가입: Firestore users/{uid} 문서가 있으면 가입한 사람이다. 없으면 약관 동의 → 프로필 설정 화면을 거쳐 만든다.
 *   프로필(users/{uid})과 약관 동의 기록(users/{uid}/agreements/{약관 버전})을 한 번에(배치) 저장한다.
 *   보안 규칙(firestore.rules)이 "본인 문서만, 정해진 필드만, 필수 약관은 모두 동의"를 확인한다.
 */
class AuthRepository {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage

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
    suspend fun loadProfile(uid: String): Profile? =
        db.collection("users").document(uid).get().await().toProfile()

    /**
     * 내 프로필(users/{uid})을 실시간으로 지켜본다. 서버가 코인을 주거나(saveLogLocation) 프로필이 바뀌면
     * 새 값이 바로 온다(문서가 없으면 null). 문서 한 개만 듣는 리스너라 바뀔 때만 읽기 1회가 든다.
     */
    fun profileChanges(uid: String): Flow<Profile?> =
        db.collection("users").document(uid).snapshots().map { it.toProfile() }

    private fun DocumentSnapshot.toProfile(): Profile? {
        if (!exists()) return null
        return Profile(
            nickname = getString("nickname") ?: "여행자",
            photoUrl = getString("photoURL"),
            coins = getLong("coins") ?: 0L,
        )
    }

    /**
     * 회원가입 완료.
     * 1) 앨범 사진을 골랐으면 줄여서 Storage users/{uid}/profile_시각.jpg 에 올리고 주소를 받는다.
     * 2) users/{uid}(닉네임·사진·가입 시각)와 users/{uid}/agreements/{약관 버전}(동의 항목·동의 시각)을
     *    한 번에 저장한다. 둘 중 하나라도 보안 규칙에 걸리면 둘 다 저장되지 않는다.
     * coins 는 서버(saveLogLocation 함수)만 바꿀 수 있어서 넣지 않는다.
     */
    suspend fun createProfile(
        context: Context,
        uid: String,
        nickname: String,
        photo: PhotoChoice,
        agreements: Agreements,
    ): Profile {
        val photoUrl = when (photo) {
            is PhotoChoice.Google -> safePhotoUrl(photo.url)
            is PhotoChoice.Album -> safePhotoUrl(uploadProfilePhoto(uid, ProfilePhoto.toJpeg(context, photo.uri)))
            PhotoChoice.Default -> null
        }

        val userRef = db.collection("users").document(uid)
        val agreementRef = userRef.collection("agreements").document(TERMS_VERSION)
        db.batch()
            .set(
                userRef,
                mapOf(
                    "nickname" to nickname,
                    "photoURL" to photoUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .set(
                agreementRef,
                mapOf(
                    "ageOver14" to agreements.ageOver14,
                    "terms" to agreements.terms,
                    "location" to agreements.location,
                    "notifyNewLogs" to agreements.notifyNewLogs,
                    "agreedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commit()
            .await()
        return Profile(nickname = nickname, photoUrl = photoUrl, coins = 0L)
    }

    /** 프로필 사진을 Storage 에 올리고 내려받기 주소를 돌려준다(보안 규칙: 본인 폴더, 이미지 5MB 미만). */
    private suspend fun uploadProfilePhoto(uid: String, jpeg: ByteArray): String {
        val ref = storage.reference.child("users/$uid/profile_${System.currentTimeMillis()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(jpeg, metadata).await()
        return ref.downloadUrl.await().toString()
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
