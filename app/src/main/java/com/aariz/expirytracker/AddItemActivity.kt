package com.aariz.expirytracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

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
    }

    private fun showCategoryDialog() {
        // For now, showing a simple category selection
        // You can replace this with a proper dialog or spinner
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
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%02d/%02d/%d", month + 1, dayOfMonth, year)
                if (isPurchaseDate) {
                    selectedPurchaseDate = selectedDate
                    textPurchaseDate.text = selectedDate
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

        // TODO: Save item to database or pass back to previous activity
        // For now, just show success message
        Toast.makeText(this, "Item '$itemName' saved successfully!", Toast.LENGTH_SHORT).show()

        // Return to previous screen
        finish()
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // You can add confirmation dialog here if needed
                // For example, if user has unsaved changes
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
        // Check if user has entered any data
        val itemName = inputName.text.toString().trim()
        return itemName.isNotEmpty() ||
                selectedCategory.isNotEmpty() ||
                selectedExpiryDate.isNotEmpty() ||
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