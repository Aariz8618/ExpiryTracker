package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_signup)

        auth = FirebaseAuth.getInstance()

        val emailField = findViewById<EditText>(R.id.input_email)
        val passwordField = findViewById<EditText>(R.id.input_password)
        val confirmPasswordField = findViewById<EditText>(R.id.input_confirm_password)
        val createAccountButton = findViewById<LinearLayout>(R.id.button_create_account)
        val loginRedirect = findViewById<LinearLayout>(R.id.button_login_redirect)

        // Password visibility toggles
        val togglePasswordVisibility = findViewById<ImageView>(R.id.toggle_password_visibility)
        val toggleConfirmPasswordVisibility = findViewById<ImageView>(R.id.toggle_confirm_password_visibility)

        // Setup password visibility toggles
        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_on)
            } else {
                passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_off)
            }
            passwordField.setSelection(passwordField.text.length)
        }

        toggleConfirmPasswordVisibility.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                confirmPasswordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_eye_on)
            } else {
                confirmPasswordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_eye_off)
            }
            confirmPasswordField.setSelection(confirmPasswordField.text.length)
        }

        createAccountButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val confirmPassword = confirmPasswordField.text.toString().trim()

            // Validation
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show pre-signup guidance about email verification
            showEmailGuidanceDialog(email, password)
        }

        loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showEmailGuidanceDialog(email: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage(
                """Before creating your account, please note:

• A verification email will be sent to: $email
• Check your SPAM/Junk folder if not in inbox
• Gmail users: Also check Promotions tab
• The email will come from: support@freshtrack-d3269.firebaseapp.com

The verification email may take 2-5 minutes to arrive."""
            )
            .setPositiveButton("Continue & Create Account") { _, _ ->
                proceedWithAccountCreation(email, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun proceedWithAccountCreation(email: String, password: String) {
        // Disable button to prevent multiple clicks
        findViewById<LinearLayout>(R.id.button_create_account).apply {
            isEnabled = false
            isClickable = false
            alpha = 0.6f
        }

        createAccountWithEmail(email, password)
    }

    private fun createAccountWithEmail(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SignupAuth", "createUserWithEmail:success")
                    val user = auth.currentUser

                    if (user != null) {
                        sendVerificationEmail(user, email)
                    } else {
                        showError("Account creation failed. Please try again.")
                    }
                } else {
                    Log.w("SignupAuth", "createUserWithEmail:failure", task.exception)
                    val errorMessage = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "This email is already registered. Please login instead."
                        task.exception?.message?.contains("email address is badly formatted") == true ->
                            "Please enter a valid email address"
                        task.exception?.message?.contains("weak password") == true ->
                            "Password is too weak. Please use a stronger password"
                        task.exception?.message?.contains("network error") == true ->
                            "Network error. Please check your internet connection."
                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    showError(errorMessage)
                }
            }
    }

    private fun sendVerificationEmail(user: com.google.firebase.auth.FirebaseUser, email: String) {
        user.sendEmailVerification()
            .addOnCompleteListener { emailTask ->
                if (emailTask.isSuccessful) {
                    Log.d("SignupAuth", "Email verification sent successfully")

                    // Show success dialog with detailed instructions
                    showEmailSentDialog(email)

                    // Sign out user until they verify their email
                    auth.signOut()

                } else {
                    Log.w("SignupAuth", "sendEmailVerification failed", emailTask.exception)

                    AlertDialog.Builder(this)
                        .setTitle("Account Created")
                        .setMessage("Your account was created but the verification email failed to send. You can try resending it from the login screen.")
                        .setPositiveButton("Go to Login") { _, _ ->
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
    }

    private fun showEmailSentDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Account Created Successfully!")
            .setMessage(
                """Verification email sent to:
$email

WHERE TO LOOK:
• Check your Inbox first
• Then check SPAM/Junk folder  
• Gmail: Check Promotions tab or Spam Folder
• Outlook: Check Junk folder
• Yahoo: Check Spam folder

TIMING:
Email may take 2-5 minutes to arrive

SENDER:
From: support@freshtrack-d3269.firebaseapp.com """
            )
            .setPositiveButton("Got It!") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Re-enable button
        findViewById<LinearLayout>(R.id.button_create_account).apply {
            isEnabled = true
            isClickable = true
            alpha = 1f
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}