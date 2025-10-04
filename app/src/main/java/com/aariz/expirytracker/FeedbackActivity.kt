package com.aariz.expirytracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class FeedbackActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI components
    private lateinit var buttonBack: MaterialButton
    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView
    private lateinit var ratingLabel: TextView
    private lateinit var radioGroupCategory: RadioGroup
    private lateinit var radioBug: RadioButton
    private lateinit var radioSuggestion: RadioButton
    private lateinit var radioFeedback: RadioButton
    private lateinit var inputMessage: EditText
    private lateinit var inputEmail: EditText
    private lateinit var buttonAttachScreenshot: LinearLayout
    private lateinit var screenshotPreview: ImageView
    private lateinit var buttonRemoveScreenshot: LinearLayout
    private lateinit var buttonSubmit: LinearLayout
    private lateinit var loadingOverlay: FrameLayout

    private var rating = 0
    private var screenshotUri: Uri? = null
    private val stars = mutableListOf<ImageView>()

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                screenshotUri = uri
                showScreenshotPreview(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_feedback)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Cloudinary
        CloudinaryManager.init(this)

        initViews()
        setupClickListeners()
        loadUserEmail()
    }

    private fun initViews() {
        buttonBack = findViewById(R.id.button_back)
        star1 = findViewById(R.id.star_1)
        star2 = findViewById(R.id.star_2)
        star3 = findViewById(R.id.star_3)
        star4 = findViewById(R.id.star_4)
        star5 = findViewById(R.id.star_5)
        ratingLabel = findViewById(R.id.rating_label)
        radioGroupCategory = findViewById(R.id.radio_group_category)
        radioBug = findViewById(R.id.radio_bug)
        radioSuggestion = findViewById(R.id.radio_suggestion)
        radioFeedback = findViewById(R.id.radio_feedback)
        inputMessage = findViewById(R.id.input_message)
        inputEmail = findViewById(R.id.input_email)
        buttonAttachScreenshot = findViewById(R.id.button_attach_screenshot)
        screenshotPreview = findViewById(R.id.screenshot_preview)
        buttonRemoveScreenshot = findViewById(R.id.button_remove_screenshot)
        buttonSubmit = findViewById(R.id.button_submit)
        loadingOverlay = findViewById(R.id.loading_overlay)

        // Add stars to list for easier management
        stars.addAll(listOf(star1, star2, star3, star4, star5))
    }

    private fun setupClickListeners() {
        buttonBack.setOnClickListener {
            finish()
        }

        // Setup star rating
        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                setRating(index + 1)
            }
        }

        buttonAttachScreenshot.setOnClickListener {
            openImagePicker()
        }

        buttonRemoveScreenshot.setOnClickListener {
            removeScreenshot()
        }

        buttonSubmit.setOnClickListener {
            submitFeedback()
        }
    }

    private fun loadUserEmail() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: ""
            inputEmail.setText(email)
        } else {
            inputEmail.hint = "Not logged in"
        }
    }

    private fun setRating(value: Int) {
        rating = value
        updateStarDisplay()
        updateRatingLabel()
    }

    private fun updateStarDisplay() {
        stars.forEachIndexed { index, star ->
            if (index < rating) {
                star.setImageResource(R.drawable.ic_star_filled)
                star.imageTintList = getColorStateList(R.color.yellow_500)
            } else {
                star.setImageResource(R.drawable.ic_star_outline)
                star.imageTintList = getColorStateList(R.color.gray_400)
            }
        }
    }

    private fun updateRatingLabel() {
        ratingLabel.text = when (rating) {
            1 -> "Poor"
            2 -> "Fair"
            3 -> "Good"
            4 -> "Very Good"
            5 -> "Excellent"
            else -> "Tap to rate"
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun showScreenshotPreview(uri: Uri) {
        screenshotPreview.setImageURI(uri)
        screenshotPreview.visibility = View.VISIBLE
        buttonRemoveScreenshot.visibility = View.VISIBLE
    }

    private fun removeScreenshot() {
        screenshotUri = null
        screenshotPreview.setImageURI(null)
        screenshotPreview.visibility = View.GONE
        buttonRemoveScreenshot.visibility = View.GONE
    }

    private fun submitFeedback() {
        // Validate inputs
        if (rating == 0) {
            Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategoryId = radioGroupCategory.checkedRadioButtonId
        if (selectedCategoryId == -1) {
            Toast.makeText(this, "Please select a feedback type", Toast.LENGTH_SHORT).show()
            return
        }

        val message = inputMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter your message", Toast.LENGTH_SHORT).show()
            return
        }

        // Get category
        val category = when (selectedCategoryId) {
            R.id.radio_bug -> "Bug Report"
            R.id.radio_suggestion -> "Suggestion"
            R.id.radio_feedback -> "General Feedback"
            else -> "General Feedback"
        }

        // Show loading
        loadingOverlay.visibility = View.VISIBLE

        // If there's a screenshot, upload it first
        if (screenshotUri != null) {
            uploadScreenshotToCloudinary(category, message)
        } else {
            saveFeedbackToFirestore(category, message, null)
        }
    }

    private fun uploadScreenshotToCloudinary(category: String, message: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            loadingOverlay.visibility = View.GONE
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        screenshotUri?.let { uri ->
            CloudinaryManager.uploadFeedbackScreenshot(
                context = this,
                imageUri = uri,
                onSuccess = { imageUrl ->
                    runOnUiThread {
                        saveFeedbackToFirestore(category, message, imageUrl)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Error uploading screenshot: $error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    private fun saveFeedbackToFirestore(category: String, message: String, screenshotUrl: String?) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            loadingOverlay.visibility = View.GONE
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(timestamp))

        val feedbackData = hashMapOf(
            "userId" to currentUser.uid,
            "userEmail" to (currentUser.email ?: ""),
            "userName" to (currentUser.displayName ?: "Anonymous"),
            "rating" to rating,
            "category" to category,
            "message" to message,
            "screenshotUrl" to (screenshotUrl ?: ""),
            "timestamp" to timestamp,
            "dateFormatted" to formattedDate,
            "status" to "pending"
        )

        firestore.collection("feedback")
            .add(feedbackData)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Error submitting feedback: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Thank You!")
            .setMessage("Your feedback has been submitted successfully. We appreciate your input and will review it shortly.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}