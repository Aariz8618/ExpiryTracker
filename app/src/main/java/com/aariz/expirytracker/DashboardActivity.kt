package com.aariz.expirytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var greetingText: TextView
    private lateinit var profileButton: ImageView
    private lateinit var loadingIndicator: LinearLayout
    private val groceryItems = mutableListOf<GroceryItem>()
    private lateinit var btnFilter: MaterialButton
    private lateinit var bottomNav: LinearLayout
    private lateinit var headerSection: LinearLayout

    private val allGroceryItems = mutableListOf<GroceryItem>()
    private val filteredGroceryItems = mutableListOf<GroceryItem>()
    private var currentFilter = "all"
    private var currentFilterIndex = 0
    private val selectedFilters = mutableSetOf<String>()

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var hasAskedForNotificationPermission = false

    private val filterOptions = arrayOf("Fresh", "Expiring Soon", "Expired", "Used")
    private val filterValues = arrayOf("fresh", "expiring", "expired", "used")

    // Notification permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled! You'll receive expiry reminders.", Toast.LENGTH_LONG).show()
            scheduleNotifications()
        } else {
            Toast.makeText(this, "Notifications disabled. You won't receive expiry reminders.", Toast.LENGTH_LONG).show()
        }
        saveNotificationPermissionAsked()
    }

    // Activity Result Launchers
    private val addItemLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadGroceryItems()
        }
    }

    private val itemDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val itemUpdated = result.data?.getBooleanExtra("item_updated", false) ?: false
            val itemDeleted = result.data?.getBooleanExtra("item_deleted", false) ?: false

            if (itemUpdated || itemDeleted) {
                loadGroceryItems()
            }
        }
    }

    private val profileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            setupGreeting()
            loadProfileImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()
        firestore = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupWindowInsets()
        setupRecyclerView()
        setupFilterButton()
        setupNavigation()
        setupFab()
        setupProfileButton()
        setupGreeting()
        loadGroceryItems()

        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadGroceryItems()
        setupGreeting()
        loadProfileImage()
    }

    private fun setupWindowInsets() {
        // Apply insets to header section
        // This adds top, left, and right padding while preserving bottom padding
        headerSection.applyHeaderInsets()

        // Apply insets to bottom navigation
        // This adds bottom, left, and right padding while preserving top padding
        bottomNav.applyBottomNavInsets()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            hasAskedForNotificationPermission = prefs.getBoolean("notification_permission_asked", false)

            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                scheduleNotifications()
            } else if (!hasAskedForNotificationPermission) {
                showNotificationPermissionDialog()
            }
        } else {
            scheduleNotifications()
        }
    }

    private fun showNotificationPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Notifications")
            .setMessage("Stay informed about items nearing expiry! Enable notifications to receive timely reminders.")
            .setPositiveButton("Enable") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now") { _, _ ->
                saveNotificationPermissionAsked()
                Toast.makeText(this, "You can enable notifications later in Settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveNotificationPermissionAsked() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("notification_permission_asked", true).apply()
    }

    private fun scheduleNotifications() {
        val notificationScheduler = NotificationScheduler(this)
        notificationScheduler.scheduleExpiryChecks()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_items)
        emptyState = findViewById(R.id.empty_state)
        tvEmptyMessage = findViewById(R.id.tv_empty_message)
        greetingText = findViewById(R.id.tv_greeting)
        profileButton = findViewById(R.id.iv_profile)
        loadingIndicator = findViewById(R.id.loading_indicator)
        btnFilter = findViewById(R.id.btn_filter)
        bottomNav = findViewById(R.id.bottom_nav)
        headerSection = findViewById(R.id.header_section)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GroceryAdapter(filteredGroceryItems) { item ->
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

    private fun setupFilterButton() {
        btnFilter.setOnClickListener {
            showMultiSelectFilterDialog()
        }
    }

    private fun showMultiSelectFilterDialog() {
        // Create a boolean array to track selected items
        val checkedItems = BooleanArray(filterOptions.size) { index ->
            selectedFilters.contains(filterValues[index])
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Filters")
            .setMultiChoiceItems(filterOptions, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedFilters.add(filterValues[which])
                } else {
                    selectedFilters.remove(filterValues[which])
                }
            }
            .setPositiveButton("Apply") { _, _ ->
                applyFilter()
                updateFilterButtonText()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear All") { _, _ ->
                selectedFilters.clear()
                applyFilter()
                updateFilterButtonText()
            }
            .show()
    }

    private fun updateFilterButtonText() {
        val buttonText = when {
            selectedFilters.isEmpty() -> "Filter: All"
            selectedFilters.size == 1 -> {
                val index = filterValues.indexOf(selectedFilters.first())
                "Filter: ${filterOptions[index]}"
            }
            else -> "Filter: ${selectedFilters.size} selected"
        }
        btnFilter.text = buttonText
    }

    private fun applyFilter() {
        filteredGroceryItems.clear()

        // If no filters selected, show all items
        if (selectedFilters.isEmpty()) {
            filteredGroceryItems.addAll(allGroceryItems)
        } else {
            // Apply multi-select filter logic
            val filtered = allGroceryItems.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                val status = determineStatus(daysLeft, item.status)

                selectedFilters.any { filter ->
                    when (filter) {
                        "fresh" -> status == "fresh"
                        "expiring" -> status == "expiring"
                        "expired" -> status == "expired"
                        "used" -> item.status == "used"
                        else -> false
                    }
                }
            }
            filteredGroceryItems.addAll(filtered)
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun setupNavigation() {
        val tabHome = findViewById<LinearLayout>(R.id.tab_home)
        val tabStats = findViewById<LinearLayout>(R.id.tab_stats)
        val tabRecipes = findViewById<LinearLayout>(R.id.tab_recipes)
        val tabSettings = findViewById<LinearLayout>(R.id.tab_settings)

        tabHome.setOnClickListener {
            loadGroceryItems()
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

        // Load profile image
        loadProfileImage()
    }

    private fun loadProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val profileImageUrl = document.getString("profileImageUrl")
                        if (!profileImageUrl.isNullOrEmpty()) {
                            // Load profile image from Cloudinary
                            Glide.with(this)
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_user)
                                .error(R.drawable.ic_user)
                                .into(profileButton)
                        } else {
                            // No profile image, show initial letter
                            setProfileInitial()
                        }
                    } else {
                        // Document doesn't exist, show initial letter
                        setProfileInitial()
                    }
                }
                .addOnFailureListener { e ->
                    // On error, show initial letter
                    setProfileInitial()
                }
        }
    }

    private fun setProfileInitial() {
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email

        val initial = when {
            !displayName.isNullOrEmpty() -> displayName.firstOrNull()?.uppercase() ?: "U"
            !email.isNullOrEmpty() -> email.firstOrNull()?.uppercase() ?: "U"
            else -> "U"
        }

        // Create a bitmap with the initial letter
        val size = 40 // Match the ImageView size in dp (converted to px)
        val sizePx = (size * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw circle background
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(this@DashboardActivity, R.color.green_primary)
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Draw initial text
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val textY = sizePx / 2f - textBounds.exactCenterY()

        canvas.drawText(initial, sizePx / 2f, textY, textPaint)

        // Set the bitmap to ImageView
        profileButton.setImageBitmap(bitmap)
    }

    private fun loadGroceryItems() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()
                showLoading(false)

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    allGroceryItems.clear()
                    allGroceryItems.addAll(items)

                    applyFilter()
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
        if (filteredGroceryItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            // Update empty message based on filter
            val message = when {
                selectedFilters.isEmpty() -> "No items added yet"
                selectedFilters.size == 1 -> {
                    when (selectedFilters.first()) {
                        "fresh" -> "No fresh items"
                        "expiring" -> "No items expiring soon"
                        "expired" -> "No expired items"
                        "used" -> "No used items"
                        else -> "No items found"
                    }
                }
                else -> "No items match selected filters"
            }
            tvEmptyMessage.text = message
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
        if (currentStatus == "used") return "used"

        return when {
            daysLeft < 0 -> "expired"
            daysLeft == 0 -> "expiring"
            daysLeft <= 2 -> "expiring"
            else -> "fresh"
        }
    }

    private fun clearUserLoggedInFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }
}