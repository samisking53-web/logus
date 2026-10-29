package com.logus.app.journeydetail

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.logus.app.R
import com.logus.app.feed.minuteLabel
import com.logus.app.journey.Member
import com.logus.app.record.GeoPoint
import com.logus.app.record.LogRepository
import com.logus.app.ui.journeydetail.MAP_STYLE_URL
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.PrimaryStrong
import com.logus.app.ui.theme.Surface
import com.logus.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/** 저장 영상의 글자: 여정 이름, 날짜·도시("2026.9.24 · 포르투"), 파일 이름 앞부분 */
data class RecapExportInfo(val title: String, val subtitle: String, val fileStem: String)

/**
 * S17 DAY RECAP "영상 다운로드"(2026-10-22 팀 결정): 서버 비용 없이 폰에서 영상을 합친다(Media3 Transformer).
 * 1) 그날 영상 파일을 받는다 → 2) 장면 길이·칸 자리를 정한다(planExport) → 3) 지도 장면은 지도 사진을 찍는다(MapSnapshotter)
 * → 4) 칸마다 영상 줄(sequence)을 만들어 한 화면에 겹치고(합성기), 그 위에 로고·글자·프로필·지도·핀을 덧그린다(RecapOverlay)
 * → 5) 릴스 크기(1080×1920) mp4 로 만들어 갤러리 "LOG US" 앨범에 저장한다.
 * 칸은 3줄로 고정하고, 칸이 필요 없는 때(지도 장면, 칸이 적은 장면)는 검은 그림을 채운 뒤 보이지 않게 한다.
 */
