package com.logus.app.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 앨범에서 고른 사진을 올리기 전에 줄인다(CLAUDE.md "데이터 원칙": 사진은 앱에서 줄여서 올린다).
 * 긴 변을 최대 1024px 로 줄이고 JPEG(품질 85)로 바꾼다. HEIC 등 다른 형식도 JPEG 로 바뀐다.
 */
object ProfilePhoto {
    private const val MAX_SIZE = 1024
    private const val JPEG_QUALITY = 85

    suspend fun toJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri)
        } else {
            decodeLegacy(context, uri)
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** 안드로이드 9 이상: 사진 방향(EXIF)까지 알아서 맞춰 준다 */
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, MAX_SIZE.toFloat() / max(width, height))
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** 안드로이드 8: 크기를 줄여 읽고, EXIF 방향대로 돌린다 */
    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("사진을 읽을 수 없어요.")

        val scale = minOf(1f, MAX_SIZE.toFloat() / max(decoded.width, decoded.height))
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}
