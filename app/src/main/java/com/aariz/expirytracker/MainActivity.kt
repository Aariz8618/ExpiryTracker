package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val splashDelay = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_splash)

        auth = FirebaseAuth.getInstance()

        // Check if this is a fresh install by looking at shared preferences
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (isFirstRun) {
            // First run after install - always sign out and require fresh login
            auth.signOut()
            prefs.edit().putBoolean("is_first_run", false).apply()
            Log.d("MainActivity", "First run detected - clearing auth state")
        } else if (!hasUserLoggedInBefore) {
            // User hasn't successfully logged in before, so sign out any cached auth
            auth.signOut()
            Log.d("MainActivity", "No previous successful login - clearing auth state")
        }

        // Delay for splash screen, then check authentication
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthenticationAndNavigate()
        }, splashDelay)
    }

    private fun checkAuthenticationAndNavigate() {
        val currentUser = auth.currentUser
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (currentUser != null && currentUser.isEmailVerified && hasUserLoggedInBefore) {
            // User is logged in, verified, and has logged in successfully before
            Log.d("MainActivity", "User authenticated: ${currentUser.email}")
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            // User not logged in, not verified, or hasn't logged in successfully before
            if (currentUser != null && !hasUserLoggedInBefore) {
                // Clear any cached auth if user hasn't successfully logged in through our app before
                auth.signOut()
            }
            Log.d("MainActivity", "No authenticated user found or first-time user - going to login")
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }

    // Method to mark that user has successfully logged in (call this from LoginActivity)
    fun markUserLoggedIn() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", true).apply()
    }

    // Optional: Add method to reset first run flag (useful for testing)
    private fun resetFirstRunFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_first_run", true)
            .putBoolean("user_logged_in_before", false)
            .apply()
    }
}