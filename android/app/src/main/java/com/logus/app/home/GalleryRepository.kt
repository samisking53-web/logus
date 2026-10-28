package com.logus.app.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 폰 사진 한 장(대표 사진 선택 팝업의 칸 하나) */
data class GalleryPhoto(
    /** content://… 주소(Coil 이 바로 그린다) */
    val uri: android.net.Uri,
    /** 파일 이름(확장자 뺌). 예) IMG_2381 */
    val name: String,
    /** 찍은 시각(없으면 폰에 저장된 시각), 밀리초 */
    val takenMillis: Long,
    /** 앨범(폴더) 구분값 */
    val albumId: String?,
)

/** 앨범 하나. id 가 null 이면 "최근 항목"(모든 사진) */
data class GalleryAlbum(val id: String?, val name: String, val count: Int)

/** 사진 접근 권한 상태 */
enum class PhotoAccess {
    /** 모든 사진을 볼 수 있다 */
    FULL,

    /** 안드로이드 14 이상에서 "사진 선택"으로 일부만 허용했다(고른 사진만 보인다) */
    PARTIAL,

    /** 허용하지 않았다 */
    NONE,
}

/**
 * 폰 사진첩 읽기 담당(대표 사진 선택 팝업). 안드로이드 기본 MediaStore 로 사진 목록만 읽는다(라이브러리 없음).
 * 권한: 안드로이드 13 이상 READ_MEDIA_IMAGES(14 이상은 일부만 허용 READ_MEDIA_VISUAL_USER_SELECTED 도), 12 이하 READ_EXTERNAL_STORAGE.
 * 사진은 이 팝업에서 고른 한 장만 줄여서 올린다. 목록은 폰 밖으로 보내지 않는다.
 */
object GalleryRepository {

    /** 최근 사진을 이만큼까지만 읽는다(사진이 아주 많은 폰에서도 팝업이 빨리 뜨게) */
    const val MAX_PHOTOS = 3000

    /** 이 폰에서 물어볼 권한들 */
    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(context: Context): PhotoAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.FULL
            else -> PhotoAccess.NONE
        }
    }

    /** 최근에 저장된 순서로 사진 목록과 앨범 이름을 읽는다(권한이 있을 때만 부른다) */
    suspend fun load(context: Context): Pair<List<GalleryPhoto>, Map<String, String>> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<GalleryPhoto>()
        val albumNames = mutableMapOf<String, String>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext() && photos.size < MAX_PHOTOS) {
                val id = cursor.getLong(idCol)
                val taken = cursor.getLong(takenCol).takeIf { it > 0 } ?: (cursor.getLong(addedCol) * 1000)
                val bucket = cursor.getString(bucketCol)
                if (bucket != null) {
                    cursor.getString(bucketNameCol)?.let { albumNames.putIfAbsent(bucket, it) }
                }
                photos += GalleryPhoto(
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = cursor.getString(nameCol)?.substringBeforeLast('.') ?: "사진",
                    takenMillis = taken,
                    albumId = bucket,
                )
            }
        }
        photos to albumNames
    }
}
