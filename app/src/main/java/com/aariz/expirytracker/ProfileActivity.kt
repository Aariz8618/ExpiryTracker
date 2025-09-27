package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI components
    private lateinit var btnBack: MaterialButton
    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvPhoneNumber: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnPersonalization: LinearLayout
    private lateinit var llPersonalizationContent: LinearLayout
    private lateinit var btnLogout: LinearLayout

    // Personalization buttons
    private lateinit var btnVeg: MaterialButton
    private lateinit var btnVegan: MaterialButton
    private lateinit var btnNonVeg: MaterialButton
    private lateinit var btnCustom: MaterialButton

    // Category chips
    private lateinit var chipDairy: TextView
    private lateinit var chipSnacks: TextView
    private lateinit var chipBeverages: TextView
    private lateinit var chipFruits: TextView
    private lateinit var chipFrozen: TextView
    private lateinit var chipPantry: TextView
    private lateinit var chipVegetables: TextView
    private lateinit var chipMeat: TextView

    // Personalization data
    private var selectedDietaryPreference: String = ""
    private val selectedCategories = mutableSetOf<String>()

    // Activity Result Launcher for Edit Profile
    private val editProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh user data when returning from edit profile
            loadUserData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupClickListeners()
        loadUserData()
        setupPersonalizationListeners()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this activity
        loadUserData()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        tvAvatarInitial = findViewById(R.id.tv_avatar_initial)
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserEmail = findViewById(R.id.tv_user_email)
        tvPhoneNumber = findViewById(R.id.tv_phone_number)
        tvMemberSince = findViewById(R.id.tv_member_since)
        btnEditProfile = findViewById(R.id.btn_edit_profile)
        btnChangePassword = findViewById(R.id.btn_change_password)
        btnPersonalization = findViewById(R.id.btn_personalization)
        llPersonalizationContent = findViewById(R.id.ll_personalization_content)
        btnLogout = findViewById(R.id.btn_logout)

        // Initialize personalization buttons
        btnVeg = findViewById(R.id.btn_veg)
        btnVegan = findViewById(R.id.btn_vegan)
        btnNonVeg = findViewById(R.id.btn_non_veg)
        btnCustom = findViewById(R.id.btn_custom)

        // Initialize category chips
        chipDairy = findViewById(R.id.chip_dairy)
        chipSnacks = findViewById(R.id.chip_snacks)
        chipBeverages = findViewById(R.id.chip_beverages)
        chipFruits = findViewById(R.id.chip_fruits)
        chipFrozen = findViewById(R.id.chip_frozen)
        chipPantry = findViewById(R.id.chip_pantry)
        chipVegetables = findViewById(R.id.chip_vegetables)
        chipMeat = findViewById(R.id.chip_meat)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            editProfileLauncher.launch(intent)
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnPersonalization.setOnClickListener {
            // Toggle personalization content visibility
            val arrow = findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iv_personalization_arrow)

            if (llPersonalizationContent.visibility == android.view.View.VISIBLE) {
                llPersonalizationContent.visibility = android.view.View.GONE
                arrow.rotation = 0f
            } else {
                llPersonalizationContent.visibility = android.view.View.VISIBLE
                arrow.rotation = 90f
            }
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupPersonalizationListeners() {
        // Dietary preference buttons
        btnVeg.setOnClickListener { selectDietaryPreference("vegetarian", btnVeg) }
        btnVegan.setOnClickListener { selectDietaryPreference("vegan", btnVegan) }
        btnNonVeg.setOnClickListener { selectDietaryPreference("non_vegetarian", btnNonVeg) }
        btnCustom.setOnClickListener { selectDietaryPreference("custom", btnCustom) }

        // Category chips
        chipDairy.setOnClickListener { toggleCategory("dairy", chipDairy) }
        chipSnacks.setOnClickListener { toggleCategory("snacks", chipSnacks) }
        chipBeverages.setOnClickListener { toggleCategory("beverages", chipBeverages) }
        chipFruits.setOnClickListener { toggleCategory("fruits", chipFruits) }
        chipFrozen.setOnClickListener { toggleCategory("frozen", chipFrozen) }
        chipPantry.setOnClickListener { toggleCategory("pantry", chipPantry) }
        chipVegetables.setOnClickListener { toggleCategory("vegetables", chipVegetables) }
        chipMeat.setOnClickListener { toggleCategory("meat", chipMeat) }
    }

    private fun selectDietaryPreference(preference: String, button: MaterialButton) {
        // Reset all dietary buttons
        resetDietaryButtons()

        // Set selected preference
        selectedDietaryPreference = preference
        button.setBackgroundColor(getColor(R.color.green_primary))
        button.setTextColor(getColor(android.R.color.white))

        // Save to Firestore
        savePersonalizationData()
    }

    private fun resetDietaryButtons() {
        val buttons = listOf(btnVeg, btnVegan, btnNonVeg, btnCustom)
        buttons.forEach { button ->
            button.setBackgroundColor(getColor(android.R.color.white))
            button.setTextColor(getColor(R.color.gray_600))
        }
    }

    private fun toggleCategory(category: String, chip: TextView) {
        if (selectedCategories.contains(category)) {
            // Deselect
            selectedCategories.remove(category)
            chip.setBackgroundColor(getColor(android.R.color.white))
            chip.setTextColor(getColor(R.color.gray_600))
        } else {
            // Select
            selectedCategories.add(category)
            chip.setBackgroundColor(getColor(R.color.green_primary))
            chip.setTextColor(getColor(android.R.color.white))
        }

        // Save to Firestore
        savePersonalizationData()
    }

    private fun savePersonalizationData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val personalizationData = hashMapOf(
                "dietaryPreference" to selectedDietaryPreference,
                "favoriteCategories" to selectedCategories.toList(),
                "personalizedAt" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("users").document(currentUser.uid)
                .set(mapOf("personalization" to personalizationData), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    // Silent success - no toast for smooth UX
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save preferences: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Load user data from Firebase Auth
            val email = currentUser.email ?: "No email"
            val displayName = currentUser.displayName ?: email.substringBefore("@")
            val creationTime = currentUser.metadata?.creationTimestamp

            // Set user name and initial from Firebase Auth
            tvUserName.text = displayName
            tvAvatarInitial.text = displayName.firstOrNull()?.uppercase() ?: "U"

            // Set email from Firebase Auth
            tvUserEmail.text = email

            // Set member since date from Firebase metadata
            if (creationTime != null) {
                val date = Date(creationTime)
                val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                tvMemberSince.text = formatter.format(date)
            } else {
                tvMemberSince.text = "Recently joined"
            }

            // Load additional data from Firestore
            loadFirestoreData(currentUser.uid)

        } else {
            // No authenticated user - redirect to login
            Toast.makeText(this, "Please login to view profile", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadFirestoreData(userId: String) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Load phone number from Firestore
                    val phone = document.getString("phone")
                    if (!phone.isNullOrEmpty()) {
                        tvPhoneNumber.text = phone
                    } else {
                        tvPhoneNumber.text = "Not provided"
                    }

                    // Load name from Firestore if available (it might be more up-to-date)
                    val firestoreName = document.getString("name")
                    if (!firestoreName.isNullOrEmpty()) {
                        tvUserName.text = firestoreName
                        tvAvatarInitial.text = firestoreName.firstOrNull()?.uppercase() ?: "U"
                    }

                    // Load personalization data
                    loadPersonalizationData(document.data)
                } else {
                    tvPhoneNumber.text = "Not provided"
                }
            }
            .addOnFailureListener { e ->
                tvPhoneNumber.text = "Error loading data"
                Toast.makeText(this, "Error loading profile data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPersonalizationData(data: Map<String, Any>?) {
        if (data != null && data.containsKey("personalization")) {
            val personalization = data["personalization"] as? Map<String, Any>
            if (personalization != null) {
                // Load dietary preference
                selectedDietaryPreference = personalization["dietaryPreference"] as? String ?: ""
                updateDietaryPreferenceUI()

                // Load favorite categories
                val categories = personalization["favoriteCategories"] as? List<String>
                if (categories != null) {
                    selectedCategories.clear()
                    selectedCategories.addAll(categories)
                    updateCategoriesUI()
                }
            }
        }
    }

    private fun updateDietaryPreferenceUI() {
        resetDietaryButtons()
        when (selectedDietaryPreference) {
            "vegetarian" -> {
                btnVeg.setBackgroundColor(getColor(R.color.green_primary))
                btnVeg.setTextColor(getColor(android.R.color.white))
            }
            "vegan" -> {
                btnVegan.setBackgroundColor(getColor(R.color.green_primary))
                btnVegan.setTextColor(getColor(android.R.color.white))
            }
            "non_vegetarian" -> {
                btnNonVeg.setBackgroundColor(getColor(R.color.green_primary))
                btnNonVeg.setTextColor(getColor(android.R.color.white))
            }
            "custom" -> {
                btnCustom.setBackgroundColor(getColor(R.color.green_primary))
                btnCustom.setTextColor(getColor(android.R.color.white))
            }
        }
    }

    private fun updateCategoriesUI() {
        val chips = mapOf(
            "dairy" to chipDairy,
            "snacks" to chipSnacks,
            "beverages" to chipBeverages,
            "fruits" to chipFruits,
            "frozen" to chipFrozen,
            "pantry" to chipPantry,
            "vegetables" to chipVegetables,
            "meat" to chipMeat
        )

        chips.forEach { (category, chip) ->
            if (selectedCategories.contains(category)) {
                chip.setBackgroundColor(getColor(R.color.green_primary))
                chip.setTextColor(getColor(android.R.color.white))
            } else {
                chip.setBackgroundColor(getColor(R.color.gray_200))
                chip.setTextColor(getColor(R.color.gray_600))
            }
        }
    }

    private fun showChangePasswordDialog() {
        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setMessage("You will receive an email with instructions to reset your password.")
            .setPositiveButton("Send Email") { _, _ ->
                sendPasswordResetEmail()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendPasswordResetEmail() {
        val currentUser = auth.currentUser
        val email = currentUser?.email

        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "No email address found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performLogout() {
        try {
            // Clear the logged-in flag FIRST before signing out
            clearUserLoggedInFlag()

            // Sign out from Firebase
            auth.signOut()

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            // Navigate back to login screen and clear the entire task stack
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clear the flag that tracks if user has successfully logged in through our app
     */
    private fun clearUserLoggedInFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }
}