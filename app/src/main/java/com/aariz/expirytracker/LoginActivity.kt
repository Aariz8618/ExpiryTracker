package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_login)

        auth = FirebaseAuth.getInstance()

        val emailField = findViewById<EditText>(R.id.input_email)
        val passwordField = findViewById<EditText>(R.id.input_password)
        val loginButton = findViewById<LinearLayout>(R.id.button_login)
        val loginButtonText = findViewById<TextView>(R.id.text_login)
        val signupRedirect = findViewById<LinearLayout>(R.id.button_signup_redirect)
        val forgotPassword = findViewById<TextView>(R.id.text_forgot_password)
        val resendVerification = findViewById<TextView>(R.id.text_resend_verification)
        val continueGuest = findViewById<TextView>(R.id.text_continue_guest)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.toggle_password_visibility)

        // Toggle password visibility
        togglePasswordVisibility.setOnClickListener {
            if (isPasswordVisible) {
                // Hide password
                passwordField.transformationMethod = PasswordTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_off)
                isPasswordVisible = false
            } else {
                // Show password
                passwordField.transformationMethod = HideReturnsTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_on)
                isPasswordVisible = true
            }
            // Move cursor to end of text
            passwordField.setSelection(passwordField.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button to prevent multiple clicks
            loginButton.isEnabled = false
            loginButtonText.text = "Signing In..."

            signInWithEmail(email, password)
        }

        signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }

        forgotPassword.setOnClickListener {
            val email = emailField.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendPasswordResetEmail(email)
        }

        resendVerification.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            resendEmailVerification(email, password)
        }

        continueGuest.setOnClickListener {
            // Navigate to dashboard without authentication
            Toast.makeText(this, "Continuing as guest", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }

    private fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginAuth", "signInWithEmail:success")
                    val user = auth.currentUser

                    // Check if email is verified
                    if (user?.isEmailVerified == true) {
                        // Mark that user has successfully logged in through our app
                        markUserLoggedIn()

                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        // Navigate to main dashboard
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    } else {
                        // Email not verified
                        Toast.makeText(
                            this,
                            "Please verify your email first. Check your inbox for verification link.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Sign out the unverified user
                        auth.signOut()

                        // Show resend verification option
                        findViewById<TextView>(R.id.text_resend_verification).visibility = TextView.VISIBLE
                    }
                } else {
                    Log.w("LoginAuth", "signInWithEmail:failure", task.exception)
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ||
                                task.exception?.message?.contains("user not found") == true ->
                            "No account found with this email. Please sign up first."
                        task.exception?.message?.contains("password is invalid") == true ||
                                task.exception?.message?.contains("wrong password") == true ->
                            "Incorrect password. Please try again."
                        task.exception?.message?.contains("email address is badly formatted") == true ->
                            "Please enter a valid email address"
                        task.exception?.message?.contains("too many requests") == true ->
                            "Too many failed attempts. Please try again later."
                        else -> "Login failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }

                // Re-enable login button
                findViewById<LinearLayout>(R.id.button_login).isEnabled = true
                findViewById<TextView>(R.id.text_login).text = "Login"
            }
    }

    private fun markUserLoggedIn() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", true).apply()
        Log.d("LoginAuth", "Marked user as having logged in successfully")
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun resendEmailVerification(email: String, password: String) {
        // First sign in to get the user object
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { emailTask ->
                            if (emailTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Verification email resent to $email",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to resend verification email",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            // Sign out again since they're not verified
                            auth.signOut()
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Please check your email and password",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}