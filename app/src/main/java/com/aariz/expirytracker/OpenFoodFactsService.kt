package com.aariz.expirytracker

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProductInfo(@Path("barcode") barcode: String): Response<OpenFoodFactsResponse>
}

class OpenFoodFactsService {
    private val api: OpenFoodFactsApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(OpenFoodFactsApi::class.java)
    }

    suspend fun getProductInfo(barcode: String): Result<CachedProductInfo?> {
        return try {
            val response = api.getProductInfo(barcode)
            if (response.isSuccessful) {
                val openFoodFactsResponse = response.body()
                if (openFoodFactsResponse?.status == 1 && openFoodFactsResponse.product != null) {
                    val product = openFoodFactsResponse.product
                    val cachedProduct = CachedProductInfo(
                        barcode = barcode,
                        productName = product.productName ?: "",
                        brands = product.brands ?: "",
                        categories = product.categories ?: "",
                        imageUrl = product.imageFrontSmallUrl ?: product.imageUrl ?: "",
                        suggestedCategory = mapCategoriesToAppCategory(product.categories),
                        cachedAt = java.util.Date(),
                        source = "openfoodfacts"
                    )
                    Result.success(cachedProduct)
                } else {
                    Result.success(null) // Product not found
                }
            } else {
                Result.failure(Exception("API request failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapCategoriesToAppCategory(categories: String?): String {
        if (categories.isNullOrEmpty()) return "Other"

        val categoryLower = categories.lowercase()
        return when {
            categoryLower.contains("dairy") || categoryLower.contains("milk") ||
                    categoryLower.contains("cheese") || categoryLower.contains("yogurt") -> "Dairy"

            categoryLower.contains("meat") || categoryLower.contains("beef") ||
                    categoryLower.contains("chicken") || categoryLower.contains("pork") ||
                    categoryLower.contains("fish") || categoryLower.contains("seafood") -> "Meat"

            categoryLower.contains("vegetable") || categoryLower.contains("tomato") ||
                    categoryLower.contains("onion") || categoryLower.contains("carrot") -> "Vegetables"

            categoryLower.contains("fruit") || categoryLower.contains("apple") ||
                    categoryLower.contains("banana") || categoryLower.contains("orange") -> "Fruits"

            categoryLower.contains("bread") || categoryLower.contains("bakery") ||
                    categoryLower.contains("cake") || categoryLower.contains("pastry") -> "Bakery"

            categoryLower.contains("frozen") -> "Frozen"

            categoryLower.contains("beverage") || categoryLower.contains("drink") ||
                    categoryLower.contains("snack") || categoryLower.contains("cereal") ||
                    categoryLower.contains("pasta") || categoryLower.contains("rice") -> "Pantry"

            else -> "Other"
        }
    }
}