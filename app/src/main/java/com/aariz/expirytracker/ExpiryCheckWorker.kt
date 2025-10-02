package com.aariz.expirytracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)
    private val firestoreRepository = FirestoreRepository()
    private val prefs: SharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ExpiryCheckWorker"
        private const val LAST_NOTIFICATION_KEY = "last_notification_timestamp"
        private const val MIN_NOTIFICATION_INTERVAL_HOURS = 6 // Minimum 6 hours between notifications
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting expiry check work")

            // Check if notifications are enabled
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled, skipping check")
                return Result.success()
            }

            // Check if we should throttle notifications
            if (shouldThrottleNotifications()) {
                Log.d(TAG, "Throttling notifications - too soon since last notification")
                return Result.success()
            }

            // Check if user is authenticated
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.d(TAG, "User not authenticated, skipping check")
                return Result.success()
            }

            // Get days before expiry threshold from settings
            val daysBefore = prefs.getInt("days_before_expiry", 2)

            // Fetch grocery items from Firestore
            val result = firestoreRepository.getUserGroceryItems()

            if (result.isFailure) {
                Log.e(TAG, "Failed to fetch items: ${result.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val items = result.getOrNull() ?: emptyList()
            Log.d(TAG, "Fetched ${items.size} items")

            // Filter items that are expiring or expired
            val expiringItems = items.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                daysLeft <= daysBefore && daysLeft >= 0 // Items expiring within threshold
            }

            val expiredItems = items.filter { item ->
                calculateDaysLeft(item.expiryDate) < 0 // Expired items
            }

            Log.d(TAG, "Found ${expiringItems.size} expiring items and ${expiredItems.size} expired items")

            // Send notifications
            if (expiredItems.isNotEmpty()) {
                sendNotificationForItems(expiredItems, isExpired = true)
            } else if (expiringItems.isNotEmpty()) {
                sendNotificationForItems(expiringItems, isExpired = false)
            }

            // Update last notification timestamp if we sent a notification
            if (expiredItems.isNotEmpty() || expiringItems.isNotEmpty()) {
                updateLastNotificationTimestamp()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in expiry check worker", e)
            Result.retry()
        }
    }

    private fun shouldThrottleNotifications(): Boolean {
        val lastNotificationTime = prefs.getLong(LAST_NOTIFICATION_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastNotification = TimeUnit.MILLISECONDS.toHours(currentTime - lastNotificationTime)

        return hoursSinceLastNotification < MIN_NOTIFICATION_INTERVAL_HOURS
    }

    private fun updateLastNotificationTimestamp() {
        prefs.edit()
            .putLong(LAST_NOTIFICATION_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun sendNotificationForItems(items: List<GroceryItem>, isExpired: Boolean) {
        if (items.isEmpty()) return

        val sortedItems = items.sortedBy { calculateDaysLeft(it.expiryDate) }
        val firstItem = sortedItems.first()
        val daysLeft = calculateDaysLeft(firstItem.expiryDate)

        if (items.size == 1) {
            // Single item notification
            notificationHelper.showExpiryNotification(
                itemName = firstItem.name,
                daysLeft = daysLeft,
                totalItems = 1
            )
        } else {
            // Multiple items notification
            notificationHelper.showExpiryNotification(
                itemName = firstItem.name,
                daysLeft = daysLeft,
                totalItems = items.size
            )
        }

        Log.d(TAG, "Sent notification for ${items.size} items")
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.isLenient = false

            val expiry = sdf.parse(expiryDate) ?: return 0

            val expiryCalendar = Calendar.getInstance().apply {
                time = expiry
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffInMillis = expiryCalendar.timeInMillis - todayCalendar.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating days left: ${e.message}", e)
            0
        }
    }
}