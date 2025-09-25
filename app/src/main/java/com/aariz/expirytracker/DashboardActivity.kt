package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private val groceryItems = mutableListOf<GroceryItem>()

    companion object {
        const val ADD_ITEM_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        initViews()
        setupRecyclerView()
        setupNavigation()
        setupFab()
        updateEmptyState()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_items)
        emptyState = findViewById(R.id.empty_state)
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
            }
            startActivity(intent)
        }

        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        val tabHome = findViewById<LinearLayout>(R.id.tab_home)
        val tabStats = findViewById<LinearLayout>(R.id.tab_stats)
        val tabRecipes = findViewById<LinearLayout>(R.id.tab_recipes)
        val tabSettings = findViewById<LinearLayout>(R.id.tab_settings)

        tabHome.setOnClickListener {
            // Already on home - do nothing or refresh
        }

        tabStats.setOnClickListener {
            // startActivity(Intent(this, StatisticsActivity::class.java))
        }

        tabRecipes.setOnClickListener {
            // startActivity(Intent(this, RecipesActivity::class.java))
        }

        tabSettings.setOnClickListener {
            // startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_item)
        fab.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            startActivityForResult(intent, ADD_ITEM_REQUEST_CODE)
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_ITEM_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get the new item data from the intent
            val newItem = GroceryItem(
                id = data.getIntExtra("id", 0),
                name = data.getStringExtra("name") ?: "",
                category = data.getStringExtra("category") ?: "",
                expiryDate = data.getStringExtra("expiryDate") ?: "",
                purchaseDate = data.getStringExtra("purchaseDate") ?: "",
                quantity = data.getIntExtra("quantity", 1),
                status = data.getStringExtra("status") ?: "fresh",
                daysLeft = data.getIntExtra("daysLeft", 0)
            )

            // Add the new item to the list
            groceryItems.add(0, newItem) // Add at the beginning

            // Notify adapter about the new item
            adapter.notifyItemInserted(0)

            // Update empty state
            updateEmptyState()

            // Scroll to the top to show newly added item
            recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun updateEmptyState() {
        if (groceryItems.isEmpty()) {
            recyclerView.visibility = android.view.View.GONE
            emptyState.visibility = android.view.View.VISIBLE
        } else {
            recyclerView.visibility = android.view.View.VISIBLE
            emptyState.visibility = android.view.View.GONE
        }
    }
}