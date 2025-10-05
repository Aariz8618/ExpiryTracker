package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var buttonBack: MaterialButton
    private lateinit var buttonViewProfile: MaterialButton
    private lateinit var rowNotifications: LinearLayout
    private lateinit var rowPermissions: LinearLayout
    private lateinit var rowAbout: LinearLayout
    private lateinit var rowPrivacy: LinearLayout
    private lateinit var rowSupport: LinearLayout
    private lateinit var iconPermissionsChevron: ImageView
    private lateinit var layoutPermissionsExpanded: LinearLayout
    private var isPermissionsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_settings)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        findViewById<View>(R.id.header_section).applyHeaderInsets()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupClickListeners()
        loadUserInfo()
    }

    private fun initViews() {
        textUserName = findViewById(R.id.text_user_name)
        textUserEmail = findViewById(R.id.text_user_email)
        buttonBack = findViewById(R.id.button_back)
        buttonViewProfile = findViewById(R.id.button_view_profile)
        rowNotifications = findViewById(R.id.row_notifications)
        rowPermissions = findViewById(R.id.row_permissions)
        rowAbout = findViewById(R.id.row_about)
        rowPrivacy = findViewById(R.id.row_privacy)
        rowSupport = findViewById(R.id.row_support)
        iconPermissionsChevron = findViewById(R.id.icon_permissions_chevron)
        layoutPermissionsExpanded = findViewById(R.id.layout_permissions_expanded)
    }

    private fun setupClickListeners() {
        buttonBack.setOnClickListener {
            finish()
        }

        buttonViewProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        rowNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        rowPermissions.setOnClickListener {
            togglePermissionsExpanded()
        }

        rowAbout.setOnClickListener {
            showAboutDialog()
        }

        rowPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        rowSupport.setOnClickListener {
            // Navigate to Feedback Activity instead of email
            startActivity(Intent(this, FeedbackActivity::class.java))
        }

    }

    private fun loadUserInfo() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            textUserName.text = currentUser.displayName ?: "User"
            textUserEmail.text = currentUser.email ?: "No email"
        } else {
            textUserName.text = "Guest"
            textUserEmail.text = "Not logged in"
        }
    }

    private fun togglePermissionsExpanded() {
        isPermissionsExpanded = !isPermissionsExpanded

        if (isPermissionsExpanded) {
            layoutPermissionsExpanded.visibility = View.VISIBLE
            iconPermissionsChevron.rotation = 180f
        } else {
            layoutPermissionsExpanded.visibility = View.GONE
            iconPermissionsChevron.rotation = 0f
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About Expiry Tracker")
            .setMessage(
                "Version: 1.0.2\n" +
                        "Developer: TechFlow Solutions\n\n" +
                        "Expiry Tracker helps you reduce food waste by tracking expiration dates " +
                        "and sending timely reminders.\n\n" +
                        "© 2025 TechFlow Solutions"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadUserInfo()
    }
}