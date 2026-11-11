package com.logus.app.record

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import com.logus.app.notify.UploadNotification
import java.io.FileNotFoundException

/**
 * 폰에 보관한 기록 올리기(PendingLogs 가 예약, WorkManager 가 인터넷이 연결되면 실행한다. 앱이 꺼져 있어도 된다).
 * ① 영상·첫 장면·기록 문서를 올린다(S05 와 같은 LogRepository.uploadVideoLog, 미리 정한 기록 ID 라 두 번 생기지 않는다)
 * ② 위치를 골랐으면 saveLogLocation 으로 위치 저장 + 5코인. 오프라인에서 고른 위치는 이때 장소 이름을 다시 찾는다.
 * ③ 보관함에서 빼고, 앱을 보고 있으면 화면 안내, 아니면 폰 알림("기록 N개를 여정에 올렸어요").
 * 인터넷이 끊기는 등 다시 하면 될 실패는 나중에 다시 한다(Result.retry). 여정에서 나가 권한이 없는 등
 * 다시 해도 안 되는 실패는 보관함에서 빼고 알린다. 로그인한 사람이 바뀌었으면 그 사람이 다시 로그인할 때까지 기다린다.
 */
class LogUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val logId = inputData.getString(PendingLogs.KEY_LOG_ID) ?: return Result.failure()
        val item = PendingLogs.read(applicationContext, logId) ?: return Result.success() // 이미 올렸거나 지운 기록
        if (!item.videoFile.exists()) {
            // 영상 파일이 없어졌으면(앱 데이터 삭제 등) 올릴 수 없다
            PendingLogs.remove(applicationContext, logId)
            UploadNotification.failed(applicationContext)
            return Result.failure()
        }
        if (Firebase.auth.currentUser?.uid != item.uid) return Result.retry()
        val logs = LogRepository()
        return try {
            logs.uploadVideoLog(
                journeyId = item.journeyId,
                uid = item.uid,
                video = item.videoFile,
                capturedAtMillis = item.capturedAtMillis,
                capturedTz = item.capturedTz,
                themes = item.themes,
                logId = item.logId,
                resumed = true,
            )
            val coins = item.place?.let { saveLocation(logs, item, it) } ?: 0
            PendingLogs.remove(applicationContext, logId)
            PendingLogs.notifyUploaded(UploadedLog(item.journeyId, logId, coins))
            UploadNotification.uploaded(applicationContext, coins)
            Result.success()
        } catch (e: RetryLater) {
            Result.retry()
        } catch (e: Exception) {
            if (e.isPermanent()) {
                Log.w(TAG, "보관한 기록을 올릴 수 없어 뺀다: $logId", e)
                PendingLogs.remove(applicationContext, logId)
                UploadNotification.failed(applicationContext)
                Result.failure()
            } else {
                Log.w(TAG, "보관한 기록 올리기 실패, 나중에 다시: $logId", e)
                Result.retry()
            }
        }
    }

    /** 위치 저장 + 코인. 인터넷 문제면 나중에 다시(기록은 이미 올라가 있어 다음에는 위치만 한다), 그 밖의 실패는 위치 없이 끝낸다 */
    private suspend fun saveLocation(logs: LogRepository, item: PendingLog, picked: PickedPlace): Int {
        val place = if (picked.address == OFFLINE_PLACE_ADDRESS) {
            runCatching { PlaceRepository().reverse(picked.point) }.getOrDefault(picked)
        } else {
            picked
        }
        return try {
            logs.saveLocation(item.journeyId, item.logId, place).coins
        } catch (e: Exception) {
            if (e is FirebaseNetworkException ||
                (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.UNAVAILABLE)
            ) {
                throw RetryLater()
            }
            Log.w(TAG, "보관한 기록의 위치 저장 실패(기록은 올라감)", e)
            0
        }
    }

    /** 다시 해도 안 되는 실패(권한 없음·여정이 없어짐·보관한 파일이 없어짐) */
    private fun Exception.isPermanent(): Boolean = when (this) {
        is FirebaseFirestoreException ->
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED || code == FirebaseFirestoreException.Code.NOT_FOUND
        is StorageException -> errorCode == StorageException.ERROR_NOT_AUTHORIZED
        is FileNotFoundException -> true
        else -> false
    }

    private class RetryLater : Exception()

    private companion object {
        const val TAG = "LogUploadWorker"
    }
}
