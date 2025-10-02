package com.aariz.expirytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DebugNotificationsActivity : AppCompatActivity() {

    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvNotificationEnabled: TextView
    private lateinit var tvThrottleStatus: TextView
    private lateinit var tvExpiringItems: TextView
    private lateinit var tvLastNotification: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnRequestPermission: MaterialButton
    private lateinit var btnOpenSettings: MaterialButton
    private lateinit var btnTriggerWorker: MaterialButton
    private lateinit var btnSendTest: MaterialButton
    private lateinit var btnClearThrottle: MaterialButton
    private lateinit var btnBack: MaterialButton

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var firestoreRepository: FirestoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_notifications)

        notificationHelper = NotificationHelper(this)
        firestoreRepository = FirestoreRepository()

        initViews()
        setupListeners()
        refreshStatus()
    }

    private fun initViews() {
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        tvNotificationEnabled = findViewById(R.id.tv_notification_enabled)
        tvThrottleStatus = findViewById(R.id.tv_throttle_status)
        tvExpiringItems = findViewById(R.id.tv_expiring_items)
        tvLastNotification = findViewById(R.id.tv_last_notification)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnRequestPermission = findViewById(R.id.btn_request_permission)
        btnOpenSettings = findViewById(R.id.btn_open_settings)
        btnTriggerWorker = findViewById(R.id.btn_trigger_worker)
        btnSendTest = findViewById(R.id.btn_send_test)
        btnClearThrottle = findViewById(R.id.btn_clear_throttle)
        btnBack = findViewById(R.id.btn_back)
    }

    private fun setupListeners() {
        btnRefresh.setOnClickListener { refreshStatus() }

        btnRequestPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }

        btnTriggerWorker.setOnClickListener {
            triggerWorkerNow()
        }

        btnSendTest.setOnClickListener {
            notificationHelper.showExpiryNotification(
                itemName = "Debug Test Item",
                daysLeft = 2,
                totalItems = 1
            )
            Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show()
        }

        btnClearThrottle.setOnClickListener {
            val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
            prefs.edit().remove("last_notification_timestamp").apply()
            Toast.makeText(this, "Throttle cleared!", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun refreshStatus() {
        // Permission Status
        val hasPermission = notificationHelper.hasNotificationPermission()
        tvPermissionStatus.text = if (hasPermission) "✓ GRANTED" else "✗ NOT GRANTED"
        tvPermissionStatus.setTextColor(
            if (hasPermission) getColor(R.color.green_primary)
            else getColor(R.color.red_500)
        )

        // Notification Enabled Status
        val areEnabled = notificationHelper.areNotificationsEnabled()
        tvNotificationEnabled.text = if (areEnabled) "✓ ENABLED" else "✗ DISABLED"
        tvNotificationEnabled.setTextColor(
            if (areEnabled) getColor(R.color.green_primary)
            else getColor(R.color.red_500)
        )

        // Throttle Status
        val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        val lastNotificationTime = prefs.getLong("last_notification_timestamp", 0)
        val currentTime = System.currentTimeMillis()
        val hoursSince = TimeUnit.MILLISECONDS.toHours(currentTime - lastNotificationTime)

        if (lastNotificationTime == 0L) {
            tvThrottleStatus.text = "Never sent"
            tvThrottleStatus.setTextColor(getColor(R.color.gray_600))
        } else if (hoursSince < 6) {
            tvThrottleStatus.text = "⏱ THROTTLED (${6 - hoursSince}h remaining)"
            tvThrottleStatus.setTextColor(getColor(R.color.orange_500))
        } else {
            tvThrottleStatus.text = "✓ Ready to send"
            tvThrottleStatus.setTextColor(getColor(R.color.green_primary))
        }

        // Last Notification Time
        if (lastNotificationTime == 0L) {
            tvLastNotification.text = "Never"
        } else {
            val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastNotificationTime))
            tvLastNotification.text = date
        }

        // Check expiring items
        checkExpiringItems()
    }

    private fun checkExpiringItems() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            tvExpiringItems.text = "Not logged in"
            return
        }

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()
                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()
                    val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
                    val daysBefore = prefs.getInt("days_before_expiry", 2)

                    val expiringCount = items.count { item ->
                        val daysLeft = calculateDaysLeft(item.expiryDate)
                        daysLeft in 0..daysBefore
                    }

                    val expiredCount = items.count { item ->
                        calculateDaysLeft(item.expiryDate) < 0
                    }

                    tvExpiringItems.text = "$expiringCount expiring, $expiredCount expired"
                    tvExpiringItems.setTextColor(
                        when {
                            expiredCount > 0 -> getColor(R.color.red_500)
                            expiringCount > 0 -> getColor(R.color.orange_500)
                            else -> getColor(R.color.green_primary)
                        }
                    )
                } else {
                    tvExpiringItems.text = "Error fetching items"
                }
            } catch (e: Exception) {
                tvExpiringItems.text = "Error: ${e.message}"
            }
        }
    }

    private fun triggerWorkerNow() {
        val workRequest = OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        Toast.makeText(this, "Worker triggered! Check status in 5-10 seconds", Toast.LENGTH_LONG).show()

        // Refresh after 10 seconds
        findViewById<MaterialButton>(R.id.btn_refresh).postDelayed({
            refreshStatus()
        }, 10000)
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val expiry = sdf.parse(expiryDate) ?: return 0

            val expiryCalendar = java.util.Calendar.getInstance().apply {
                time = expiry
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            TimeUnit.MILLISECONDS.toDays(expiryCalendar.timeInMillis - today.timeInMillis).toInt()
        } catch (e: Exception) {
            0
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }
}