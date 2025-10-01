package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aariz.expirytracker.data.model.Recipe
import com.aariz.expirytracker.data.repository.RecipeRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class RecipesActivity : AppCompatActivity() {
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecipesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout

    private val recipeRepository = RecipeRepository()
    private val firestoreRepository = FirestoreRepository()
    private val recipes = mutableListOf<Recipe>()
    private var userIngredients = listOf<String>()
    private var isSearchMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_recipes)

        initViews()
        setupBackButton()
        setupRecyclerView()
        setupSearchView()

        // Load all recipes with priority sorting
        loadAllRecipes()
    }

    private fun initViews() {
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.recycler_recipes)
        progressBar = findViewById(R.id.progress_bar)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupBackButton() {
        findViewById<MaterialButton>(R.id.button_back).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecipesAdapter(recipes) { recipe ->
            openRecipeDetail(recipe)
        }
        recyclerView.adapter = adapter
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    isSearchMode = true
                    searchRecipes(query)
                } else {
                    isSearchMode = false
                    loadAllRecipes()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // If search is cleared, reload all recipes
                if (newText.isNullOrBlank() && isSearchMode) {
                    isSearchMode = false
                    loadAllRecipes()
                }
                return true
            }
        })
    }

    private fun loadAllRecipes() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // First, get user's grocery items
                val itemsResult = firestoreRepository.getUserGroceryItems()

                if (itemsResult.isSuccess) {
                    val groceryItems = itemsResult.getOrNull() ?: emptyList()

                    // Extract unique ingredients from user's items (non-expired only)
                    userIngredients = groceryItems
                        .filter { it.status != "expired" && it.status != "used" }
                        .map { it.name.lowercase() }
                        .distinct()

                    // Get suggested recipes based on user's ingredients
                    val suggestedResult = recipeRepository.getSuggestedRecipes(userIngredients)

                    // Get popular recipes
                    val popularResult = recipeRepository.getPopularRecipes()

                    // Combine and sort recipes
                    val suggestedRecipes = suggestedResult.getOrNull() ?: emptyList()
                    val popularRecipes = popularResult.getOrNull() ?: emptyList()

                    // Create a combined list with priority
                    val allRecipes = mutableListOf<Recipe>()

                    // Add suggested recipes first (these match user's ingredients)
                    allRecipes.addAll(suggestedRecipes)

                    // Add popular recipes that aren't already in suggested
                    val suggestedTitles = suggestedRecipes.map { it.title }.toSet()
                    val uniquePopular = popularRecipes.filter { it.title !in suggestedTitles }
                    allRecipes.addAll(uniquePopular)

                    // Sort by relevance score
                    val sortedRecipes = sortRecipesByRelevance(allRecipes)

                    recipes.clear()
                    recipes.addAll(sortedRecipes)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()

                    showLoading(false)
                } else {
                    showLoading(false)
                    Toast.makeText(
                        this@RecipesActivity,
                        "Failed to load your ingredients",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Fallback to popular recipes only
                    loadPopularRecipesOnly()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@RecipesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyState()
            }
        }
    }

    private fun loadPopularRecipesOnly() {
        lifecycleScope.launch {
            try {
                val result = recipeRepository.getPopularRecipes()
                handleRecipeResult(result)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@RecipesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyState()
            }
        }
    }

    private fun sortRecipesByRelevance(recipeList: List<Recipe>): List<Recipe> {
        return recipeList.sortedByDescending { recipe ->
            // Calculate relevance score
            var score = 0

            // Check how many user ingredients match recipe ingredients
            recipe.ingredients.forEach { ingredient ->
                val ingredientLower = ingredient.lowercase()
                userIngredients.forEach { userIngredient ->
                    if (ingredientLower.contains(userIngredient) ||
                        userIngredient.contains(ingredientLower)) {
                        score += 10 // High score for ingredient match
                    }
                }
            }

            // Bonus for recipes with fewer total ingredients (easier to make)
            if (recipe.ingredients.size <= 5) {
                score += 5
            }

            score
        }
    }

    private fun searchRecipes(query: String) {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = recipeRepository.searchRecipes(query)
                handleRecipeResult(result)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@RecipesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyState()
            }
        }
    }

    private fun handleRecipeResult(result: Result<List<Recipe>>) {
        showLoading(false)

        if (result.isSuccess) {
            val fetchedRecipes = result.getOrNull() ?: emptyList()

            // Sort search results by relevance too if we have user ingredients
            val sortedRecipes = if (userIngredients.isNotEmpty() && isSearchMode) {
                sortRecipesByRelevance(fetchedRecipes)
            } else {
                fetchedRecipes
            }

            recipes.clear()
            recipes.addAll(sortedRecipes)
            adapter.notifyDataSetChanged()
            updateEmptyState()

            // Log success
            if (fetchedRecipes.isEmpty()) {
                Toast.makeText(
                    this,
                    "No recipes found. Try a different search.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            val error = result.exceptionOrNull()
            Toast.makeText(
                this,
                "Failed to load recipes: ${error?.message}",
                Toast.LENGTH_LONG
            ).show()
            updateEmptyState()
        }
    }

    private fun openRecipeDetail(recipe: Recipe) {
        val times = extractTimesFromInstructions(recipe.instructions)

        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("title", recipe.title)
            putExtra("servings", recipe.servings)
            putExtra("prepTime", "Varies")
            putExtra("difficulty", "Medium")
            putExtra("ingredientsPreview", recipe.getIngredientsPreview())
            putExtra("instructionsPreview", recipe.getInstructionsPreview())
            putStringArrayListExtra("ingredients", ArrayList(recipe.ingredients))
            putStringArrayListExtra("instructions", ArrayList(recipe.instructions))
            putStringArrayListExtra("times", times)
            putExtra("notes", "Recipe from API Ninjas")
        }
        startActivity(intent)
    }

    private fun extractTimesFromInstructions(instructions: List<String>): ArrayList<String> {
        val times = ArrayList<String>()
        val timePattern = Regex("(\\d+)\\s*(min|minute|minutes|hour|hours|seconds?)", RegexOption.IGNORE_CASE)

        instructions.forEach { instruction ->
            val match = timePattern.find(instruction)
            if (match != null) {
                val value = match.groupValues[1]
                val unit = match.groupValues[2].lowercase()

                // Normalize to minutes
                val minutes = when {
                    unit.startsWith("hour") -> value.toInt() * 60
                    unit.startsWith("sec") -> 1 // minimum 1 min
                    else -> value.toInt()
                }
                times.add("$minutes min")
            } else {
                times.add("5 min") // Default fallback
            }
        }

        return times
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState() {
        if (recipes.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }
}