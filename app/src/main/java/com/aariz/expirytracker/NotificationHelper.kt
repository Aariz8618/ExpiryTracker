package com.aariz.expirytracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val EXPIRY_CHANNEL_ID = "expiry_notifications"
        const val EXPIRY_CHANNEL_NAME = "Expiry Reminders"
        const val EXPIRY_CHANNEL_DESCRIPTION = "Notifications for items about to expire"
        const val EXPIRY_NOTIFICATION_ID = 1001
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
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showExpiryNotification(itemName: String, daysLeft: Int, totalItems: Int = 1) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            totalItems == 1 && daysLeft == 0 -> "⚠️ $itemName expired today!"
            totalItems == 1 && daysLeft == 1 -> "⏰ $itemName expires tomorrow!"
            totalItems == 1 -> "⏰ $itemName expires in $daysLeft days"
            daysLeft == 0 -> "⚠️ $totalItems items expired today!"
            daysLeft == 1 -> "⏰ $totalItems items expire tomorrow!"
            else -> "⏰ $totalItems items expire in $daysLeft days"
        }

        val message = if (totalItems == 1) {
            "Don't forget to use it before it goes bad!"
        } else {
            "Check your expiry tracker for details"
        }

        val notification = NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell) // You'll need to add this icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(EXPIRY_NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            android.util.Log.e("NotificationHelper", "Permission denied for notifications", e)
        }
    }
}