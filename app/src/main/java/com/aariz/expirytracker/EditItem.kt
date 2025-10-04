package com.aariz.expirytracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EditItemActivity : AppCompatActivity() {

    private var quantity = 1
    private lateinit var qtyText: TextView
    private lateinit var inputName: EditText
    private lateinit var textCategory: TextView
    private lateinit var textPurchaseDate: TextView
    private lateinit var textExpiryDate: TextView
    private lateinit var saveButton: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var productImageView: ImageView
    private lateinit var barcodeInfoLayout: LinearLayout
    private lateinit var barcodeText: TextView

    private var selectedCategory: String = ""
    private var selectedPurchaseDate: String = ""
    private var selectedExpiryDate: String = ""
    private var itemId: String = ""
    private var scannedBarcode: String = ""
    private var productImageUrl: String = ""
    private var isGS1Code: Boolean = false
    private var originalStatus: String = "fresh"
    private var createdAt: Date = Date()

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_edit_item)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to edit items", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadItemData()
        setupClickListeners()
        setupBackPressedHandler()
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
        productImageView = findViewById(R.id.product_image_view)
        barcodeInfoLayout = findViewById(R.id.barcode_info_layout)
        barcodeText = findViewById(R.id.barcode_text)
        inputName.addTextChangedListener(TextCapitalizationWatcher())+00
    }

    private fun loadItemData() {
        // Get data from intent
        itemId = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val expiryDate = intent.getStringExtra("expiryDate") ?: ""
        val purchaseDate = intent.getStringExtra("purchaseDate") ?: ""
        quantity = intent.getIntExtra("quantity", 1)
        originalStatus = intent.getStringExtra("status") ?: "fresh"
        scannedBarcode = intent.getStringExtra("barcode") ?: ""
        productImageUrl = intent.getStringExtra("imageUrl") ?: ""
        isGS1Code = intent.getBooleanExtra("isGS1", false)

        // Populate fields
        inputName.setText(name)
        qtyText.text = quantity.toString()

        selectedCategory = category
        textCategory.text = category
        textCategory.setTextColor(getColor(R.color.gray_800))

        selectedPurchaseDate = purchaseDate
        textPurchaseDate.text = purchaseDate
        textPurchaseDate.setTextColor(getColor(R.color.gray_800))

        selectedExpiryDate = expiryDate
        textExpiryDate.text = expiryDate
        textExpiryDate.setTextColor(getColor(R.color.gray_800))

        // Show barcode info if available
        if (scannedBarcode.isNotEmpty()) {
            displayBarcodeInfo(scannedBarcode)
        }

        // Load product image if available
        if (productImageUrl.isNotEmpty()) {
            productImageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(productImageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(productImageView)
        } else {
            productImageView.visibility = View.GONE
        }
    }

    private fun displayBarcodeInfo(barcode: String) {
        barcodeInfoLayout.visibility = View.VISIBLE
        barcodeText.text = "Barcode: $barcode"
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

        saveButton.setOnClickListener {
            updateItemInFirestore()
        }
    }

    private fun showCategoryDialog() {
        val categories = arrayOf("Dairy", "Meat", "Vegetables", "Fruits", "Bakery", "Frozen", "Beverages", "Cereals", "Sweets", "Other")
        val currentIndex = categories.indexOf(selectedCategory)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Category")
            .setSingleChoiceItems(categories, currentIndex) { dialog, which ->
                selectedCategory = categories[which]
                textCategory.text = selectedCategory
                textCategory.setTextColor(getColor(R.color.gray_800))
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker(isPurchaseDate: Boolean) {
        val calendar = Calendar.getInstance()

        // Parse current date to set initial picker date
        try {
            val currentDate = if (isPurchaseDate) selectedPurchaseDate else selectedExpiryDate
            if (currentDate.isNotEmpty()) {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                sdf.parse(currentDate)?.let {
                    calendar.time = it
                }
            }
        } catch (e: Exception) {
            Log.e("EditItemActivity", "Error parsing date", e)
        }

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

    private fun updateItemInFirestore() {
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
        val status = if (originalStatus == "used") "used" else determineStatus(daysLeft)

        Log.d("EditItemActivity", "Updating item: $itemName with id: $itemId")

        val groceryItem = GroceryItem(
            id = itemId,
            name = itemName,
            category = selectedCategory,
            expiryDate = selectedExpiryDate,
            purchaseDate = selectedPurchaseDate,
            quantity = quantity,
            status = status,
            daysLeft = daysLeft,
            barcode = scannedBarcode,
            imageUrl = productImageUrl,
            isGS1 = isGS1Code,
            createdAt = createdAt,
            updatedAt = Date()
        )

        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.updateGroceryItem(groceryItem)
                showLoading(false)
                if (result.isSuccess) {
                    Log.d("EditItemActivity", "Item updated successfully: $itemName")
                    Toast.makeText(this@EditItemActivity, "Item updated successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("EditItemActivity", "Failed to update item: ${error?.message}", error)
                    Toast.makeText(this@EditItemActivity, "Failed to update item: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Log.e("EditItemActivity", "Exception while updating item: ${e.message}", e)
                Toast.makeText(this@EditItemActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
        val originalName = intent.getStringExtra("name") ?: ""
        val originalCategory = intent.getStringExtra("category") ?: ""
        val originalExpiry = intent.getStringExtra("expiryDate") ?: ""
        val originalPurchase = intent.getStringExtra("purchaseDate") ?: ""
        val originalQuantity = intent.getIntExtra("quantity", 1)

        return inputName.text.toString().trim() != originalName ||
                selectedCategory != originalCategory ||
                selectedExpiryDate != originalExpiry ||
                selectedPurchaseDate != originalPurchase ||
                quantity != originalQuantity
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