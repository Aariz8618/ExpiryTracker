package com.aariz.expirytracker

import android.app.Application
import com.google.firebase.FirebaseApp

class MyApplication : Application() {

    private lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize notification scheduler
        notificationScheduler = NotificationScheduler(this)

        // Schedule expiry checks
        notificationScheduler.scheduleExpiryChecks()
    }
}