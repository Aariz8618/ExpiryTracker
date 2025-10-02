package com.aariz.expirytracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var notificationHelper: NotificationHelper

    // UI components
    private lateinit var btnBack: MaterialButton
    private lateinit var switchEnableNotifications: MaterialSwitch
    private lateinit var switchDailySummary: MaterialSwitch
    private lateinit var inputDaysBefore: TextInputEditText
    private lateinit var btnSave: LinearLayout
    private lateinit var btnTestNotification: MaterialButton
    private lateinit var btnCheckPermission: MaterialButton

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_notification_settings)

        prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        notificationHelper = NotificationHelper(this)

        initViews()
        setupClickListeners()
        loadNotificationSettings()
        updatePermissionStatus()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.button_back)
        switchEnableNotifications = findViewById(R.id.switch_enable_notifications)
        switchDailySummary = findViewById(R.id.switch_daily_summary)
        inputDaysBefore = findViewById(R.id.input_days_before)
        btnSave = findViewById(R.id.button_save)
        btnTestNotification = findViewById(R.id.button_test_notification)
        btnCheckPermission = findViewById(R.id.button_check_permission)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveNotificationSettings()
        }

        inputDaysBefore.setOnClickListener {
            showDaysPickerDialog()
        }

        btnTestNotification.setOnClickListener {
            sendTestNotification()
        }

        btnCheckPermission.setOnClickListener {
            checkNotificationPermission()
        }

        switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            switchDailySummary.isEnabled = isChecked
            inputDaysBefore.isEnabled = isChecked

            if (isChecked && !notificationHelper.hasNotificationPermission()) {
                requestNotificationPermission()
            }
        }
    }

    private fun loadNotificationSettings() {
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val dailySummaryEnabled = prefs.getBoolean("daily_summary_enabled", true)
        val daysBefore = prefs.getInt("days_before_expiry", 2)

        switchEnableNotifications.isChecked = notificationsEnabled
        switchDailySummary.isChecked = dailySummaryEnabled
        inputDaysBefore.setText("$daysBefore days before")

        switchDailySummary.isEnabled = notificationsEnabled
        inputDaysBefore.isEnabled = notificationsEnabled
    }

    private fun saveNotificationSettings() {
        val notificationsEnabled = switchEnableNotifications.isChecked
        val dailySummaryEnabled = switchDailySummary.isChecked

        val daysText = inputDaysBefore.text.toString()
        val daysBefore = daysText.filter { it.isDigit() }.toIntOrNull() ?: 2

        prefs.edit().apply {
            putBoolean("notifications_enabled", notificationsEnabled)
            putBoolean("daily_summary_enabled", dailySummaryEnabled)
            putInt("days_before_expiry", daysBefore)
            apply()
        }

        val scheduler = NotificationScheduler(this)
        if (notificationsEnabled) {
            scheduler.scheduleExpiryChecks()
            Toast.makeText(this, "Notification settings saved!", Toast.LENGTH_SHORT).show()
        } else {
            scheduler.cancelExpiryChecks()
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun showDaysPickerDialog() {
        val daysOptions = arrayOf("1 day before", "2 days before", "3 days before", "5 days before", "7 days before")

        MaterialAlertDialogBuilder(this)
            .setTitle("Remind me before")
            .setItems(daysOptions) { dialog, which ->
                inputDaysBefore.setText(daysOptions[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun sendTestNotification() {
        if (!notificationHelper.hasNotificationPermission()) {
            Toast.makeText(this, "Please grant notification permission first", Toast.LENGTH_SHORT).show()
            requestNotificationPermission()
            return
        }

        if (!notificationHelper.areNotificationsEnabled()) {
            Toast.makeText(this, "Notifications are disabled in system settings", Toast.LENGTH_LONG).show()
            return
        }

        notificationHelper.showExpiryNotification(
            itemName = "Test Item",
            daysLeft = 2,
            totalItems = 1
        )

        Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show()
    }

    private fun checkNotificationPermission() {
        val hasPermission = notificationHelper.hasNotificationPermission()
        val areEnabled = notificationHelper.areNotificationsEnabled()

        val message = when {
            !hasPermission -> "Notification permission is NOT granted. Please grant permission to receive notifications."
            !areEnabled -> "Notifications are disabled in system settings. Please enable them in app settings."
            else -> "Notifications are enabled and working!"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Status")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .apply {
                if (!hasPermission) {
                    setNeutralButton("Grant Permission") { _, _ ->
                        requestNotificationPermission()
                    }
                } else if (!areEnabled) {
                    setNeutralButton("Open Settings") { _, _ ->
                        openAppSettings()
                    }
                }
            }
            .show()
    }

    private fun updatePermissionStatus() {
        val hasPermission = notificationHelper.hasNotificationPermission()
        btnCheckPermission.text = if (hasPermission) {
            "Permission Status: Granted"
        } else {
            "Permission Status: Not Granted"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Denied")
            .setMessage("Notification permission is required to receive expiry reminders. You can grant it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}