package com.aariz.expirytracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI components
    private lateinit var btnBack: MaterialButton
    private lateinit var tvAvatarInitial: TextView
    private lateinit var profileImageView: ImageView
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
    private var currentProfileImageUrl: String = ""

    // Email verification state
    private var emailVerificationSent: Boolean = false
    private var newEmailPending: String = ""

    // Image handling
    private var selectedImageUri: Uri? = null
    private var currentPhotoPath: String = ""

    // Permission launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val file = File(currentPhotoPath)
            if (file.exists()) {
                selectedImageUri = Uri.fromFile(file)
                displaySelectedImage(selectedImageUri!!)
            }
        }
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                displaySelectedImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_edit_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Cloudinary
        CloudinaryManager.init(this)

        initViews()
        setupClickListeners()
        loadUserData()
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
        profileImageView = findViewById(R.id.profile_image_view)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")

        AlertDialog.Builder(this)
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> checkStoragePermissionAndOpen()
                    2 -> removeProfilePicture()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkStoragePermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                else -> {
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            }
            cameraLauncher.launch(intent)
        } catch (e: IOException) {
            Toast.makeText(this, "Error creating image file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun createImageFile(): File {
        val timeStamp = System.currentTimeMillis()
        val storageDir = externalCacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun displaySelectedImage(uri: Uri) {
        tvAvatarInitial.visibility = TextView.GONE
        profileImageView.visibility = ImageView.VISIBLE
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(profileImageView)
    }

    private fun removeProfilePicture() {
        selectedImageUri = null
        currentProfileImageUrl = ""
        tvAvatarInitial.visibility = TextView.VISIBLE
        profileImageView.visibility = ImageView.GONE
        Toast.makeText(this, "Profile picture will be removed", Toast.LENGTH_SHORT).show()
    }

    private fun setupAuthStateListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && emailVerificationSent && newEmailPending.isNotEmpty()) {
                user.reload().addOnCompleteListener { reloadTask ->
                    if (reloadTask.isSuccessful && user.isEmailVerified) {
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
            originalEmail = currentUser.email ?: ""
            originalName = currentUser.displayName ?: ""

            etEmail.setText(originalEmail)
            etName.setText(originalName)
            updateAvatarInitial(originalName)

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

                    currentProfileImageUrl = document.getString("profileImageUrl") ?: ""
                    if (currentProfileImageUrl.isNotEmpty()) {
                        tvAvatarInitial.visibility = TextView.GONE
                        profileImageView.visibility = ImageView.VISIBLE
                        Glide.with(this)
                            .load(currentProfileImageUrl)
                            .circleCrop()
                            .into(profileImageView)
                    }
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
            if (selectedImageUri != null) {
                uploadProfileImage(name, email, phone)
            } else if (email != originalEmail) {
                showEmailChangeConfirmation(name, email, phone)
            } else {
                updateProfile(name, email, phone, currentProfileImageUrl)
            }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadProfileImage(name: String, email: String, phone: String) {
        btnSave.text = "Uploading image..."
        btnSave.isEnabled = false

        CloudinaryManager.uploadImage(
            context = this,
            imageUri = selectedImageUri!!,
            onSuccess = { imageUrl ->
                runOnUiThread {
                    if (email != originalEmail) {
                        showEmailChangeConfirmation(name, email, phone, imageUrl)
                    } else {
                        updateProfile(name, email, phone, imageUrl)
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to upload image: $error", Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
            }
        )
    }

    private fun showEmailChangeConfirmation(name: String, email: String, phone: String, imageUrl: String = currentProfileImageUrl) {
        AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage("You're changing your email address to: $email\n\nA verification email will be sent to this new address. You must verify it before the changes can be saved.\n\nProceed?")
            .setPositiveButton("Send Verification") { _, _ ->
                sendEmailVerification(name, email, phone, imageUrl)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                resetSaveButton()
            }
            .show()
    }

    private fun sendEmailVerification(name: String, email: String, phone: String, imageUrl: String) {
        btnSave.text = "Sending verification..."
        btnSave.isEnabled = false

        val currentUser = auth.currentUser
        if (currentUser != null) {
            newEmailPending = email

            currentUser.verifyBeforeUpdateEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        emailVerificationSent = true
                        showVerificationPendingDialog(name, email, phone, imageUrl)
                    } else {
                        Toast.makeText(this, "Failed to send verification: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                }
        }
    }

    private fun showVerificationPendingDialog(name: String, email: String, phone: String, imageUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Verification Email Sent")
            .setMessage("A verification email has been sent to: $email\n\nPlease check your email and click the verification link. Once verified, return to this screen and tap 'Save Changes' again.")
            .setPositiveButton("I've Verified") { _, _ ->
                checkEmailVerificationAndSave(name, email, phone, imageUrl)
            }
            .setNegativeButton("Cancel") { _, _ ->
                etEmail.setText(originalEmail)
                newEmailPending = ""
                emailVerificationSent = false
                resetSaveButton()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkEmailVerificationAndSave(name: String, email: String, phone: String, imageUrl: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            btnSave.text = "Verifying..."
            btnSave.isEnabled = false

            currentUser.reload().addOnCompleteListener { reloadTask ->
                if (reloadTask.isSuccessful) {
                    if (currentUser.isEmailVerified) {
                        completeProfileUpdate(name, email, phone, imageUrl)
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Email Not Verified")
                            .setMessage("Your email address has not been verified yet. Please check your email and click the verification link, then try again.")
                            .setPositiveButton("Try Again") { _, _ ->
                                checkEmailVerificationAndSave(name, email, phone, imageUrl)
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

    private fun completeProfileUpdate(name: String = "", email: String = "", phone: String = "", imageUrl: String = currentProfileImageUrl) {
        val finalName = if (name.isEmpty()) etName.text.toString().trim() else name
        val finalEmail = if (email.isEmpty()) etEmail.text.toString().trim() else email
        val finalPhone = if (phone.isEmpty()) etPhone.text.toString().trim() else phone

        updateProfile(finalName, finalEmail, finalPhone, imageUrl)
    }

    private fun updateProfile(name: String, email: String, phone: String, imageUrl: String) {
        btnSave.text = "Saving..."
        btnSave.isEnabled = false

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .apply {
                    if (imageUrl.isNotEmpty()) {
                        setPhotoUri(Uri.parse(imageUrl))
                    }
                }
                .build()

            currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener { profileTask ->
                    if (profileTask.isSuccessful) {
                        updateFirestoreProfile(currentUser.uid, name, email, phone, imageUrl)
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

    private fun updateFirestoreProfile(userId: String, name: String, email: String, phone: String, imageUrl: String) {
        val userProfile = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "profileImageUrl" to imageUrl,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("users").document(userId)
            .set(userProfile, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                originalName = name
                originalEmail = email
                originalPhone = phone
                currentProfileImageUrl = imageUrl

                emailVerificationSent = false
                newEmailPending = ""

                updateAvatarInitial(name)
                resetSaveButton()

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
        emailVerificationSent = false
        newEmailPending = ""
    }
}