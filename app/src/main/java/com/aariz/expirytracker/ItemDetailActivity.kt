package com.aariz.expirytracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ItemDetailActivity : AppCompatActivity() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private var itemId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_item_detail)

        firestoreRepository = FirestoreRepository()

        // Initialize loading views
        loadingOverlay = findViewById(R.id.loading_overlay)
        progressBar = findViewById(R.id.progress_bar)

        itemId = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val expiry = intent.getStringExtra("expiryDate") ?: ""
        val purchase = intent.getStringExtra("purchaseDate") ?: ""
        val quantity = intent.getIntExtra("quantity", 0)
        val status = intent.getStringExtra("status") ?: "fresh"
        val daysLeft = intent.getIntExtra("daysLeft", 0)

        findViewById<TextView>(R.id.text_name).text = name
        findViewById<TextView>(R.id.text_category_chip).text = category
        findViewById<TextView>(R.id.text_expiry).text = expiry
        findViewById<TextView>(R.id.text_purchase).text = purchase
        findViewById<TextView>(R.id.text_quantity).text = quantity.toString()

        val statusChip = findViewById<TextView>(R.id.text_status_chip)
        updateStatusChip(statusChip, status, daysLeft)

        setupClickListeners()
    }

    private fun updateStatusChip(statusChip: TextView, status: String, daysLeft: Int) {
        when (status) {
            "fresh" -> {
                statusChip.text = "Fresh ($daysLeft days left)"
                statusChip.setBackgroundResource(R.drawable.chip_green)
                statusChip.setTextColor(Color.parseColor("#2E7D32"))
            }
            "expiring" -> {
                val daysLabel = if (daysLeft == 1) "1 day" else "$daysLeft days"
                statusChip.text = "Expires in $daysLabel"
                statusChip.setBackgroundResource(R.drawable.chip_yellow)
                statusChip.setTextColor(Color.parseColor("#8D6E00"))
            }
            "expired" -> {
                statusChip.text = "Expired"
                statusChip.setBackgroundResource(R.drawable.chip_red)
                statusChip.setTextColor(Color.parseColor("#B71C1C"))
            }
            "used" -> {
                statusChip.text = "Used ✓"
                statusChip.setBackgroundResource(R.drawable.chip_green)
                statusChip.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
    }

    private fun setupClickListeners() {
        findViewById<LinearLayout>(R.id.button_edit).setOnClickListener {
            editItem()
        }

        findViewById<LinearLayout>(R.id.button_mark_used).setOnClickListener {
            showMarkAsUsedConfirmation()
        }

        findViewById<LinearLayout>(R.id.button_delete).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun editItem() {
        val intent = Intent(this, EditItemActivity::class.java).apply {
            putExtra("id", itemId)
            putExtra("name", getIntent().getStringExtra("name"))
            putExtra("category", getIntent().getStringExtra("category"))
            putExtra("expiryDate", getIntent().getStringExtra("expiryDate"))
            putExtra("purchaseDate", getIntent().getStringExtra("purchaseDate"))
            putExtra("quantity", getIntent().getIntExtra("quantity", 1))
            putExtra("status", getIntent().getStringExtra("status"))
            putExtra("barcode", getIntent().getStringExtra("barcode") ?: "")
            putExtra("imageUrl", getIntent().getStringExtra("imageUrl") ?: "")
            putExtra("isGS1", getIntent().getBooleanExtra("isGS1", false))
        }
        startActivity(intent)
        finish() // Close detail screen so we return to dashboard after edit
    }

    private fun showMarkAsUsedConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Mark as Used")
            .setMessage("Mark this item as used? This will update its status and the item card will turn green.")
            .setPositiveButton("Mark as Used") { _, _ ->
                markItemAsUsed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markItemAsUsed() {
        if (itemId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid item ID", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                // Create updated item with "used" status
                val updatedItem = GroceryItem(
                    id = itemId,
                    name = intent.getStringExtra("name") ?: "",
                    category = intent.getStringExtra("category") ?: "",
                    expiryDate = intent.getStringExtra("expiryDate") ?: "",
                    purchaseDate = intent.getStringExtra("purchaseDate") ?: "",
                    quantity = intent.getIntExtra("quantity", 0),
                    status = "used",
                    daysLeft = intent.getIntExtra("daysLeft", 0),
                    barcode = intent.getStringExtra("barcode") ?: "",
                    imageUrl = intent.getStringExtra("imageUrl") ?: "",
                    isGS1 = intent.getBooleanExtra("isGS1", false),
                    createdAt = java.util.Date(),
                    updatedAt = java.util.Date()
                )

                val result = firestoreRepository.updateGroceryItem(updatedItem)
                showLoading(false)

                if (result.isSuccess) {
                    Toast.makeText(this@ItemDetailActivity, "Item marked as used!", Toast.LENGTH_SHORT).show()
                    // Return to dashboard with success flag
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("item_updated", true)
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@ItemDetailActivity,
                        "Failed to update item: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@ItemDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete this item? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem() {
        if (itemId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid item ID", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.deleteGroceryItem(itemId)
                showLoading(false)

                if (result.isSuccess) {
                    Toast.makeText(this@ItemDetailActivity, "Item deleted successfully!", Toast.LENGTH_SHORT).show()
                    // Return to dashboard with success flag
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("item_deleted", true)
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@ItemDetailActivity,
                        "Failed to delete item: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@ItemDetailActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}