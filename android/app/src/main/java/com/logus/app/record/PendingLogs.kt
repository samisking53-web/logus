package com.logus.app.record

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 폰에 보관해 두고 인터넷이 연결되기를 기다리는 기록 하나(S05 "여정에 올리기"를 오프라인에서 누른 것) */
data class PendingLog(
    /** 미리 정해 둔 기록 ID(올라가면 logs/{logId} 가 된다) */
    val logId: String,
    val journeyId: String,
    val uid: String,
    val capturedAtMillis: Long,
    val capturedTz: String,
    val themes: List<String>,
    /** L01 에서 고른 장소(위치 없이 저장이면 null) */
    val place: PickedPlace?,
    /** 폰에 보관한 영상 / 첫 장면 사진(N01 대기 카드) */
    val videoFile: File,
    val thumbFile: File,
)

/** 기다리던 기록이 다 올라갔다(화면 안 안내·기록 보기 다시 읽기) */
data class UploadedLog(val journeyId: String, val logId: String, val coinsGranted: Int)

/**
 * 오프라인 기록 보관함(2026-11-03 팀 결정: 오프라인에서 찍은 기록은 폰에 보관했다가 인터넷이 연결되면 자동으로 올린다).
 * - 보관 위치: 앱 전용 폴더 filesDir/pending_logs/ 에 기록마다 {logId}.mp4(영상)·{logId}.jpg(첫 장면)·{logId}.json(내용).
 *   임시 폴더(cacheDir)가 아니라서 안드로이드가 저장 공간이 모자라도 지우지 않는다. 다른 앱은 읽지 못한다.
 * - 올리기: WorkManager(안드로이드 공식 예약 작업)에 "인터넷이 연결되면 LogUploadWorker 실행"을 맡긴다.
 *   앱이 꺼져 있거나 폰을 다시 켜도 예약은 남아 있고, 실패하면 30초부터 점점 길게 기다렸다가 다시 시도한다.
 * - 화면(N01 대기 카드)은 [items] 를, 다 올라간 안내는 [uploaded] 를 본다. 이 폰에서만 보인다.
 */
object PendingLogs {
    private const val FOLDER = "pending_logs"
    private const val WORK_TAG = "log_upload"
    private const val TAG = "PendingLogs"
    internal const val KEY_LOG_ID = "logId"

    private val _items = MutableStateFlow<List<PendingLog>>(emptyList())
    /** 보관 중인 기록(찍은 순서) */
    val items: StateFlow<List<PendingLog>> = _items.asStateFlow()

    private val _uploaded = MutableSharedFlow<UploadedLog>(extraBufferCapacity = 8)
    val uploaded: SharedFlow<UploadedLog> = _uploaded.asSharedFlow()

    @Volatile
    private var loaded = false

    private fun folder(context: Context) = File(context.applicationContext.filesDir, FOLDER).apply { mkdirs() }

