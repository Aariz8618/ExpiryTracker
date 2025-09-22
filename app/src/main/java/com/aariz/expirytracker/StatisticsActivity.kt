package com.aariz.expirytracker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StatisticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_statistics)

        findViewById<Button>(R.id.button_back).setOnClickListener { finish() }
    }
}
