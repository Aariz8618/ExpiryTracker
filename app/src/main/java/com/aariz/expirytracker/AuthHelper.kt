package com.aariz.expirytracker

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

object AuthHelper {
    private const val PREF_NAME = "app_prefs"
    private const val KEY_USER_LOGGED_IN_BEFORE = "user_logged_in_before"
    private const val KEY_IS_FIRST_RUN = "is_first_run"

    fun markUserLoggedIn(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USER_LOGGED_IN_BEFORE, true).apply()
    }

    fun clearUserLoggedInFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USER_LOGGED_IN_BEFORE, false).apply()
    }

    fun hasUserLoggedInBefore(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USER_LOGGED_IN_BEFORE, false)
    }

    fun logoutUser(context: Context) {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear logged-in flag
        clearUserLoggedInFlag(context)

        // Navigate to login screen
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun resetForTesting(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_FIRST_RUN, true)
            .putBoolean(KEY_USER_LOGGED_IN_BEFORE, false)
            .apply()
    }
}