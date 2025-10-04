package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
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
    private lateinit var tvName: TextView
    private lateinit var tvPhoneNumber: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnLogout: LinearLayout
    private lateinit var profileImageView: ImageView



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
        tvName = findViewById(R.id.tv_name)
        tvPhoneNumber = findViewById(R.id.tv_phone_number)
        tvMemberSince = findViewById(R.id.tv_member_since)
        btnEditProfile = findViewById(R.id.btn_edit_profile)
        btnChangePassword = findViewById(R.id.btn_change_password)
        btnLogout = findViewById(R.id.btn_logout)
        profileImageView = findViewById(R.id.profile_image_view)
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

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
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
            tvName.text = displayName
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

                    // Load name from Firestore if available
                    val firestoreName = document.getString("name")
                    if (!firestoreName.isNullOrEmpty()) {
                        tvUserName.text = firestoreName
                        tvName.text = firestoreName
                        tvAvatarInitial.text = firestoreName.firstOrNull()?.uppercase() ?: "U"
                    }

                    // Load profile image URL
                    val profileImageUrl = document.getString("profileImageUrl")
                    if (!profileImageUrl.isNullOrEmpty()) {
                        tvAvatarInitial.visibility = TextView.GONE
                        profileImageView.visibility = ImageView.VISIBLE
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(profileImageView)
                    } else {
                        tvAvatarInitial.visibility = TextView.VISIBLE
                        profileImageView.visibility = ImageView.GONE
                    }
                } else {
                    tvPhoneNumber.text = "Not provided"
                }
            }
            .addOnFailureListener { e ->
                tvPhoneNumber.text = "Error loading data"
                Toast.makeText(this, "Error loading profile data: ${e.message}", Toast.LENGTH_SHORT).show()
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