class RecapExporter(
    private val context: Context,
    private val logs: LogRepository = LogRepository(),
) {
    private val workDir = File(context.cacheDir, "recap_export")

    /** 영상을 만들어 갤러리에 저장하고 그 주소를 돌려준다. progress 는 0~1 */
    suspend fun export(
        info: RecapExportInfo,
        steps: List<RecapStep>,
        members: Map<String, Member>,
        onProgress: (Float) -> Unit,
    ): Uri {
        withContext(Dispatchers.IO) {
            workDir.deleteRecursively()
            workDir.mkdirs()
        }
        try {
            // 1) 영상 파일 받기(전체의 30%)
            val videoLogs = steps.filterIsInstance<RecapScene>().flatMap { s -> s.clips.mapNotNull { it.log } }.distinctBy { it.id }
            val files = mutableMapOf<String, File>()
            val withSound = mutableSetOf<String>() // 소리가 들어 있는 영상(마이크를 거절하고 찍은 영상은 소리가 없다)
            val durations = mutableMapOf<String, Long>()
            videoLogs.forEachIndexed { i, log ->
                val file = File(workDir, "${log.id}.mp4")
                val ok = runCatching { logs.downloadVideo(log, file) }.onFailure { Log.w(TAG, "영상 받기 실패", it) }.isSuccess
                val duration = if (ok) withContext(Dispatchers.IO) { videoDurationMs(file) } else null
                if (duration != null) {
                    files[log.id] = file
                    durations[log.id] = duration
                    if (withContext(Dispatchers.IO) { hasAudioTrack(file) }) withSound += log.id
                }
                onProgress(0.3f * (i + 1) / videoLogs.size)
            }
            if (files.isEmpty()) error("영상을 받지 못했어요. 인터넷 연결을 확인해 주세요.")

            // 2) 장면 계획, 3) 지도 사진·프로필 그림
            val scenes = planExport(steps, durations)
            val cameras = mutableMapOf<Int, ReelCamera>()
            val snapshots = mutableMapOf<Int, Bitmap>()
            scenes.forEachIndexed { i, scene ->
                val step = scene.step as? RecapMapScene ?: return@forEachIndexed
                val camera = fitReelCamera(step.pins.flatMap { listOfNotNull(it.from, it.to) }, ReelLayout.WIDTH, ReelLayout.BODY, padding = 160)
                cameras[i] = camera
                mapSnapshot(camera)?.let { snapshots[i] = it }
            }
            val avatars = members.mapValues { (_, m) -> loadAvatar(m.photoUrl) }
            onProgress(0.35f)

            // 4) 합치기
            val output = File(workDir, "${info.fileStem}.mp4")
            fun overlay() = RecapOverlay(context, info, scenes, members, avatars, cameras, snapshots)
            try {
                runTransformer(buildComposition(scenes, files, withSound, overlay(), sound = true), output) { onProgress(0.35f + 0.6f * it) }
            } catch (e: ExportException) {
                // 소리 때문에 실패하는 폰이 있을 수 있어서, 소리 없이 한 번 더 만든다
                Log.w(TAG, "소리 넣어 만들기 실패, 소리 없이 다시 만든다", e)
                output.delete()
                onProgress(0.35f)
                runTransformer(buildComposition(scenes, files, withSound, overlay(), sound = false), output) { onProgress(0.35f + 0.6f * it) }
            }

            // 5) 갤러리에 저장
            val uri = withContext(Dispatchers.IO) { saveToGallery(output, info.fileStem) }
            onProgress(1f)
            return uri
        } finally {
            withContext(Dispatchers.IO) { workDir.deleteRecursively() }
        }
    }

    private fun videoDurationMs(file: File): Long? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                release()
            }
        }
    }.getOrNull()

    private fun hasAudioTrack(file: File): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally {
            extractor.release()
        }
    }.getOrDefault(false)

    /**
     * 칸 3줄의 영상 줄(소리 없이 화면만)을 만들어 겹치고, 위에 덧그림(overlay)을 얹은 합성.
     * sound 면 소리 전용 줄을 하나 더 둔다: 장면마다 영상이 있는 칸 중 맨 위 영상의 소리, 소리가 없으면 조용한 소리(silence.wav).
     * (그림으로 시작하는 줄에 소리를 억지로 넣는 방법은 여러 줄을 합칠 때 멈추는 경우가 있어서 쓰지 않는다)
     */
    private suspend fun buildComposition(
        scenes: List<ExportScene>,
        files: Map<String, File>,
        withSound: Set<String>,
        overlay: RecapOverlay,
        sound: Boolean,
    ): Composition {
        val fillers = mutableMapOf<Int, Uri>()
        suspend fun filler(height: Int): Uri = fillers.getOrPut(height) {
            withContext(Dispatchers.IO) {
                val bitmap = Bitmap.createBitmap(ReelLayout.WIDTH, height, Bitmap.Config.ARGB_8888).apply { eraseColor(BackgroundDark.toArgb()) }
                val file = File(workDir, "filler_$height.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Uri.fromFile(file)
            }
        }

        val sequences = (0 until LANES).map { lane ->
            val items = scenes.map { scene ->
                val rect = scene.lanes.getOrNull(lane)
                val log = (scene.step as? RecapScene)?.clips?.getOrNull(lane)?.log
                val file = log?.let { files[it.id] }
                val height = rect?.height ?: laneRects(1).first().height
                if (rect != null && file != null && scene.audioLane != null) {
                    EditedMediaItem.Builder(
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(file))
                            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(scene.durationMs).build())
                            .build(),
                    )
                        .setRemoveAudio(true) // 소리는 아래 소리 전용 줄에서
                        .setEffects(Effects(emptyList(), listOf(laneSize(height))))
                        .build()
                } else {
                    // 칸이 비거나(검은 화면) 칸이 없는 때: 검은 그림을 장면 길이만큼
                    EditedMediaItem.Builder(MediaItem.Builder().setUri(filler(height)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(scene.durationMs).build())
                        .setFrameRate(ReelLayout.FRAME_RATE)
                        .setRemoveAudio(true)
                        .setEffects(Effects(emptyList(), listOf(laneSize(height))))
                        .build()
                }
            }
            EditedMediaItemSequence(items)
        }

        // 소리 전용 줄(영상 칸 줄 뒤에 둬서 칸 번호 0~2 는 그대로)
        val soundSource = scenes.map { scene ->
            val clip = scene.audioLane?.let { (scene.step as? RecapScene)?.clips?.getOrNull(it)?.log }
            clip?.takeIf { it.id in withSound }?.let { files[it.id] }
        }
        val audioSequence = if (sound && soundSource.any { it != null }) {
            val silence = withContext(Dispatchers.IO) { writeSilence(scenes.maxOf { it.durationMs }) }
            EditedMediaItemSequence(
                scenes.mapIndexed { i, scene ->
                    val clipping = MediaItem.ClippingConfiguration.Builder().setEndPositionMs(scene.durationMs).build()
                    val source = soundSource[i]
                    if (source != null) {
                        EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(clipping).build())
                            .setRemoveVideo(true)
                            .build()
                    } else {
                        EditedMediaItem.Builder(
                            MediaItem.Builder().setUri(Uri.fromFile(silence)).setMimeType(MimeTypes.AUDIO_WAV).setClippingConfiguration(clipping).build(),
                        ).build()
                    }
                },
            )
        } else {
            null
        }
        return Composition.Builder(listOfNotNull(*sequences.toTypedArray(), audioSequence))
            .setVideoCompositorSettings(LaneCompositorSettings(scenes))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
            .build()
    }

    /** 조용한 소리 파일(48kHz, 1채널, 16비트 WAV). 소리 없는 장면(지도·검은 화면)을 채운다 */
    private fun writeSilence(durationMs: Long): File {
        val rate = 48_000
        val dataBytes = (rate * durationMs / 1000).toInt() * 2
        val file = File(workDir, "silence.wav")
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(ByteArray(dataBytes))
        }
        return file
    }

    /** 칸 크기로 맞추고 넘치는 부분은 가운데를 잘라 꽉 채운다(앱 재생 화면과 같게) */
    private fun laneSize(height: Int) =
        Presentation.createForWidthAndHeight(ReelLayout.WIDTH, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

    /** Transformer 로 파일을 만든다. 취소되면 만들던 것을 멈춘다 */
    private suspend fun runTransformer(composition: Composition, output: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            coroutineScope {
                lateinit var transformer: Transformer
                val ticker = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        delay(300)
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    }
                }
                try {
                    suspendCancellableCoroutine { cont ->
                        transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        if (cont.isActive) cont.resume(Unit)
                                    }

                                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                        if (cont.isActive) cont.resumeWithException(exportException)
                                    }
                                },
                            )
                            .build()
                        cont.invokeOnCancellation { transformer.cancel() }
                        transformer.start(composition, output.absolutePath)
                    }
                } finally {
                    ticker.cancel()
                }
            }
        }

    /** 지도 사진(본문 크기). 인터넷이 안 되는 등으로 못 찍으면 null(연보라 바탕에 핀만 그린다) */
    private suspend fun mapSnapshot(camera: ReelCamera): Bitmap? = withContext(Dispatchers.Main) {
        MapLibre.getInstance(context)
        val center = camera.center
        val options = MapSnapshotter.Options(camera.width, camera.height)
            .withStyleBuilder(Style.Builder().fromUri(MAP_STYLE_URL))
            .withCameraPosition(CameraPosition.Builder().target(LatLng(center.lat, center.lng)).zoom(camera.zoom).build())
            .withPixelRatio(1f)
            .withLogo(false)
        val snapshotter = MapSnapshotter(context, options)
        try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine { cont ->
                    snapshotter.start(
                        { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)) },
                        { error ->
                            Log.w(TAG, "지도 사진 실패: $error")
                            if (cont.isActive) cont.resume(null)
                        },
                    )
                    cont.invokeOnCancellation { snapshotter.cancel() }
                }
            }
        } finally {
            snapshotter.cancel()
        }
    }

    /** 프로필 사진(정사각형). 없거나 못 받으면 null(지구본 기본 프로필을 그린다) */
    private suspend fun loadAvatar(url: String?): Bitmap? {
        url ?: return null
        val request = ImageRequest.Builder(context).data(url).size(AVATAR_PX).allowHardware(false).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    /** 만든 파일을 갤러리 "LOG US" 앨범(동영상 폴더)에 저장한다. 안드로이드 9 이하는 저장 공간 권한이 필요하다 */
    private suspend fun saveToGallery(file: File, stem: String): Uri {
        val name = "$stem.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("갤러리에 저장하지 못했어요")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("갤러리에 저장하지 못했어요")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM).apply { mkdirs() }
        val target = File(dir, name)
        file.copyTo(target, overwrite = true)
        // 갤러리 앱이 바로 보이도록 알리고, 공유에 쓸 주소를 받는다
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4")) { _, uri ->
                if (cont.isActive) cont.resume(uri ?: Uri.fromFile(target))
            }
        }
    }

    private companion object {
        const val TAG = "RecapExporter"
        const val LANES = RECAP_MAX_CLIPS
        const val ALBUM = "LOG US"
        const val AVATAR_PX = 160
    }
}

