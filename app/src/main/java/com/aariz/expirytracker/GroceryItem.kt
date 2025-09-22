package com.aariz.expirytracker

data class GroceryItem(
    val id: Int,
    val name: String,
    val category: String,
    val expiryDate: String,
    val purchaseDate: String,
    val quantity: Int,
    val status: String,
    val daysLeft: Int
)
