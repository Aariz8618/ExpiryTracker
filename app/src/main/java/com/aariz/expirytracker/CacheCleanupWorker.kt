package com.aariz.expirytracker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CacheCleanupWorker"
        private const val WORK_NAME = "cache_cleanup_work"
        private const val CLEANUP_INTERVAL_DAYS = 7L

        fun schedulePeriodicCleanup(context: Context) {
            val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
                CLEANUP_INTERVAL_DAYS, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
            Log.d(TAG, "Cache cleanup scheduled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting cache cleanup")

            // Clean app cache
            applicationContext.cacheDir.deleteRecursively()

            // Clean external cache if available
            applicationContext.externalCacheDir?.deleteRecursively()

            Log.d(TAG, "Cache cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
            Result.failure()
        }
    }
}