package com.aariz.expirytracker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {

    companion object {
        private const val EXPIRY_CHECK_WORK = "expiry_check_work"
        private const val CHECK_INTERVAL_HOURS = 12L // Check twice daily
    }

    fun scheduleExpiryChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Need internet for Firestore
            .setRequiresBatteryNotLow(true) // Don't drain battery
            .build()

        val expiryCheckRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
            CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES) // Start checking after 1 minute
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EXPIRY_CHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            expiryCheckRequest
        )
    }

    fun cancelExpiryChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(EXPIRY_CHECK_WORK)
    }

    fun scheduleImmediateCheck() {
        val immediateCheckRequest = OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(immediateCheckRequest)
    }
}
