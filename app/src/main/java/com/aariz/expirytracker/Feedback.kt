package com.aariz.expirytracker

data class Feedback(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val category: String = "",
    val message: String = "",
    val screenshotUrl: String? = null,
    val timestamp: Long = 0,
    val dateFormatted: String = "",
    val status: String = "pending" // pending, reviewed, resolved
)