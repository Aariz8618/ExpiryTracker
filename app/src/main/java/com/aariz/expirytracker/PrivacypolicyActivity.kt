package com.aariz.expirytracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var buttonBack: MaterialButton
    private lateinit var contactCard: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_privacy_policy)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(R.id.header_section).applyHeaderInsets()

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        buttonBack = findViewById(R.id.button_back)
        contactCard = findViewById(R.id.contact_card)
    }

    private fun setupClickListeners() {
        buttonBack.setOnClickListener {
            finish()
        }

        contactCard.setOnClickListener {
            openEmailClient()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun openEmailClient() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("freshtrack@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Privacy Policy Inquiry - Grocery Tracker")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if no email app is available
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("freshtrack@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Privacy Policy Inquiry - Grocery Tracker")
            }
            try {
                startActivity(Intent.createChooser(sendIntent, "Send Email"))
            } catch (ex: Exception) {
                // Handle case where no email app exists
            }
        }
    }
}