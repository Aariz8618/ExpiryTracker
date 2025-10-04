package com.aariz.expirytracker.data.api

import com.aariz.expirytracker.data.model.RecipeResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit API interface for Recipe API
 */
interface RecipeApiService {

    @GET("recipe")
    suspend fun searchRecipes(
        @Query("query") query: String,
        @Header("X-Api-Key") apiKey: String
    ): Response<List<RecipeResponse>>

    companion object {
        const val BASE_URL = "https://api.api-ninjas.com/v1/"
        const val API_KEY = "DFjO/ZiJRuFrtUyJUpgA/w==DFB0MmIijzEgzsrv"
    }
}