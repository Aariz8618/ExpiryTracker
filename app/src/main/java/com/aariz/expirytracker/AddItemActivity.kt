package com.aariz.expirytracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AddItemActivity : AppCompatActivity() {
    private var quantity = 1
    private lateinit var qtyText: TextView
    private lateinit var inputName: EditText
    private lateinit var textCategory: TextView
    private lateinit var textPurchaseDate: TextView
    private lateinit var textExpiryDate: TextView

    private var selectedCategory: String = ""
    private var selectedPurchaseDate: String = ""
    private var selectedExpiryDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_add_item)

        initViews()
        setupClickListeners()
        setupInitialValues()
        setupBackPressedHandler()
    }

    private fun initViews() {
        qtyText = findViewById(R.id.input_quantity)
        inputName = findViewById(R.id.input_name)
        textCategory = findViewById(R.id.text_category)
        textPurchaseDate = findViewById(R.id.text_purchase_date)
        textExpiryDate = findViewById(R.id.text_expiry_date)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Quantity controls
        findViewById<LinearLayout>(R.id.button_decrement).setOnClickListener {
            if (quantity > 1) {
                quantity--
                qtyText.text = quantity.toString()
            }
        }

        findViewById<LinearLayout>(R.id.button_increment).setOnClickListener {
            quantity++
            qtyText.text = quantity.toString()
        }

        // Category selector
        findViewById<LinearLayout>(R.id.category_container).setOnClickListener {
            showCategoryDialog()
        }

        // Purchase date picker
        findViewById<LinearLayout>(R.id.purchase_date_container).setOnClickListener {
            showDatePicker(true) // true for purchase date
        }

        // Expiry date picker
        findViewById<LinearLayout>(R.id.expiry_date_container).setOnClickListener {
            showDatePicker(false) // false for expiry date
        }

        // Scan barcode button
        findViewById<LinearLayout>(R.id.button_scan).setOnClickListener {
            // TODO: Implement barcode scanning functionality
            Toast.makeText(this, "Barcode scanning not implemented yet", Toast.LENGTH_SHORT).show()
        }

        // Save button
        findViewById<LinearLayout>(R.id.button_save_item).setOnClickListener {
            saveItem()
        }
    }

    private fun setupInitialValues() {
        qtyText.text = quantity.toString()

        // Set current date as default purchase date
        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        textPurchaseDate.text = currentDate
        selectedPurchaseDate = currentDate
        textPurchaseDate.setTextColor(getColor(R.color.gray_800))
    }

    private fun showCategoryDialog() {
        val categories = arrayOf("Dairy", "Meat", "Vegetables", "Fruits", "Bakery", "Frozen", "Pantry", "Other")

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Select Category")
        builder.setItems(categories) { _, which ->
            selectedCategory = categories[which]
            textCategory.text = selectedCategory
            textCategory.setTextColor(getColor(R.color.gray_800))
        }
        builder.show()
    }

    private fun showDatePicker(isPurchaseDate: Boolean) {
        val calendar = Calendar.getInstance()

        // Set minimum date to today for expiry date
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%02d/%02d/%d", month + 1, dayOfMonth, year)
                if (isPurchaseDate) {
                    selectedPurchaseDate = selectedDate
                    textPurchaseDate.text = selectedDate
                    textPurchaseDate.setTextColor(getColor(R.color.gray_800))
                } else {
                    selectedExpiryDate = selectedDate
                    textExpiryDate.text = selectedDate
                    textExpiryDate.setTextColor(getColor(R.color.gray_800))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // For expiry date, set minimum date to today
        if (!isPurchaseDate) {
            datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        }

        datePickerDialog.show()
    }

    private fun saveItem() {
        val itemName = inputName.text.toString().trim()

        // Validate required fields
        if (itemName.isEmpty()) {
            Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show()
            inputName.requestFocus()
            return
        }

        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedExpiryDate.isEmpty()) {
            Toast.makeText(this, "Please select expiry date", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate days left and status
        val daysLeft = calculateDaysLeft(selectedExpiryDate)
        val status = determineStatus(daysLeft)

        // Create result intent with item data
        val resultIntent = Intent().apply {
            putExtra("id", generateId())
            putExtra("name", itemName)
            putExtra("category", selectedCategory)
            putExtra("expiryDate", formatDisplayDate(selectedExpiryDate))
            putExtra("purchaseDate", formatDisplayDate(selectedPurchaseDate))
            putExtra("quantity", quantity)
            putExtra("status", status)
            putExtra("daysLeft", daysLeft)
        }

        // Set result and finish
        setResult(RESULT_OK, resultIntent)

        Toast.makeText(this, "Item '$itemName' saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
            val expiry = sdf.parse(expiryDate)
            val today = Date()

            // Reset time to start of day for accurate calculation
            val calToday = Calendar.getInstance().apply {
                time = today
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calExpiry = Calendar.getInstance().apply {
                time = expiry!!
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (expiry != null) {
                val diffInMillies = calExpiry.timeInMillis - calToday.timeInMillis
                TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS).toInt()
            } else {
                0
            }
        } catch (e: ParseException) {
            0
        }
    }

    private fun determineStatus(daysLeft: Int): String {
        return when {
            daysLeft < 0 -> "expired"
            daysLeft <= 3 -> "expiring"
            else -> "fresh"
        }
    }

    private fun formatDisplayDate(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            if (parsedDate != null) {
                outputFormat.format(parsedDate)
            } else {
                date
            }
        } catch (e: ParseException) {
            date
        }
    }

    private fun generateId(): Int {
        // Generate a simple ID based on current timestamp
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val hasUnsavedChanges = hasUnsavedChanges()

                if (hasUnsavedChanges) {
                    showDiscardChangesDialog()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun hasUnsavedChanges(): Boolean {
        val itemName = inputName.text.toString().trim()
        return itemName.isNotEmpty() ||
                selectedCategory.isNotEmpty() ||
                (selectedExpiryDate.isNotEmpty() && selectedExpiryDate != "mm/dd/yyyy") ||
                quantity != 1
    }

    private fun showDiscardChangesDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Discard Changes")
        builder.setMessage("You have unsaved changes. Are you sure you want to discard them?")
        builder.setPositiveButton("Discard") { _, _ ->
            finish()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}