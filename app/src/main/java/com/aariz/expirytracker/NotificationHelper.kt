package com.aariz.expirytracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val EXPIRY_CHANNEL_ID = "expiry_notifications"
        const val EXPIRY_CHANNEL_NAME = "Expiry Reminders"
        const val EXPIRY_CHANNEL_DESCRIPTION = "Notifications for items about to expire"
        const val EXPIRY_NOTIFICATION_ID = 1001
        private const val TAG = "NotificationHelper"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EXPIRY_CHANNEL_ID,
                EXPIRY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = EXPIRY_CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    fun showExpiryNotification(itemName: String, daysLeft: Int, totalItems: Int = 1) {
        // Check if notifications are enabled in settings
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled in settings")
            return
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.d(TAG, "Notification permission not granted")
                return
            }
        }

        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message) = buildNotificationContent(itemName, daysLeft, totalItems)

        val notification = NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification sent successfully: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for notifications", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    private fun buildNotificationContent(itemName: String, daysLeft: Int, totalItems: Int): Pair<String, String> {
        val title: String
        val message: String

        when {
            // Single item expired
            totalItems == 1 && daysLeft < 0 -> {
                title = "⚠️ Item Expired!"
                message = "$itemName has expired. Check it now to avoid waste."
            }
            // Single item expiring today
            totalItems == 1 && daysLeft == 0 -> {
                title = "⏰ Expires Today!"
                message = "$itemName expires today. Use it soon!"
            }
            // Single item expiring tomorrow
            totalItems == 1 && daysLeft == 1 -> {
                title = "⏰ Expires Tomorrow"
                message = "$itemName expires tomorrow. Plan to use it!"
            }
            // Single item expiring soon
            totalItems == 1 -> {
                title = "⏰ Expiring Soon"
                message = "$itemName expires in $daysLeft days. Don't forget about it!"
            }
            // Multiple items expired
            daysLeft < 0 -> {
                title = "⚠️ $totalItems Items Expired!"
                message = "You have $totalItems expired items. Check your expiry tracker."
            }
            // Multiple items expiring today
            daysLeft == 0 -> {
                title = "⏰ $totalItems Items Expire Today!"
                message = "$totalItems items expire today. Use them before they go bad!"
            }
            // Multiple items expiring tomorrow
            daysLeft == 1 -> {
                title = "⏰ $totalItems Items Expire Tomorrow"
                message = "$totalItems items expire tomorrow. Check your tracker!"
            }
            // Multiple items expiring soon
            else -> {
                title = "⏰ $totalItems Items Expiring Soon"
                message = "$totalItems items expire in $daysLeft days. Plan ahead!"
            }
        }

        return Pair(title, message)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Older versions don't need runtime permission
        }
    }

    fun areNotificationsEnabled(): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        return notificationManager.areNotificationsEnabled()
    }
}