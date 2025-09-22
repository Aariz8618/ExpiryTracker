package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fixed: Changed from screen_dashboard to activity_dashboard to match your XML filename
        setContentView(R.layout.screen_dashboard)

        // Recycler setup
        val recycler = findViewById<RecyclerView>(R.id.recycler_items)
        recycler.layoutManager = LinearLayoutManager(this)

        val items = listOf(
            GroceryItem(1, "Fresh Apples", "Fruits", "Sep 25, 2025", "Sep 18, 2025", 6, "fresh", 5),
            GroceryItem(2, "Organic Milk", "Dairy", "Sep 22, 2025", "Sep 18, 2025", 1, "expiring", 2),
            GroceryItem(3, "Whole Wheat Bread", "Bakery", "Sep 19, 2025", "Sep 17, 2025", 1, "expired", -1),
            GroceryItem(4, "Salmon Fillet", "Seafood", "Sep 21, 2025", "Sep 18, 2025", 2, "expiring", 1),
            GroceryItem(5, "Baby Carrots", "Vegetables", "Sep 28, 2025", "Sep 18, 2025", 3, "fresh", 8)
        )

        recycler.adapter = GroceryAdapter(items) { item ->
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

        // Fixed: Changed from BottomNavigationView to individual LinearLayout tabs
        // Set up individual tab click listeners
        val tabHome = findViewById<LinearLayout>(R.id.tab_home)
        val tabStats = findViewById<LinearLayout>(R.id.tab_stats)
        val tabRecipes = findViewById<LinearLayout>(R.id.tab_recipes)
        val tabSettings = findViewById<LinearLayout>(R.id.tab_settings)

        tabHome.setOnClickListener {
            // Already on home - do nothing or refresh
        }

        tabStats.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        tabRecipes.setOnClickListener {
            startActivity(Intent(this, RecipesActivity::class.java))
        }

        tabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Fixed: Changed from ExtendedFloatingActionButton to FloatingActionButton
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_item)
        fab.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }
    }
}