/**
 * 칸 영상들을 한 화면(1080×1920)에 놓는 규칙: 칸 i 는 그 장면의 i번째 자리(위에서부터)에 놓고,
 * 그 장면에 없는 칸(지도 장면, 칸이 적은 장면)은 보이지 않게 한다.
 */
private class LaneCompositorSettings(private val scenes: List<ExportScene>) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>) = Size(ReelLayout.WIDTH, ReelLayout.HEIGHT)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val scene = sceneAt(scenes, presentationTimeUs / 1000)
        val rect = scene?.lanes?.getOrNull(inputId)
            ?: return OverlaySettings.Builder().setAlphaScale(0f).build()
        // 화면 좌표(-1~1, 위가 +1)로: 칸의 위쪽 가운데를 그 자리 위쪽 가운데에 맞춘다
        val y = 1f - 2f * rect.top / ReelLayout.HEIGHT
        return OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 1f)
            .setBackgroundFrameAnchor(0f, y)
            .build()
    }
}

private fun sceneAt(scenes: List<ExportScene>, ms: Long): ExportScene? =
    scenes.lastOrNull { ms >= it.startMs } ?: scenes.firstOrNull()

/**
 * 합친 화면 위에 덧그리는 그림(1080×1920, 투명 바탕): 위 로고·여정 이름·날짜·시각, 영상 장면의 프로필·장소 이름·칸 사이 흰 선,
 * 지도 장면의 지도 사진·점선·프로필 핀(같은 건물은 흰 말풍선에 묶음)·"© OpenStreetMap".
 * 움직이지 않는 장면은 한 번 그린 그림을 다시 쓴다.
 */
