package com.yu.syncon.service.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.util.DiagnosticLog
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyResetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Starting 4:00 AM daily reset & data retention cleanup")
            val repository = (applicationContext as? SyncOnApp)?.repository ?: UsageRepository(applicationContext)
            repository.performDailyReset()
            Log.i(TAG, "Daily reset completed successfully")
            DiagnosticLog.record(applicationContext, "daily_maintenance_completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily reset failed; requesting retry", e)
            DiagnosticLog.record(applicationContext, "daily_maintenance_failed", e.message)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "syncon_daily_4am_reset"
        private const val TAG = "DailyResetWorker"

        /**
         * Enqueues a 24-hour periodic work request with an initial delay calculated
         * so that it triggers at the next upcoming 4:00 AM.
         */
        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            var next4Am = now.withHour(4).withMinute(0).withSecond(0).withNano(0)
            if (now.isAfter(next4Am)) {
                next4Am = next4Am.plusDays(1)
            }
            val initialDelayMillis = Duration.between(now, next4Am).toMillis()

            val workRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Scheduled DailyResetWorker with initial delay ${initialDelayMillis / 1000}s to next 4 AM")
        }

        /**
         * Manually and immediately runs the daily reset logic.
         * Used for QA and debug testing from the Settings screen.
         */
        suspend fun triggerImmediateReset(context: Context) {
            Log.i(TAG, "Triggering immediate manual daily reset (debug)")
            val repository = (context.applicationContext as? SyncOnApp)?.repository ?: UsageRepository(context.applicationContext)
            repository.performDailyReset()
        }
    }
}
