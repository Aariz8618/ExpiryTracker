package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class RecipeDetailActivity : AppCompatActivity() {
    private var isIngredientsExpanded = false
    private var isInstructionsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_recipe_detail)

        setupBackButton()
        loadRecipeData()
        setupExpandableCards()
        setupActionButtons()
        setupStartCookingButton()
    }

    private fun setupBackButton() {
        findViewById<MaterialButton>(R.id.button_back).setOnClickListener {
            finish()
        }
    }

    private fun loadRecipeData() {
        val title = intent.getStringExtra("title") ?: "Recipe"
        val servings = intent.getStringExtra("servings") ?: "4 servings"
        val prepTime = intent.getStringExtra("prepTime") ?: "25 min"
        val difficulty = intent.getStringExtra("difficulty") ?: "Easy"
        val ingredientsPreview = intent.getStringExtra("ingredientsPreview") ?: ""
        val instructionsPreview = intent.getStringExtra("instructionsPreview") ?: ""
        val ingredients = intent.getStringArrayListExtra("ingredients") ?: arrayListOf()
        val instructions = intent.getStringArrayListExtra("instructions") ?: arrayListOf()
        val times = intent.getStringArrayListExtra("times") ?: arrayListOf() // ⬅ step times
        val notes = intent.getStringExtra("notes") ?: ""

        findViewById<TextView>(R.id.text_recipe_title).text = title
        findViewById<TextView>(R.id.text_servings).text = servings
        findViewById<TextView>(R.id.text_ingredients_preview).text = ingredientsPreview
        findViewById<TextView>(R.id.text_instructions_preview).text = instructionsPreview

        populateIngredients(ingredients)
        populateInstructions(instructions)
    }

    private fun populateIngredients(ingredients: List<String>) {
        val ingredientsLayout = findViewById<LinearLayout>(R.id.layout_ingredients_full)
        ingredientsLayout.removeAllViews()

        ingredients.forEach { ingredient ->
            val textView = TextView(this).apply {
                text = "• $ingredient"
                textSize = 14f
            }
            ingredientsLayout.addView(textView)
        }
    }

    private fun populateInstructions(instructions: List<String>) {
        val instructionsLayout = findViewById<LinearLayout>(R.id.layout_instructions_full)
        instructionsLayout.removeAllViews()

        instructions.forEachIndexed { index, instruction ->
            val textView = TextView(this).apply {
                text = "${index + 1}. $instruction"
                textSize = 14f
            }
            instructionsLayout.addView(textView)
        }
    }

    private fun setupExpandableCards() {
        val ingredientsButton = findViewById<LinearLayout>(R.id.button_show_ingredients)
        val ingredientsButtonText = ingredientsButton.getChildAt(0) as TextView
        val ingredientsFull = findViewById<LinearLayout>(R.id.layout_ingredients_full)
        val ingredientsPreview = findViewById<TextView>(R.id.text_ingredients_preview)

        ingredientsButton.setOnClickListener {
            isIngredientsExpanded = !isIngredientsExpanded
            if (isIngredientsExpanded) {
                ingredientsFull.visibility = View.VISIBLE
                ingredientsPreview.visibility = View.GONE
                ingredientsButtonText.text = "Hide Ingredients"
            } else {
                ingredientsFull.visibility = View.GONE
                ingredientsPreview.visibility = View.VISIBLE
                ingredientsButtonText.text = "Show Full Ingredients"
            }
        }

        val instructionsButton = findViewById<LinearLayout>(R.id.button_show_instructions)
        val instructionsButtonText = instructionsButton.getChildAt(0) as TextView
        val instructionsFull = findViewById<LinearLayout>(R.id.layout_instructions_full)
        val instructionsPreview = findViewById<TextView>(R.id.text_instructions_preview)

        instructionsButton.setOnClickListener {
            isInstructionsExpanded = !isInstructionsExpanded
            if (isInstructionsExpanded) {
                instructionsFull.visibility = View.VISIBLE
                instructionsPreview.visibility = View.GONE
                instructionsButtonText.text = "Hide Instructions"
            } else {
                instructionsFull.visibility = View.GONE
                instructionsPreview.visibility = View.VISIBLE
                instructionsButtonText.text = "Show Full Instructions"
            }
        }
    }

    private fun setupActionButtons() {
        findViewById<LinearLayout>(R.id.button_save).setOnClickListener {
            Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.button_share).setOnClickListener {
            shareRecipe()
        }
    }

    private fun setupStartCookingButton() {
        findViewById<LinearLayout>(R.id.button_start_cooking).setOnClickListener {
            val instructions = intent.getStringArrayListExtra("instructions") ?: arrayListOf()
            val times = intent.getStringArrayListExtra("times") ?: arrayListOf()

            // If times are not provided, create default times
            val defaultTimes = if (times.isEmpty()) {
                ArrayList(instructions.map { "5 min" })
            } else {
                times
            }

            val intent = Intent(this, CookingModeActivity::class.java).apply {
                putStringArrayListExtra("instructions", instructions)
                putStringArrayListExtra("times", defaultTimes)
            }
            startActivity(intent)
        }
    }

    private fun shareRecipe() {
        val title = findViewById<TextView>(R.id.text_recipe_title).text.toString()
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "✨ Found something tasty! $title – give it a try\n" +
                    "Check it out on FreshTrack - https://bit.ly/4nTf0Oo")
            type = "text/plain"
        }


        startActivity(Intent.createChooser(shareIntent, "Share recipe via"))
    }
}
