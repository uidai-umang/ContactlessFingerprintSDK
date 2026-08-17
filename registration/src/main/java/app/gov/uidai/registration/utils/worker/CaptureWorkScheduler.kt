package app.gov.uidai.registration.utils.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CaptureWorkScheduler {

    // Schedules periodic sync every 15 mins.
    // KEEP_EXISTING prevents rescheduling if already queued —
    // safe to call multiple times (on app start, on login etc.)
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CaptureUploadWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CaptureUploadWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    // Cancels the periodic sync — call on logout
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(
            CaptureUploadWorker.WORK_NAME
        )
    }
}