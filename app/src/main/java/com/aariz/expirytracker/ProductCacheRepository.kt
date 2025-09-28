package com.aariz.expirytracker

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.TimeUnit

class ProductCacheRepository(private val context: Context? = null) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val openFoodFactsService = OpenFoodFactsService()

    // Cache expiry time (7 days)
    private val cacheExpiryMs = TimeUnit.DAYS.toMillis(7)
    // Soft cache expiry (3 days) - after this, we'll try to refresh but still use cache if API fails
    private val softCacheExpiryMs = TimeUnit.DAYS.toMillis(3)

    // Constructor for cases where context is not available
    constructor() : this(null)

    /**
     * Enhanced get product info with network awareness and better offline support:
     * 1. Check local Firestore cache first
     * 2. If cache is fresh (< 3 days), return it
     * 3. If cache is stale but network unavailable, return cached data with warning
     * 4. If network available, try to fetch from API and update cache
     * 5. If API fails, fall back to cached data (even if expired)
     */
    suspend fun getProductInfo(barcode: String): Result<ProductLookupResult> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("ProductCacheRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            val isNetworkAvailable = context?.let { NetworkUtils.isNetworkAvailable(it) } ?: true

            // First, try to get from cache
            val cachedResult = getCachedProductInfo(barcode)
            val cachedProduct = cachedResult.getOrNull()

            // If we have fresh cache (< 3 days), return it immediately
            if (cachedProduct != null && !isSoftCacheExpired(cachedProduct)) {
                Log.d("ProductCacheRepository", "Found fresh cached product for barcode: $barcode")
                return Result.success(
                    ProductLookupResult(
                        productInfo = cachedProduct,
                        source = DataSource.CACHE_FRESH,
                        isOfflineData = false
                    )
                )
            }

            // If network is not available, return cached data (even if stale)
            if (!isNetworkAvailable) {
                return if (cachedProduct != null) {
                    Log.d("ProductCacheRepository", "Network unavailable, returning cached product for barcode: $barcode")
                    Result.success(
                        ProductLookupResult(
                            productInfo = cachedProduct,
                            source = DataSource.CACHE_OFFLINE,
                            isOfflineData = true
                        )
                    )
                } else {
                    Log.d("ProductCacheRepository", "Network unavailable and no cache for barcode: $barcode")
                    Result.success(
                        ProductLookupResult(
                            productInfo = null,
                            source = DataSource.NETWORK_FAILED,
                            isOfflineData = true
                        )
                    )
                }
            }

            // Network is available - try to fetch from API
            Log.d("ProductCacheRepository", "Network available, fetching from API for barcode: $barcode")
            val apiResult = openFoodFactsService.getProductInfo(barcode)

            if (apiResult.isSuccess) {
                val productInfo = apiResult.getOrNull()
                if (productInfo != null) {
                    // Successfully fetched from API - cache it
                    cacheProductInfo(productInfo)
                    Log.d("ProductCacheRepository", "Successfully fetched and cached product: ${productInfo.productName}")
                    Result.success(
                        ProductLookupResult(
                            productInfo = productInfo,
                            source = DataSource.API,
                            isOfflineData = false
                        )
                    )
                } else {
                    // Product not found in API
                    Log.d("ProductCacheRepository", "Product not found in API for barcode: $barcode")
                    Result.success(
                        ProductLookupResult(
                            productInfo = null,
                            source = DataSource.API,
                            isOfflineData = false
                        )
                    )
                }
            } else {
                // API call failed - fall back to cached data if available
                if (cachedProduct != null) {
                    Log.w("ProductCacheRepository", "API failed, returning cached data for barcode: $barcode")
                    Result.success(
                        ProductLookupResult(
                            productInfo = cachedProduct,
                            source = DataSource.CACHE_FALLBACK,
                            isOfflineData = true
                        )
                    )
                } else {
                    Log.e("ProductCacheRepository", "API failed and no cache available for barcode: $barcode")
                    Result.success(
                        ProductLookupResult(
                            productInfo = null,
                            source = DataSource.NETWORK_FAILED,
                            isOfflineData = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error getting product info: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Batch lookup for multiple barcodes - useful for inventory scanning
     */
    suspend fun getProductInfoBatch(barcodes: List<String>): Result<Map<String, ProductLookupResult>> {
        return try {
            val results = mutableMapOf<String, ProductLookupResult>()

            for (barcode in barcodes) {
                val result = getProductInfo(barcode)
                if (result.isSuccess) {
                    results[barcode] = result.getOrThrow()
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error in batch lookup: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun getCachedProductInfo(barcode: String): Result<CachedProductInfo?> {
        return try {
            val currentUser = auth.currentUser!!
            val document = firestore.collection("users")
                .document(currentUser.uid)
                .collection("cached_products")
                .document(barcode)
                .get()
                .await()

            val cachedProduct = document.toObject(CachedProductInfo::class.java)
            Result.success(cachedProduct)
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error getting cached product: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun cacheProductInfo(productInfo: CachedProductInfo) {
        try {
            val currentUser = auth.currentUser!!
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("cached_products")
                .document(productInfo.barcode)
                .set(productInfo)
                .await()

            Log.d("ProductCacheRepository", "Successfully cached product: ${productInfo.productName}")
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error caching product: ${e.message}", e)
            // Don't throw - caching failure shouldn't break the flow
        }
    }

    private fun isCacheExpired(cachedProduct: CachedProductInfo): Boolean {
        val now = Date()
        val cacheAge = now.time - cachedProduct.cachedAt.time
        return cacheAge > cacheExpiryMs
    }

    private fun isSoftCacheExpired(cachedProduct: CachedProductInfo): Boolean {
        val now = Date()
        val cacheAge = now.time - cachedProduct.cachedAt.time
        return cacheAge > softCacheExpiryMs
    }

    /**
     * Get cache statistics for debugging/admin purposes
     */
    suspend fun getCacheStatistics(): Result<CacheStatistics> {
        return try {
            val currentUser = auth.currentUser!!
            val snapshot = firestore.collection("users")
                .document(currentUser.uid)
                .collection("cached_products")
                .get()
                .await()

            var totalItems = 0
            var freshItems = 0
            var staleItems = 0
            var expiredItems = 0
            val now = Date()

            for (document in snapshot.documents) {
                val cachedProduct = document.toObject(CachedProductInfo::class.java)
                if (cachedProduct != null) {
                    totalItems++
                    val cacheAge = now.time - cachedProduct.cachedAt.time
                    when {
                        cacheAge <= softCacheExpiryMs -> freshItems++
                        cacheAge <= cacheExpiryMs -> staleItems++
                        else -> expiredItems++
                    }
                }
            }

            val stats = CacheStatistics(
                totalCachedItems = totalItems,
                freshItems = freshItems,
                staleItems = staleItems,
                expiredItems = expiredItems,
                cacheHitRate = 0.0 // Would need to track hits/misses separately
            )

            Result.success(stats)
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error getting cache statistics: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear expired cache entries
     */
    suspend fun clearExpiredCache(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val snapshot = firestore.collection("users")
                .document(currentUser.uid)
                .collection("cached_products")
                .get()
                .await()

            val batch = firestore.batch()
            var deletedCount = 0

            for (document in snapshot.documents) {
                val cachedProduct = document.toObject(CachedProductInfo::class.java)
                if (cachedProduct != null && isCacheExpired(cachedProduct)) {
                    batch.delete(document.reference)
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                batch.commit().await()
                Log.d("ProductCacheRepository", "Cleared $deletedCount expired cache entries")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error clearing expired cache: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Force refresh a specific product from the API
     */
    suspend fun forceRefreshProduct(barcode: String): Result<ProductLookupResult> {
        return try {
            val apiResult = openFoodFactsService.getProductInfo(barcode)

            if (apiResult.isSuccess) {
                val productInfo = apiResult.getOrNull()
                if (productInfo != null) {
                    cacheProductInfo(productInfo)
                    Log.d("ProductCacheRepository", "Force refreshed product: ${productInfo.productName}")
                    Result.success(
                        ProductLookupResult(
                            productInfo = productInfo,
                            source = DataSource.API_FORCE_REFRESH,
                            isOfflineData = false
                        )
                    )
                } else {
                    Result.success(
                        ProductLookupResult(
                            productInfo = null,
                            source = DataSource.API_FORCE_REFRESH,
                            isOfflineData = false
                        )
                    )
                }
            } else {
                Result.failure(apiResult.exceptionOrNull() ?: Exception("API call failed"))
            }
        } catch (e: Exception) {
            Log.e("ProductCacheRepository", "Error force refreshing product: ${e.message}", e)
            Result.failure(e)
        }
    }
}

// Data classes for enhanced result information
data class ProductLookupResult(
    val productInfo: CachedProductInfo?,
    val source: DataSource,
    val isOfflineData: Boolean
)

enum class DataSource {
    CACHE_FRESH,        // Data from cache, less than 3 days old
    CACHE_OFFLINE,      // Data from cache, returned because network unavailable
    CACHE_FALLBACK,     // Data from cache, returned because API failed
    API,                // Fresh data from API
    API_FORCE_REFRESH,  // Fresh data from API via force refresh
    NETWORK_FAILED      // No data available, network failed and no cache
}

data class CacheStatistics(
    val totalCachedItems: Int,
    val freshItems: Int,
    val staleItems: Int,
    val expiredItems: Int,
    val cacheHitRate: Double
)