private class RecapOverlay(
    context: Context,
    private val info: RecapExportInfo,
    private val scenes: List<ExportScene>,
    private val members: Map<String, Member>,
    private val avatars: Map<String, Bitmap?>,
    private val cameras: Map<Int, ReelCamera>,
    private val snapshots: Map<Int, Bitmap>,
) : BitmapOverlay() {
    private val globe = ContextCompat.getDrawable(context, R.drawable.ic_globe)
    private val cache = mutableMapOf<Int, Bitmap>()
    private val buffers = List(2) { Bitmap.createBitmap(ReelLayout.WIDTH, ReelLayout.HEIGHT, Bitmap.Config.ARGB_8888) }
    private var bufferIndex = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = PhotoFrame.toArgb()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val index = scenes.indexOf(sceneAt(scenes, ms)).coerceAtLeast(0)
        val scene = scenes[index]
        val step = scene.step
        val local = ms - scene.startMs
        val (fraction, arrived) = if (step is RecapMapScene) mapProgress(step, local) else 1f to true
        // 움직이는 동안만 매번 새로 그린다(두 장을 번갈아 써서 바뀐 그림이 확실히 올라가게)
        if (step is RecapMapScene && !arrived) {
            bufferIndex = 1 - bufferIndex
            return buffers[bufferIndex].also { draw(it, index, scene, fraction, false) }
        }
        return cache.getOrPut(index) {
            Bitmap.createBitmap(ReelLayout.WIDTH, ReelLayout.HEIGHT, Bitmap.Config.ARGB_8888).also { draw(it, index, scene, 1f, true) }
        }
    }

    private fun draw(bitmap: Bitmap, index: Int, scene: ExportScene, fraction: Float, arrived: Boolean) {
        bitmap.eraseColor(0)
        val canvas = Canvas(bitmap)
        when (val step = scene.step) {
            is RecapMapScene -> drawMap(canvas, index, step, fraction, arrived)
            is RecapScene -> drawLanes(canvas, scene, step)
        }
        drawHeader(canvas, scene.step)
    }

    private fun drawHeader(canvas: Canvas, step: RecapStep) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = BackgroundDark.toArgb()
        canvas.drawRect(0f, 0f, ReelLayout.WIDTH.toFloat(), ReelLayout.HEADER.toFloat(), paint)
        globe?.let {
            val icon = it.toBitmap(44, 44)
            paint.colorFilter = android.graphics.PorterDuffColorFilter(white, android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(icon, 56f, 58f, paint)
            paint.colorFilter = null
        }
        text(canvas, "LOG EARTH", 116f, 94f, 32f, white, bold = true, spacing = 0.15f)
        text(canvas, info.title, 56f, 186f, 58f, white, bold = true, maxWidth = 760f)
        text(canvas, info.subtitle, 56f, 246f, 34f, TextSecondaryDark.toArgb())
        // 오른쪽 위 시각
        val from = (step as? RecapMapScene)?.fromMinute
        val label = if (from != null) "${minuteLabel(from)} → ${minuteLabel(step.startMinute)}" else minuteLabel(step.startMinute)
        paint.textSize = 40f
        paint.typeface = Typeface.DEFAULT_BOLD
        val w = paint.measureText(label) + 48f
        paint.color = PhotoScrim.toArgb()
        canvas.drawRoundRect(RectF(ReelLayout.WIDTH - 48f - w, 54f, ReelLayout.WIDTH - 48f, 118f), 32f, 32f, paint)
        text(canvas, label, ReelLayout.WIDTH - 48f - w + 24f, 100f, 40f, white, bold = true)
    }

    private fun drawLanes(canvas: Canvas, scene: ExportScene, step: RecapScene) {
        step.clips.forEachIndexed { i, clip ->
            val rect = scene.lanes.getOrNull(i) ?: return@forEachIndexed
            val top = rect.top.toFloat()
            val bottom = (rect.top + rect.height).toFloat()
            // 칸 사이 흰 선
            if (i > 0) {
                paint.reset()
                paint.color = white
                canvas.drawRect(0f, top - ReelLayout.DIVIDER, ReelLayout.WIDTH.toFloat(), top, paint)
            }
            val place = clip.log?.placeName
            if (place != null) {
                paint.reset()
                paint.shader = LinearGradient(0f, bottom - 140f, 0f, bottom, 0, PhotoScrim.toArgb(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, bottom - 140f, ReelLayout.WIDTH.toFloat(), bottom, paint)
                paint.shader = null
                text(canvas, place, 36f, bottom - 34f, 36f, white, bold = true, maxWidth = ReelLayout.WIDTH - 72f)
            }
            drawFace(canvas, clip.uid, clip.colorIndex, 36f + 46f, top + 36f + 46f, 46f, badge = false)
        }
    }

    private fun drawMap(canvas: Canvas, index: Int, step: RecapMapScene, fraction: Float, arrived: Boolean) {
        val top = ReelLayout.HEADER.toFloat()
        paint.reset()
        paint.color = Surface.toArgb()
        canvas.drawRect(0f, top, ReelLayout.WIDTH.toFloat(), ReelLayout.HEIGHT.toFloat(), paint)
        snapshots[index]?.let { canvas.drawBitmap(it, 0f, top, null) }
        val camera = cameras[index] ?: return
        fun at(p: GeoPoint): Pair<Float, Float> = camera.pixel(p).let { (x, y) -> x to y + top }
        fun lerp(a: GeoPoint, b: GeoPoint) = GeoPoint(a.lat + (b.lat - a.lat) * fraction, a.lng + (b.lng - a.lng) * fraction)

        if (!arrived) {
            step.pins.forEach { pin ->
                val from = pin.from
                val to = pin.to
                if (from != null && to != null) {
                    val (x1, y1) = at(from)
                    val (x2, y2) = at(lerp(from, to))
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 9f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.pathEffect = DashPathEffect(floatArrayOf(6f, 20f), 0f)
                    paint.color = MemberColors[pin.colorIndex % MemberColors.size].toArgb()
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
            step.pins.forEach { pin ->
                val point = when {
                    pin.from == null -> pin.to
                    pin.to == null -> pin.from
                    else -> lerp(pin.from, pin.to)
                } ?: return@forEach
                val alpha = when {
                    pin.from == null -> fraction
                    pin.to == null -> 1f - fraction
                    else -> 1f
                }
                val (x, y) = at(point)
                drawPin(canvas, listOf(pin), x, y, alpha)
            }
        } else {
            val byUid = step.pins.associateBy { it.uid }
            step.groups.forEach { uids ->
                val pins = uids.mapNotNull { byUid[it] }
                val points = pins.mapNotNull { it.to }
                if (points.isEmpty()) return@forEach
                val (x, y) = at(GeoPoint(points.map { it.lat }.average(), points.map { it.lng }.average()))
                drawPin(canvas, pins, x, y, 1f)
            }
        }
        // 지도 출처
        paint.reset()
        paint.textSize = 26f
        val credit = "© OpenStreetMap"
        val w = paint.measureText(credit) + 28f
        paint.color = PhotoScrim.toArgb()
        canvas.drawRoundRect(RectF(ReelLayout.WIDTH - 24f - w, ReelLayout.HEIGHT - 72f, ReelLayout.WIDTH - 24f, ReelLayout.HEIGHT - 28f), 12f, 12f, paint)
        text(canvas, credit, ReelLayout.WIDTH - 24f - w + 14f, ReelLayout.HEIGHT - 41f, 26f, white)
    }

    /** 혼자면 흰 동그라미 안 프로필, 여럿이면 흰 말풍선 안에 겹친 프로필 */
    private fun drawPin(canvas: Canvas, pins: List<RecapPin>, cx: Float, cy: Float, alpha: Float) {
        val r = 44f
        val step = r * 1.55f
        val width = r * 2 + step * (pins.size - 1)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = white
        paint.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(RectF(cx - width / 2 - 12f, cy - r - 12f, cx + width / 2 + 12f, cy + r + 12f), r + 12f, r + 12f, paint)
        pins.forEachIndexed { i, pin ->
            drawFace(canvas, pin.uid, pin.colorIndex, cx - width / 2 + r + i * step, cy, r, badge = true, alpha = alpha)
        }
    }

    /** 프로필 동그라미(구성원 색 테두리). badge 면 오른쪽 아래에 이름 첫 글자(색만으로 구분하지 않게) */
    private fun drawFace(canvas: Canvas, uid: String, colorIndex: Int, cx: Float, cy: Float, r: Float, badge: Boolean, alpha: Float = 1f) {
        val color = MemberColors[colorIndex % MemberColors.size].toArgb()
        val a = (alpha * 255).toInt()
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.alpha = a
        canvas.drawCircle(cx, cy, r, paint)
        val inner = r - 7f
        val photo = avatars[uid]
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (inner * 2) / min(photo.width, photo.height)
            shader.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
                },
            )
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = shader
            paint.alpha = a
            canvas.drawCircle(cx, cy, inner, paint)
            paint.shader = null
        } else {
            // 기본 프로필: 연보라 동그라미 가운데 지구본
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Surface.toArgb()
            paint.alpha = a
            canvas.drawCircle(cx, cy, inner, paint)
            globe?.let {
                val size = (inner * 1.1f).toInt()
                val icon = it.toBitmap(size, size)
                paint.colorFilter = android.graphics.PorterDuffColorFilter(PrimaryStrong.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(icon, cx - size / 2f, cy - size / 2f, paint)
                paint.colorFilter = null
            }
        }
        if (badge) {
            val br = r * 0.42f
            val bx = cx + r * 0.72f
            val by = cy + r * 0.72f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = white
            paint.alpha = a
            canvas.drawCircle(bx, by, br + 3f, paint)
            paint.color = color
            paint.alpha = a
            canvas.drawCircle(bx, by, br, paint)
            val initial = members[uid]?.nickname?.firstOrNull()?.toString() ?: "?"
            paint.color = white
            paint.alpha = a
            paint.textSize = br * 1.1f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(initial, bx, by - (paint.descent() + paint.ascent()) / 2, paint)
        }
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        spacing: Float = 0f,
        maxWidth: Float = Float.MAX_VALUE,
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.textSize = size
        paint.letterSpacing = spacing
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        var shown = value
        if (paint.measureText(shown) > maxWidth) {
            while (shown.isNotEmpty() && paint.measureText("$shown…") > maxWidth) shown = shown.dropLast(1)
            shown = "$shown…"
        }
        canvas.drawText(shown, x, y, paint)
    }
}
