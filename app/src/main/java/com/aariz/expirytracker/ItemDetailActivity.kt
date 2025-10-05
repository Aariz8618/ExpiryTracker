package com.aariz.expirytracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ItemDetailActivity : AppCompatActivity() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private var itemId: String = ""

    // Countdown timer views
    private lateinit var countdownDays: TextView
    private lateinit var countdownHours: TextView
    private lateinit var countdownMinutes: TextView
    private lateinit var countdownSection: LinearLayout
    private lateinit var statusChip: TextView
    private lateinit var statusText: TextView
    private lateinit var itemIcon: ImageView

    // Timeline views
    private lateinit var timelinePurchaseDot: View
    private lateinit var timelineExpiryDot: View
    private lateinit var timelineProgress: View
    private lateinit var timelineLine: View

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_item_detail)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(R.id.header_section).applyHeaderInsets()
        findViewById<View>(R.id.bottom_bar).applyBottomNavInsets()

        firestoreRepository = FirestoreRepository()

        // Initialize loading views
        loadingOverlay = findViewById(R.id.loading_overlay)
        progressBar = findViewById(R.id.progress_bar)

        // Initialize countdown views
        countdownDays = findViewById(R.id.text_countdown_days)
        countdownHours = findViewById(R.id.text_countdown_hours)
        countdownMinutes = findViewById(R.id.text_countdown_minutes)
        countdownSection = findViewById(R.id.countdown_section)
        statusChip = findViewById(R.id.text_status_chip)
        statusText = findViewById(R.id.text_status)
        itemIcon = findViewById(R.id.img_item_icon)

        // Initialize timeline views
        timelinePurchaseDot = findViewById(R.id.timeline_purchase_dot)
        timelineExpiryDot = findViewById(R.id.timeline_expiry_dot)
        timelineProgress = findViewById(R.id.timeline_progress)
        timelineLine = findViewById(R.id.timeline_line)

        itemId = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val expiry = intent.getStringExtra("expiryDate") ?: ""
        val purchase = intent.getStringExtra("purchaseDate") ?: ""
        val quantity = intent.getIntExtra("quantity", 0)
        val status = intent.getStringExtra("status") ?: "fresh"
        val oldDaysLeft = intent.getIntExtra("daysLeft", 0) // Original days left

        // RECALCULATE days left and status in real-time
        val actualDaysLeft = calculateDaysLeft(expiry)
        val actualStatus = determineStatus(actualDaysLeft, status)

        findViewById<TextView>(R.id.text_name).text = name
        findViewById<TextView>(R.id.text_category_chip).text = category
        findViewById<TextView>(R.id.text_expiry).text = expiry
        findViewById<TextView>(R.id.text_purchase).text = purchase
        findViewById<TextView>(R.id.text_quantity).text = quantity.toString()

        setItemIcon(category)
        updateStatusDisplay(actualStatus, actualDaysLeft) // Use recalculated values
        updateTimelineVisuals(purchase, expiry, actualStatus) // Use recalculated status
        startCountdownTimer(expiry, actualStatus) // Use recalculated status
        setupClickListeners(actualStatus)
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.isLenient = false

            val expiry = sdf.parse(expiryDate) ?: return 0

            val expiryCalendar = Calendar.getInstance().apply {
                time = expiry
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffInMillis = expiryCalendar.timeInMillis - todayCalendar.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun determineStatus(daysLeft: Int, currentStatus: String): String {
        // Don't change status if item is marked as "used"
        if (currentStatus == "used") return "used"

        return when {
            daysLeft < 0 -> "expired"
            daysLeft == 0 -> "expiring"
            daysLeft <= 3 -> "expiring"
            else -> "fresh"
        }
    }

    private fun setItemIcon(category: String) {
        val iconRes = when (category.lowercase()) {
            "fruits", "fruit" -> R.drawable.fruits
            "dairy" -> R.drawable.milk
            "vegetables", "vegetable" -> R.drawable.vegetables
            "meat" -> R.drawable.meat
            "bakery" -> R.drawable.bread
            "frozen" -> R.drawable.frozen
            "beverages" -> R.drawable.beverages
            "cereals" -> R.drawable.cereals
            "sweets" -> R.drawable.sweets
            else -> R.drawable.ic_grocery
        }
        itemIcon.setImageResource(iconRes)
    }

    private fun updateStatusDisplay(status: String, daysLeft: Int) {
        when (status) {
            "fresh" -> {
                statusChip.text = "Fresh ($daysLeft days left)"
                statusChip.setTextColor(Color.parseColor("#2E7D32"))
                statusText.text = "Fresh"
                statusText.setTextColor(Color.parseColor("#2E7D32"))
            }
            "expiring" -> {
                val daysLabel = when {
                    daysLeft == 0 -> "Today"
                    daysLeft == 1 -> "1 day"
                    else -> "$daysLeft days"
                }
                statusChip.text = "Expires in $daysLabel"
                statusChip.setTextColor(Color.parseColor("#F57C00"))
                statusText.text = "Expiring"
                statusText.setTextColor(Color.parseColor("#F57C00"))
            }
            "expired" -> {
                val daysAgo = Math.abs(daysLeft)
                val expiredText = when {
                    daysAgo == 0 -> "Expired today"
                    daysAgo == 1 -> "Expired 1 day ago"
                    else -> "Expired $daysAgo days ago"
                }
                statusChip.text = expiredText
                statusChip.setTextColor(Color.parseColor("#B71C1C"))
                statusText.text = "Expired"
                statusText.setTextColor(Color.parseColor("#B71C1C"))
            }
            "used" -> {
                statusChip.text = "Used ✓"
                statusChip.setTextColor(Color.parseColor("#2E7D32"))
                statusText.text = "Used ✓"
                statusText.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
    }

    private fun updateTimelineVisuals(purchaseDateStr: String, expiryDateStr: String, status: String) {
        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val purchaseDate = sdf.parse(purchaseDateStr)
            val expiryDate = sdf.parse(expiryDateStr)
            val now = Calendar.getInstance().time

            if (purchaseDate != null && expiryDate != null) {
                val totalDuration = expiryDate.time - purchaseDate.time
                val elapsed = now.time - purchaseDate.time
                val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                // Update progress bar height
                val lineHeight = timelineLine.layoutParams.height
                val progressHeight = (lineHeight * progress).toInt()
                timelineProgress.layoutParams.height = progressHeight
                timelineProgress.requestLayout()

                // Update colors based on status
                when (status) {
                    "fresh" -> {
                        timelineProgress.setBackgroundColor(Color.parseColor("#4CAF50"))
                    }
                    "expiring" -> {
                        timelineProgress.setBackgroundColor(Color.parseColor("#FF9800"))
                    }
                    "expired" -> {
                        timelineProgress.setBackgroundColor(Color.parseColor("#F44336"))
                        timelineExpiryDot.setBackgroundResource(R.drawable.circle_icon_bg)
                    }
                    "used" -> {
                        timelineProgress.setBackgroundColor(Color.parseColor("#4CAF50"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCountdownTimer(expiryDateStr: String, status: String) {
        // Hide countdown for expired or used items
        if (status == "expired" || status == "used") {
            countdownSection.visibility = View.GONE
            return
        }

        countdownSection.visibility = View.VISIBLE

        countdownRunnable = object : Runnable {
            override fun run() {
                try {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val expiryDate = sdf.parse(expiryDateStr)

                    if (expiryDate != null) {
                        // Set expiry date to end of day (23:59:59) for accurate countdown
                        val expiryCalendar = Calendar.getInstance().apply {
                            time = expiryDate
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }

                        val now = Calendar.getInstance()
                        val diffInMillis = expiryCalendar.timeInMillis - now.timeInMillis

                        if (diffInMillis <= 0) {
                            // Expired
                            countdownDays.text = "00"
                            countdownHours.text = "00"
                            countdownMinutes.text = "00"
                            countdownSection.visibility = View.GONE
                            return
                        }

                        val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                        val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis) % 24
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) % 60

                        countdownDays.text = String.format("%02d", days)
                        countdownHours.text = String.format("%02d", hours)
                        countdownMinutes.text = String.format("%02d", minutes)

                        // Update color based on time remaining
                        val color = when {
                            days < 1 -> Color.parseColor("#B71C1C") // Red
                            days <= 3 -> Color.parseColor("#F57C00") // Orange
                            else -> Color.parseColor("#2E7D32") // Green
                        }
                        countdownDays.setTextColor(color)
                        countdownHours.setTextColor(color)
                        countdownMinutes.setTextColor(color)

                        // Schedule next update in 1 minute
                        handler.postDelayed(this, 60000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Start the countdown
        countdownRunnable?.let { handler.post(it) }
    }

    private fun setupClickListeners(actualStatus: String) {
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val editButton = findViewById<MaterialCardView>(R.id.button_edit)
        val markUsedButton = findViewById<MaterialCardView>(R.id.button_mark_used)

        // Hide edit and mark as used buttons if item is already used
        if (actualStatus == "used") {
            editButton.visibility = View.GONE
            markUsedButton.visibility = View.GONE
        } else {
            editButton.setOnClickListener {
                editItem()
            }

            markUsedButton.setOnClickListener {
                showMarkAsUsedConfirmation()
            }
        }

        findViewById<MaterialCardView>(R.id.button_delete).setOnClickListener {
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
        finish()
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
                    createdAt = Date(),
                    updatedAt = Date()
                )

                val result = firestoreRepository.updateGroceryItem(updatedItem)
                showLoading(false)

                if (result.isSuccess) {
                    Toast.makeText(this@ItemDetailActivity, "Item marked as used!", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        // Stop the countdown timer
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }
}