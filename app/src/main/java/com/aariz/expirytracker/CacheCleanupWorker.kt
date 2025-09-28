package com.aariz.expirytracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("CacheCleanupWorker", "Starting cache cleanup...")

            val productCacheRepository = ProductCacheRepository()
            val result = productCacheRepository.clearExpiredCache()

            if (result.isSuccess) {
                Log.d("CacheCleanupWorker", "Cache cleanup completed successfully")
                Result.success()
            } else {
                Log.e("CacheCleanupWorker", "Cache cleanup failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("CacheCleanupWorker", "Cache cleanup worker failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "cache_cleanup_work"

        fun schedulePeriodicCleanup(context: Context) {
            val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
                7, TimeUnit.DAYS // Run weekly
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            Log.d("CacheCleanupWorker", "Scheduled periodic cache cleanup")
        }

        fun cancelPeriodicCleanup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("CacheCleanupWorker", "Cancelled periodic cache cleanup")
        }
    }
}