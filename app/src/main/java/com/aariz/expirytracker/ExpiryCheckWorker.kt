package com.aariz.expirytracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(applicationContext)
    private val firestoreRepository = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        return try {
            Log.d("ExpiryCheckWorker", "Starting expiry check...")

            // Check if user is authenticated
            if (auth.currentUser == null) {
                Log.d("ExpiryCheckWorker", "User not authenticated, skipping check")
                return Result.success()
            }

            // Get all grocery items
            val result = firestoreRepository.getUserGroceryItems()
            if (result.isFailure) {
                Log.e("ExpiryCheckWorker", "Failed to fetch items: ${result.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val items = result.getOrNull() ?: emptyList()
            Log.d("ExpiryCheckWorker", "Checking ${items.size} items for expiry")

            // Check for items expiring soon (0-2 days)
            val expiringItems = items.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                daysLeft in 0..2 // Today, tomorrow, or day after tomorrow
            }

            if (expiringItems.isNotEmpty()) {
                Log.d("ExpiryCheckWorker", "Found ${expiringItems.size} expiring items")

                if (expiringItems.size == 1) {
                    val item = expiringItems.first()
                    val daysLeft = calculateDaysLeft(item.expiryDate)
                    notificationHelper.showExpiryNotification(item.name, daysLeft)
                } else {
                    // Multiple items - show summary notification
                    val minDaysLeft = expiringItems.minOf { calculateDaysLeft(it.expiryDate) }
                    notificationHelper.showExpiryNotification(
                        expiringItems.first().name,
                        minDaysLeft,
                        expiringItems.size
                    )
                }
            } else {
                Log.d("ExpiryCheckWorker", "No items expiring soon")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("ExpiryCheckWorker", "Error in expiry check", e)
            Result.retry()
        }
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val expiry = sdf.parse(expiryDate)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            val diff = expiry!!.time - today.time
            TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS).toInt()
        } catch (e: Exception) {
            Log.e("ExpiryCheckWorker", "Error calculating days left", e)
            Int.MAX_VALUE // Return high value to avoid false notifications
        }
    }
}