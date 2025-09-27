package com.aariz.expirytracker

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class GroceryItem(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val expiryDate: String = "",
    val purchaseDate: String = "",
    val quantity: Int = 1,
    val status: String = "fresh",
    val daysLeft: Int = 0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        category = "",
        expiryDate = "",
        purchaseDate = "",
        quantity = 1,
        status = "fresh",
        daysLeft = 0,
        createdAt = Date(),
        updatedAt = Date()
    )
}

// User data class for users collection
data class User(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val email: String = ""
) {
    constructor() : this("", "", "")
}