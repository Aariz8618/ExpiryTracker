package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val recipeRepository = RecipeRepository()
    private val firestoreRepository = FirestoreRepository()
    private val recipes = mutableListOf<Recipe>()
    private var userIngredients = listOf<String>()
    private var isSearchMode = false
    private var currentSearchQuery = ""

    // Cache for suggested recipes based on user ingredients
    private val suggestedRecipesCache = mutableListOf<Recipe>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_recipes)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(R.id.header_section).applyHeaderInsets()

        initViews()
        setupBackButton()
        setupRecyclerView()
        setupSearchView()
        setupSwipeRefresh()

        // Load all recipes with priority sorting
        loadAllRecipes()
    }

    private fun initViews() {
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.recycler_recipes)
        progressBar = findViewById(R.id.progress_bar)
        emptyState = findViewById(R.id.empty_state)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
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
                    currentSearchQuery = query
                    searchRecipes(query)
                } else {
                    isSearchMode = false
                    currentSearchQuery = ""
                    loadAllRecipes()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // If search is cleared, reload all recipes
                if (newText.isNullOrBlank() && isSearchMode) {
                    isSearchMode = false
                    currentSearchQuery = ""
                    loadAllRecipes()
                }
                return true
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (isSearchMode) {
                // Re-run the search
                if (currentSearchQuery.isNotBlank()) {
                    searchRecipes(currentSearchQuery)
                } else {
                    loadAllRecipes()
                }
            } else {
                // Reload all recipes
                loadAllRecipes()
            }
        }

        // Set color scheme for refresh indicator
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun loadAllRecipes(forceRefresh: Boolean = false) {
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

                    // Get suggested recipes based on user's ingredients (cache them)
                    if (suggestedRecipesCache.isEmpty() || forceRefresh) {
                        val suggestedResult = recipeRepository.getSuggestedRecipes(userIngredients)
                        val suggestedRecipes = suggestedResult.getOrNull() ?: emptyList()

                        suggestedRecipesCache.clear()
                        suggestedRecipesCache.addAll(suggestedRecipes)
                    }

                    // Get popular recipes with varied queries for diversity
                    val popularQueries = getRandomPopularQueries()
                    val allPopularRecipes = mutableListOf<Recipe>()

                    for (query in popularQueries) {
                        val result = recipeRepository.searchRecipes(query)
                        if (result.isSuccess) {
                            val newRecipes = result.getOrNull() ?: emptyList()
                            allPopularRecipes.addAll(newRecipes)
                        }
                    }

                    // Create a combined list with priority
                    val allRecipes = mutableListOf<Recipe>()

                    // Add suggested recipes first (these match user's ingredients)
                    allRecipes.addAll(suggestedRecipesCache)

                    // Add popular recipes that aren't already in suggested
                    val suggestedTitles = suggestedRecipesCache.map { it.title.lowercase() }.toSet()
                    val uniquePopular = allPopularRecipes.filter {
                        it.title.lowercase() !in suggestedTitles
                    }
                    allRecipes.addAll(uniquePopular)

                    // Remove duplicates and sort by relevance score
                    val uniqueRecipes = allRecipes.distinctBy { it.title.lowercase() }
                    val sortedRecipes = sortRecipesByRelevance(uniqueRecipes)

                    recipes.clear()
                    recipes.addAll(sortedRecipes)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()

                    showLoading(false)
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    showLoading(false)
                    swipeRefreshLayout.isRefreshing = false
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
                swipeRefreshLayout.isRefreshing = false
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
                val allRecipes = mutableListOf<Recipe>()
                val queries = getRandomPopularQueries()

                for (query in queries) {
                    val result = recipeRepository.searchRecipes(query)
                    if (result.isSuccess) {
                        val newRecipes = result.getOrNull() ?: emptyList()
                        allRecipes.addAll(newRecipes)
                    }
                }

                // Remove duplicates
                val uniqueRecipes = allRecipes.distinctBy { it.title.lowercase() }

                recipes.clear()
                recipes.addAll(uniqueRecipes)
                adapter.notifyDataSetChanged()
                updateEmptyState()

                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
            } catch (e: Exception) {
                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(
                    this@RecipesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyState()
            }
        }
    }

    private fun getRandomPopularQueries(): List<String> {
        val allQueries = listOf(
            "chicken", "pasta", "salad", "soup", "beef", "fish", "vegetarian",
            "rice", "noodles", "curry", "steak", "sandwich", "burger", "pizza",
            "dessert", "cake", "cookies", "seafood", "lamb", "pork", "tacos",
            "stir fry", "casserole", "breakfast", "lunch", "dinner", "healthy",
            "quick", "easy", "mexican", "italian", "chinese", "indian", "thai"
        )

        // Return 10-12 random queries for more variety
        return allQueries.shuffled().take((10..12).random())
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
                // Get varied search results by using related terms
                val searchQueries = generateSearchVariations(query)
                val allSearchResults = mutableListOf<Recipe>()

                for (searchQuery in searchQueries) {
                    val result = recipeRepository.searchRecipes(searchQuery)
                    if (result.isSuccess) {
                        val newRecipes = result.getOrNull() ?: emptyList()
                        allSearchResults.addAll(newRecipes)
                    }
                }

                // Remove duplicates
                val uniqueResults = allSearchResults.distinctBy { it.title.lowercase() }

                val finalResult = Result.success(uniqueResults)
                handleRecipeResult(finalResult)
                swipeRefreshLayout.isRefreshing = false
            } catch (e: Exception) {
                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(
                    this@RecipesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyState()
            }
        }
    }

    private fun generateSearchVariations(query: String): List<String> {
        val variations = mutableListOf<String>()

        // Add original query
        variations.add(query)

        // Add variations with common terms
        variations.add("$query recipe")
        variations.add("$query recipes")
        variations.add("$query dish")
        variations.add("easy $query")
        variations.add("best $query")
        variations.add("simple $query")

        // If query is more than one word, try each word separately
        val words = query.split(" ").filter { it.length > 3 }
        words.forEach { word ->
            if (word != query) {
                variations.add(word)
            }
        }

        return variations.distinct()
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