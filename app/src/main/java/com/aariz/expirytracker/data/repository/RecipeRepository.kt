package com.aariz.expirytracker.data.repository

import com.aariz.expirytracker.data.api.RecipeApiService
import com.aariz.expirytracker.data.api.RetrofitClient
import com.aariz.expirytracker.data.model.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for Recipe API operations
 * Handles data fetching and transformation
 */
class RecipeRepository {

    private val apiService: RecipeApiService = RetrofitClient.getRecipeApiService()
    private val apiKey = RecipeApiService.API_KEY

    /**
     * Search recipes by query
     */
    suspend fun searchRecipes(query: String): Result<List<Recipe>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchRecipes(query, apiKey)

            if (response.isSuccessful) {
                val recipeResponses = response.body() ?: emptyList()
                val recipes = recipeResponses.map { Recipe.fromResponse(it) }
                Result.success(recipes)
            } else {
                Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}", e))
        }
    }

    /**
     * Get recipes by ingredient
     */
    suspend fun getRecipesByIngredient(ingredient: String): Result<List<Recipe>> {
        return searchRecipes(ingredient)
    }

    /**
     * Get suggested recipes based on multiple user ingredients
     * Searches for recipes using user's ingredients and returns unique results
     */
    suspend fun getSuggestedRecipes(userIngredients: List<String>): Result<List<Recipe>> = withContext(Dispatchers.IO) {
        try {
            if (userIngredients.isEmpty()) {
                // Return default recipes if no ingredients
                return@withContext searchRecipes("popular")
            }

            val allRecipes = mutableListOf<Recipe>()
            val uniqueTitles = mutableSetOf<String>()

            // Search for recipes using top 3 ingredients to avoid too many API calls
            val topIngredients = userIngredients.take(3)

            for (ingredient in topIngredients) {
                val result = searchRecipes(ingredient)

                if (result.isSuccess) {
                    val recipes = result.getOrNull() ?: emptyList()

                    // Add only unique recipes
                    recipes.forEach { recipe ->
                        if (uniqueTitles.add(recipe.title.lowercase())) {
                            allRecipes.add(recipe)
                        }
                    }
                }

                // Limit total recipes
                if (allRecipes.size >= 15) break
            }

            if (allRecipes.isEmpty()) {
                // Fallback to general search if no results
                return@withContext searchRecipes("dinner")
            }

            Result.success(allRecipes)
        } catch (e: Exception) {
            Result.failure(Exception("Error fetching suggested recipes: ${e.message}", e))
        }
    }

    /**
     * Get popular recipes
     */
    suspend fun getPopularRecipes(): Result<List<Recipe>> {
        return searchRecipes("popular")
    }

    /**
     * Get recipes by category/cuisine
     */
    suspend fun getRecipesByCategory(category: String): Result<List<Recipe>> {
        return searchRecipes(category)
    }
}