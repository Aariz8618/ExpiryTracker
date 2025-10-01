package com.aariz.expirytracker.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for API calls
 */
object RetrofitClient {

    private var retrofit: Retrofit? = null

    /**
     * Get Retrofit instance
     */
    fun getClient(): Retrofit {
        if (retrofit == null) {
            // Create logging interceptor for debugging
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Create OkHttp client with interceptors
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // Build Retrofit instance
            retrofit = Retrofit.Builder()
                .baseUrl(RecipeApiService.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    /**
     * Get Recipe API service
     */
    fun getRecipeApiService(): RecipeApiService {
        return getClient().create(RecipeApiService::class.java)
    }
}