    /** 지금 인터넷을 쓸 수 있는지(와이파이·데이터가 켜져 있고 실제로 연결이 확인된 상태) */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 앱을 켤 때: 폰에 남아 있는 보관 기록을 읽고, 올리기 예약이 빠져 있으면 다시 건다(한 번만) */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        val all = folder(context).listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { read(context, it.nameWithoutExtension) }
            .sortedBy { it.capturedAtMillis }
        _items.value = all
        all.forEach { enqueue(context, it.logId) }
    }

    /**
     * 기록을 폰에 보관하고 "인터넷이 연결되면 올리기"를 예약한다(S05 에서 오프라인이거나 올리다 끊겼을 때).
     * 찍은 영상은 S05 를 닫으면 지워지는 임시 파일이라 앱 전용 폴더로 복사해 둔다.
     */
    suspend fun add(
        context: Context,
        logId: String,
        journeyId: String,
        uid: String,
        video: File,
        capturedAtMillis: Long,
        capturedTz: String,
        themes: List<String>,
        place: PickedPlace?,
    ): PendingLog = withContext(Dispatchers.IO) {
        val dir = folder(context)
        val videoFile = File(dir, "$logId.mp4")
        val thumbFile = File(dir, "$logId.jpg")
        video.copyTo(videoFile, overwrite = true)
        videoThumbnailJpeg(videoFile)?.let { thumbFile.writeBytes(it) }
        val json = JSONObject()
            .put("logId", logId)
            .put("journeyId", journeyId)
            .put("uid", uid)
            .put("capturedAtMillis", capturedAtMillis)
            .put("capturedTz", capturedTz)
            .put("themes", JSONArray(themes))
        if (place != null) {
            json.put(
                "place",
                JSONObject()
                    .put("lat", place.point.lat)
                    .put("lng", place.point.lng)
                    .put("name", place.name)
                    .put("address", place.address),
            )
        }
        // 내용 파일을 맨 마지막에 써야 반쯤 저장된 기록을 읽지 않는다
        File(dir, "$logId.json").writeText(json.toString())
        val item = PendingLog(logId, journeyId, uid, capturedAtMillis, capturedTz, themes, place, videoFile, thumbFile)
        _items.update { list -> (list.filterNot { it.logId == logId } + item).sortedBy { it.capturedAtMillis } }
        enqueue(context, logId)
        item
    }

    /** 보관한 기록 하나를 읽는다(없거나 망가졌으면 null) */
    fun read(context: Context, logId: String): PendingLog? {
        val dir = folder(context)
        val file = File(dir, "$logId.json")
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val themes = json.optJSONArray("themes")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
            val place = json.optJSONObject("place")?.let {
                PickedPlace(GeoPoint(it.getDouble("lat"), it.getDouble("lng")), it.getString("name"), it.getString("address"))
            }
            PendingLog(
                logId = json.getString("logId"),
                journeyId = json.getString("journeyId"),
                uid = json.getString("uid"),
                capturedAtMillis = json.getLong("capturedAtMillis"),
                capturedTz = json.getString("capturedTz"),
                themes = themes,
                place = place,
                videoFile = File(dir, "$logId.mp4"),
                thumbFile = File(dir, "$logId.jpg"),
            )
        }.onFailure { Log.w(TAG, "보관한 기록 읽기 실패: $logId", it) }.getOrNull()
    }

    /** 다 올렸거나(또는 더는 올릴 수 없어) 보관함에서 뺀다 */
    fun remove(context: Context, logId: String) {
        val dir = folder(context)
        listOf("json", "mp4", "jpg").forEach { File(dir, "$logId.$it").delete() }
        _items.update { list -> list.filterNot { it.logId == logId } }
    }

    /** 다 올라갔다고 화면에 알린다(앱을 보고 있으면 MainScreen 이 안내를 띄우고 기록 보기를 다시 읽는다) */
    fun notifyUploaded(done: UploadedLog) {
        _uploaded.tryEmit(done)
    }

    /** 지금 올리는 중인 기록 ID(N01 대기 카드의 "올리는 중…") */
    fun uploadingIds(context: Context): Flow<Set<String>> =
        WorkManager.getInstance(context.applicationContext).getWorkInfosByTagFlow(WORK_TAG).map { infos ->
            infos.filter { it.state == WorkInfo.State.RUNNING }
                .flatMap { it.tags }
                .filter { it.startsWith("log_") }
                .map { it.removePrefix("log_") }
                .toSet()
        }

    /** "인터넷이 연결되면 LogUploadWorker 실행"을 예약한다. 같은 기록은 한 번만 예약된다 */
    private fun enqueue(context: Context, logId: String) {
        val request = OneTimeWorkRequestBuilder<LogUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_LOG_ID to logId))
            .addTag(WORK_TAG)
            .addTag("log_$logId")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("upload_$logId", ExistingWorkPolicy.KEEP, request)
    }
}
