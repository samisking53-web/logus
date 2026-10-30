package com.logus.app.record

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 기록 영상 캐시(2026-10-30 팀 결정: Storage 다운로드 사용량 줄이기).
 * 한 번 받은 영상은 폰 안 앱 전용 임시 폴더(cacheDir/video_cache)에 남겨 두고, 같은 영상을 다시 볼 때 Storage 에서 또 받지 않는다.
 * - 원본 영상 팝업(LogVideoDialog)·S17 DAY RECAP 재생(RecapPlayer)은 [newPlayer]로 만든 재생기로,
 *   DAY RECAP 영상 추출(RecapExporter)은 [copyTo]로 같은 캐시를 함께 쓴다(재생으로 본 영상은 추출할 때 다시 받지 않는다).
 * - 캐시 열쇠는 Storage 경로(mediaPath)라서 다운로드 주소가 달라져도 같은 영상으로 알아본다.
 * - 최대 [MAX_BYTES](200MB). 가득 차면 가장 오래 안 본 영상부터 지운다. 폰 저장 공간이 모자라면 안드로이드가 비울 수도 있다(그럼 다시 받는다).
 * - 캐시에 있어도 영상 주소(videoUrl)는 먼저 받으므로, 여정 구성원이 아니면 보안 규칙에 막혀 재생되지 않는다.
 * Media3(이미 쓰는 라이브러리)의 SimpleCache 를 쓴다. 한 폴더에 하나만 만들 수 있어서 앱 전체가 이 하나를 같이 쓴다.
 */
@OptIn(UnstableApi::class)
object VideoCache {
    private const val MAX_BYTES = 200L * 1024 * 1024
    private const val FOLDER = "video_cache"

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, FOLDER),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** 캐시를 거쳐 읽기: 캐시에 있으면 폰에서, 없으면 인터넷에서 받으면서 캐시에 남긴다(캐시가 고장 나면 그냥 인터넷에서) */
    private fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** 캐시를 쓰는 재생기(ExoPlayer). 다 쓰면 release() 한다 */
    fun newPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory(context)))
            .build()

    /** 재생할 영상(주소 + 캐시 열쇠 = Storage 경로) */
    fun mediaItem(url: String, storagePath: String?): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setCustomCacheKey(storagePath ?: url)
            .build()

    /** 영상 파일을 target 에 만든다(캐시에 있으면 폰에서 복사, 없으면 받아서 캐시에도 남긴다). DAY RECAP 영상 추출용 */
    suspend fun copyTo(context: Context, url: String, storagePath: String?, target: File) = withContext(Dispatchers.IO) {
        val source = dataSourceFactory(context).createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(url).setKey(storagePath ?: url).build())
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive() // 영상 만들기를 취소하면 바로 멈춘다
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    out.write(buffer, 0, read)
                }
            }
        } finally {
            source.close()
        }
    }
}
