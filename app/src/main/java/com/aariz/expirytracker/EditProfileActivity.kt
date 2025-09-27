package com.aariz.expirytracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI components
    private lateinit var btnBack: MaterialButton
    private lateinit var tvAvatarInitial: TextView
    private lateinit var btnChangePhoto: TextView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Original data for comparison
    private var originalName: String = ""
    private var originalEmail: String = ""
    private var originalPhone: String = ""

    // Email verification state
    private var emailVerificationSent: Boolean = false
    private var newEmailPending: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_edit_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupClickListeners()
        loadUserData()

        // Listen for auth state changes (for email verification)
        setupAuthStateListener()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        tvAvatarInitial = findViewById(R.id.tv_avatar_initial)
        btnChangePhoto = findViewById(R.id.btn_change_photo)
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnChangePhoto.setOnClickListener {
            // TODO: Implement photo change functionality
            Toast.makeText(this, "Photo change functionality coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupAuthStateListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && emailVerificationSent && newEmailPending.isNotEmpty()) {
                // Reload user to get updated verification status
                user.reload().addOnCompleteListener { reloadTask ->
                    if (reloadTask.isSuccessful && user.isEmailVerified) {
                        // Email is now verified, proceed with profile update
                        Toast.makeText(this, "Email verified! Updating profile...", Toast.LENGTH_SHORT).show()
                        completeProfileUpdate()
                    }
                }
            }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Load data from Firebase Auth
            originalEmail = currentUser.email ?: ""
            originalName = currentUser.displayName ?: ""

            etEmail.setText(originalEmail)
            etName.setText(originalName)

            // Update avatar initial
            updateAvatarInitial(originalName)

            // Load additional data from Firestore
            loadFirestoreData(currentUser.uid)
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadFirestoreData(userId: String) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    originalPhone = document.getString("phone") ?: ""
                    etPhone.setText(originalPhone)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAvatarInitial(name: String) {
        val initial = if (name.isNotEmpty()) {
            name.first().uppercase()
        } else {
            "U"
        }
        tvAvatarInitial.text = initial
    }

    private fun saveProfile() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            etName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Check if email is changing
            if (email != originalEmail) {
                showEmailChangeConfirmation(name, email, phone)
            } else {
                // Email not changing, proceed with normal update
                updateProfile(name, email, phone)
            }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmailChangeConfirmation(name: String, email: String, phone: String) {
        AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage("You're changing your email address to: $email\n\nA verification email will be sent to this new address. You must verify it before the changes can be saved.\n\nProceed?")
            .setPositiveButton("Send Verification") { _, _ ->
                sendEmailVerification(name, email, phone)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendEmailVerification(name: String, email: String, phone: String) {
        // Show loading state
        btnSave.text = "Sending verification..."
        btnSave.isEnabled = false

        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Store pending data
            newEmailPending = email

            // Update email in Firebase Auth (this will automatically send verification email)
            currentUser.updateEmail(email)
                .addOnCompleteListener { emailTask ->
                    if (emailTask.isSuccessful) {
                        // Send verification email
                        currentUser.sendEmailVerification()
                            .addOnCompleteListener { verificationTask ->
                                if (verificationTask.isSuccessful) {
                                    emailVerificationSent = true
                                    showVerificationPendingDialog(name, email, phone)
                                } else {
                                    Toast.makeText(this, "Failed to send verification email: ${verificationTask.exception?.message}", Toast.LENGTH_LONG).show()
                                    resetSaveButton()
                                }
                            }
                    } else {
                        Toast.makeText(this, "Failed to update email: ${emailTask.exception?.message}", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                }
        }
    }

    private fun showVerificationPendingDialog(name: String, email: String, phone: String) {
        AlertDialog.Builder(this)
            .setTitle("Verification Email Sent")
            .setMessage("A verification email has been sent to: $email\n\nPlease check your email and click the verification link. Once verified, return to this screen and tap 'Save Changes' again.")
            .setPositiveButton("I've Verified") { _, _ ->
                checkEmailVerificationAndSave(name, email, phone)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset email to original
                etEmail.setText(originalEmail)
                newEmailPending = ""
                emailVerificationSent = false
                resetSaveButton()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkEmailVerificationAndSave(name: String, email: String, phone: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Show loading
            btnSave.text = "Verifying..."
            btnSave.isEnabled = false

            // Reload user to get updated verification status
            currentUser.reload().addOnCompleteListener { reloadTask ->
                if (reloadTask.isSuccessful) {
                    if (currentUser.isEmailVerified) {
                        // Email is verified, proceed with update
                        completeProfileUpdate(name, email, phone)
                    } else {
                        // Email not yet verified
                        AlertDialog.Builder(this)
                            .setTitle("Email Not Verified")
                            .setMessage("Your email address has not been verified yet. Please check your email and click the verification link, then try again.")
                            .setPositiveButton("Try Again") { _, _ ->
                                checkEmailVerificationAndSave(name, email, phone)
                            }
                            .setNegativeButton("Cancel") { _, _ ->
                                resetSaveButton()
                            }
                            .show()
                    }
                } else {
                    Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
            }
        }
    }

    private fun completeProfileUpdate(name: String = "", email: String = "", phone: String = "") {
        val finalName = if (name.isEmpty()) etName.text.toString().trim() else name
        val finalEmail = if (email.isEmpty()) etEmail.text.toString().trim() else email
        val finalPhone = if (phone.isEmpty()) etPhone.text.toString().trim() else phone

        updateProfile(finalName, finalEmail, finalPhone)
    }

    private fun updateProfile(name: String, email: String, phone: String) {
        // Show loading state
        btnSave.text = "Saving..."
        btnSave.isEnabled = false

        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Update display name in Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()

            currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener { profileTask ->
                    if (profileTask.isSuccessful) {
                        // Update Firestore data
                        updateFirestoreProfile(currentUser.uid, name, email, phone)
                    } else {
                        Toast.makeText(this, "Failed to update profile: ${profileTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        resetSaveButton()
                    }
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            resetSaveButton()
        }
    }

    private fun updateFirestoreProfile(userId: String, name: String, email: String, phone: String) {
        val userProfile = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("users").document(userId)
            .set(userProfile, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                // Update original values
                originalName = name
                originalEmail = email
                originalPhone = phone

                // Reset verification states
                emailVerificationSent = false
                newEmailPending = ""

                // Update avatar
                updateAvatarInitial(name)

                resetSaveButton()

                // Set result and finish
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
    }

    private fun resetSaveButton() {
        btnSave.text = "Save Changes"
        btnSave.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any pending verification states if user leaves
        emailVerificationSent = false
        newEmailPending = ""
    }
}