package com.aariz.expirytracker

import com.google.gson.annotations.SerializedName
import java.util.*

// OpenFoodFacts API Response Models
data class OpenFoodFactsResponse(
    @SerializedName("status")
    val status: Int,
    @SerializedName("product")
    val product: OpenFoodFactsProduct?
)

data class OpenFoodFactsProduct(
    @SerializedName("product_name")
    val productName: String?,
    @SerializedName("brands")
    val brands: String?,
    @SerializedName("categories")
    val categories: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("image_front_small_url")
    val imageFrontSmallUrl: String?,
    @SerializedName("nutriments")
    val nutriments: Map<String, Any>?,
    @SerializedName("ingredients_text")
    val ingredientsText: String?,
    @SerializedName("allergens")
    val allergens: String?
)

// Cached Product Info (stored in Firestore)
data class CachedProductInfo(
    val barcode: String = "",
    val productName: String = "",
    val brands: String = "",
    val categories: String = "",
    val imageUrl: String = "",
    val suggestedCategory: String = "", // Mapped from OpenFoodFacts categories to our app categories
    val cachedAt: Date = Date(),
    val source: String = "openfoodfacts" // Could be expanded to support other APIs
)

// Barcode scan result
data class BarcodeScanResult(
    val barcode: String,
    val format: String // EAN_13, UPC_A, etc.
)