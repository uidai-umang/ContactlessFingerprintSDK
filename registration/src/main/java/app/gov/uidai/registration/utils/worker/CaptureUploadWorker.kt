package app.gov.uidai.registration.utils.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.usecase.CaptureQueueManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CaptureUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val captureQueueManager: CaptureQueueManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CaptureUploadWorker"
        const val WORK_NAME = "capture_sync_work"
    }

    // Runs every 15 mins via periodic WorkManager schedule.
    // Delegates all logic to CaptureQueueManager.syncPendingCaptures().
    // Returns retry if sync fails — WorkManager will try again next cycle.
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic capture sync")

        return when (val result = captureQueueManager.syncPendingCaptures()) {
            is ApiResult.Success -> {
                Log.d(TAG, "Capture sync completed successfully")
                Result.success()
            }

            is ApiResult.Error -> {
                Log.w(TAG, "Capture sync failed: ${result.message}")
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    // After 3 attempts in this cycle give up
                    // WorkManager will try again in next 15 min window
                    Result.failure()
                }
            }
        }
    }
}