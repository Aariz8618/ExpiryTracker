package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var greetingText: TextView
    private lateinit var profileButton: ImageView
    private lateinit var loadingIndicator: LinearLayout
    private val groceryItems = mutableListOf<GroceryItem>()

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    // Activity Result Launchers
    private val addItemLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val itemSaved = result.data?.getBooleanExtra("item_saved", false) ?: false
            if (itemSaved) {
                Toast.makeText(this, "Item added successfully!", Toast.LENGTH_SHORT).show()
                // Reload all items to get the fresh data from Firestore
                loadGroceryItems()
            }
        }
    }

    private val itemDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Check if item was updated or deleted
            val itemUpdated = result.data?.getBooleanExtra("item_updated", false) ?: false
            val itemDeleted = result.data?.getBooleanExtra("item_deleted", false) ?: false

            if (itemUpdated || itemDeleted) {
                // Reload items to reflect changes
                loadGroceryItems()
            }
        }
    }

    private val profileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if user logged out from profile screen
        if (auth.currentUser == null) {
            // User logged out, clear the logged-in flag and redirect to login
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            // User came back from profile, refresh greeting
            setupGreeting()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        // Initialize Firebase components
        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        // Check if user is authenticated
        if (auth.currentUser == null) {
            // Clear logged-in flag and redirect to login activity
            clearUserLoggedInFlag()
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupNavigation()
        setupFab()
        setupProfileButton()
        setupGreeting()
        loadGroceryItems()
    }

    override fun onResume() {
        super.onResume()
        // Check if user is still authenticated when returning to dashboard
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Reload items when returning to dashboard to get fresh data
        loadGroceryItems()
        // Update greeting in case user updated their profile
        setupGreeting()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_items)
        emptyState = findViewById(R.id.empty_state)
        greetingText = findViewById(R.id.tv_greeting)
        profileButton = findViewById(R.id.iv_profile)
        loadingIndicator = findViewById(R.id.loading_indicator)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GroceryAdapter(groceryItems) { item ->
            val intent = Intent(this, ItemDetailActivity::class.java).apply {
                putExtra("id", item.id)
                putExtra("name", item.name)
                putExtra("category", item.category)
                putExtra("expiryDate", item.expiryDate)
                putExtra("purchaseDate", item.purchaseDate)
                putExtra("quantity", item.quantity)
                putExtra("status", item.status)
                putExtra("daysLeft", item.daysLeft)
                putExtra("barcode", item.barcode)
                putExtra("imageUrl", item.imageUrl)
                putExtra("isGS1", item.isGS1)
            }
            itemDetailLauncher.launch(intent)
        }

        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        val tabHome = findViewById<LinearLayout>(R.id.tab_home)
        val tabStats = findViewById<LinearLayout>(R.id.tab_stats)
        val tabRecipes = findViewById<LinearLayout>(R.id.tab_recipes)
        val tabSettings = findViewById<LinearLayout>(R.id.tab_settings)

        tabHome.setOnClickListener {
            // Already on home - refresh data
            loadGroceryItems()
        }

        tabStats.setOnClickListener {
            // startActivity(Intent(this, StatisticsActivity::class.java))
            Toast.makeText(this, "Statistics coming soon!", Toast.LENGTH_SHORT).show()
        }

        tabRecipes.setOnClickListener {
            // startActivity(Intent(this, RecipesActivity::class.java))
            Toast.makeText(this, "Recipes coming soon!", Toast.LENGTH_SHORT).show()
        }

        tabSettings.setOnClickListener {
            // Navigate to Profile/Settings screen
            val intent = Intent(this, ProfileActivity::class.java)
            profileLauncher.launch(intent)
        }
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_item)
        fab.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            addItemLauncher.launch(intent)
        }
    }

    private fun setupProfileButton() {
        profileButton.setOnClickListener {
            // Navigate to Profile screen
            val intent = Intent(this, ProfileActivity::class.java)
            profileLauncher.launch(intent)
        }
    }

    private fun setupGreeting() {
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email

        val greeting = when {
            !displayName.isNullOrEmpty() -> "Hello, $displayName 👋"
            !email.isNullOrEmpty() -> "Hello, ${email.substringBefore("@")} 👋"
            else -> "Hello, User 👋"
        }

        greetingText.text = greeting
    }

    private fun loadGroceryItems() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()
                showLoading(false)

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    // Update the list and notify adapter
                    groceryItems.clear()
                    groceryItems.addAll(items)
                    adapter.notifyDataSetChanged()

                    updateEmptyState()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(this@DashboardActivity, "Failed to load items: ${error?.message}", Toast.LENGTH_LONG).show()
                    updateEmptyState()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        if (groceryItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun clearUserLoggedInFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        // No cleanup needed for ProgressBar
    }
}