package com.aariz.expirytracker

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val splashDelay = 3500L // 3.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_splash)

        auth = FirebaseAuth.getInstance()

        // Start animations
        startSplashAnimations()

        // Check if this is a fresh install by looking at shared preferences
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (isFirstRun) {
            auth.signOut()
            prefs.edit().putBoolean("is_first_run", false).apply()
            Log.d("MainActivity", "First run detected - clearing auth state")
        } else if (!hasUserLoggedInBefore) {
            auth.signOut()
            Log.d("MainActivity", "No previous successful login - clearing auth state")
        }

        // Delay for splash screen, then check authentication
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthenticationAndNavigate()
        }, splashDelay)
    }

    private fun startSplashAnimations() {
        val logoCard = findViewById<MaterialCardView>(R.id.logo_card)
        val appName = findViewById<TextView>(R.id.text_app_name)
        val subtitle = findViewById<TextView>(R.id.text_subtitle)
        val lottieClock = findViewById<LottieAnimationView>(R.id.lottie_clock)
        val loadingText = findViewById<TextView>(R.id.text_loading)
        val versionText = findViewById<TextView>(R.id.text_version)
        val circle1 = findViewById<View>(R.id.circle_1)
        val circle2 = findViewById<View>(R.id.circle_2)

        // Animate decorative circles
        ObjectAnimator.ofFloat(circle1, View.ROTATION, 0f, 360f).apply {
            duration = 20000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(circle2, View.ROTATION, 0f, -360f).apply {
            duration = 25000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }

        // Logo entrance with elegant scale and fade (NO ROTATION)
        logoCard.alpha = 0f
        logoCard.scaleX = 0.5f
        logoCard.scaleY = 0.5f

        val logoFade = ObjectAnimator.ofFloat(logoCard, View.ALPHA, 0f, 1f)
        val logoScaleX = ObjectAnimator.ofFloat(logoCard, View.SCALE_X, 0.5f, 1.1f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logoCard, View.SCALE_Y, 0.5f, 1.1f, 1f)

        AnimatorSet().apply {
            playTogether(logoFade, logoScaleX, logoScaleY)
            duration = 900
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // App name fade up
        appName.translationY = 30f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(appName, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(appName, View.TRANSLATION_Y, 30f, 0f)
            )
            duration = 700
            startDelay = 500
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Subtitle fade up
        subtitle.translationY = 30f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(subtitle, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(subtitle, View.TRANSLATION_Y, 30f, 0f)
            )
            duration = 700
            startDelay = 700
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Lottie clock animation with scale and fade
        lottieClock.scaleX = 0.5f
        lottieClock.scaleY = 0.5f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(lottieClock, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(lottieClock, View.SCALE_X, 0.5f, 1f),
                ObjectAnimator.ofFloat(lottieClock, View.SCALE_Y, 0.5f, 1f)
            )
            duration = 600
            startDelay = 1200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Start Lottie animation after fade in
        Handler(Looper.getMainLooper()).postDelayed({
            lottieClock.playAnimation()
        }, 1200)

        // Loading text fade with subtle pulse
        ObjectAnimator.ofFloat(loadingText, View.ALPHA, 0f, 1f).apply {
            duration = 500
            startDelay = 1400
            start()
        }

        // Subtle pulse animation for loading text
        Handler(Looper.getMainLooper()).postDelayed({
            ObjectAnimator.ofFloat(loadingText, View.ALPHA, 1f, 0.6f, 1f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 1500)

        // Version text fade in
        ObjectAnimator.ofFloat(versionText, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 1600
            start()
        }
    }

    private fun checkAuthenticationAndNavigate() {
        val currentUser = auth.currentUser
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (currentUser != null && currentUser.isEmailVerified && hasUserLoggedInBefore) {
            Log.d("MainActivity", "User authenticated: ${currentUser.email}")
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            if (currentUser != null && !hasUserLoggedInBefore) {
                auth.signOut()
            }
            Log.d("MainActivity", "No authenticated user found or first-time user - going to login")
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }

    fun markUserLoggedIn() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", true).apply()
    }

    private fun resetFirstRunFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_first_run", true)
            .putBoolean("user_logged_in_before", false)
            .apply()
    }
}