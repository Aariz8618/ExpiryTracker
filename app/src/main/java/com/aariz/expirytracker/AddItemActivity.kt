package com.aariz.expirytracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
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
    private lateinit var saveButton: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar

    private var selectedCategory: String = ""
    private var selectedPurchaseDate: String = ""
    private var selectedExpiryDate: String = ""

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_add_item)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to add items", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        setupInitialValues()
        setupBackPressedHandler()
        createUserProfileIfNeeded()
    }

    private fun createUserProfileIfNeeded() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val user = User(
                    id = currentUser.uid,
                    name = currentUser.displayName ?: "User",
                    email = currentUser.email ?: ""
                )
                firestoreRepository.createUserProfile(user)
            } catch (e: Exception) {
                Log.e("AddItemActivity", "Error creating user profile: ${e.message}")
                // Don't show error to user as this is not critical for adding items
            }
        }
    }

    private fun initViews() {
        qtyText = findViewById(R.id.input_quantity)
        inputName = findViewById(R.id.input_name)
        textCategory = findViewById(R.id.text_category)
        textPurchaseDate = findViewById(R.id.text_purchase_date)
        textExpiryDate = findViewById(R.id.text_expiry_date)
        saveButton = findViewById(R.id.button_save_item)
        loadingOverlay = findViewById(R.id.loading_overlay)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<LinearLayout>(R.id.button_decrement).setOnClickListener {
            if (quantity > 1) quantity--
            qtyText.text = quantity.toString()
        }

        findViewById<LinearLayout>(R.id.button_increment).setOnClickListener {
            quantity++
            qtyText.text = quantity.toString()
        }

        findViewById<LinearLayout>(R.id.category_container).setOnClickListener {
            showCategoryDialog()
        }

        findViewById<LinearLayout>(R.id.purchase_date_container).setOnClickListener {
            showDatePicker(true)
        }

        findViewById<LinearLayout>(R.id.expiry_date_container).setOnClickListener {
            showDatePicker(false)
        }

        findViewById<LinearLayout>(R.id.button_scan).setOnClickListener {
            Toast.makeText(this, "Barcode scanning not implemented yet", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            saveItemToFirestore()
        }
    }

    private fun setupInitialValues() {
        qtyText.text = quantity.toString()
        val currentDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        textPurchaseDate.text = currentDate
        selectedPurchaseDate = currentDate
        textPurchaseDate.setTextColor(getColor(R.color.gray_800))
    }

    private fun showCategoryDialog() {
        val categories = arrayOf("Dairy", "Meat", "Vegetables", "Fruits", "Bakery", "Frozen", "Pantry", "Other")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                selectedCategory = categories[which]
                textCategory.text = selectedCategory
                textCategory.setTextColor(getColor(R.color.gray_800))
            }
            .show()
    }

    private fun showDatePicker(isPurchaseDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
                val selectedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)

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
        if (!isPurchaseDate) datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun saveItemToFirestore() {
        val itemName = inputName.text.toString().trim()
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

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val daysLeft = calculateDaysLeft(selectedExpiryDate)
        val status = determineStatus(daysLeft)
        val now = Date()

        Log.d("AddItemActivity", "Saving item for userId: ${currentUser.uid}")

        val groceryItem = GroceryItem(
            name = itemName,
            category = selectedCategory,
            expiryDate = selectedExpiryDate,
            purchaseDate = selectedPurchaseDate,
            quantity = quantity,
            status = status,
            daysLeft = daysLeft,
            createdAt = now,
            updatedAt = now
        )

        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.addGroceryItem(groceryItem)
                showLoading(false)
                if (result.isSuccess) {
                    Log.d("AddItemActivity", "Item saved successfully: $itemName")
                    Toast.makeText(this@AddItemActivity, "Item saved successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("AddItemActivity", "Failed to save item: ${error?.message}", error)
                    Toast.makeText(this@AddItemActivity, "Failed to save item: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Log.e("AddItemActivity", "Exception while saving item: ${e.message}", e)
                Toast.makeText(this@AddItemActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        saveButton.isEnabled = !show
        saveButton.alpha = if (show) 0.6f else 1f
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val expiry = sdf.parse(expiryDate)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            val diff = expiry!!.time - today.time
            TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun determineStatus(daysLeft: Int) = when {
        daysLeft < 0 -> "expired"
        daysLeft <= 3 -> "expiring"
        else -> "fresh"
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) showDiscardChangesDialog() else finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun hasUnsavedChanges(): Boolean {
        return inputName.text.toString().trim().isNotEmpty() ||
                selectedCategory.isNotEmpty() ||
                selectedExpiryDate.isNotEmpty() ||
                quantity != 1
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}