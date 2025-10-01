package com.aariz.expirytracker.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class for Recipe API response
 */
data class RecipeResponse(
    @SerializedName("title")
    val title: String = "",

    @SerializedName("ingredients")
    val ingredients: String = "",

    @SerializedName("servings")
    val servings: String = "",

    @SerializedName("instructions")
    val instructions: String = ""
)

/**
 * Domain model for Recipe (used in UI)
 */
data class Recipe(
    val title: String,
    val ingredients: List<String>,
    val servings: String,
    val instructions: List<String>,
    val ingredientsRaw: String = "",
    val instructionsRaw: String = ""
) {
    companion object {
        /**
         * Convert API response to domain model
         */
        fun fromResponse(response: RecipeResponse): Recipe {
            return Recipe(
                title = response.title,
                ingredients = parseIngredients(response.ingredients),
                servings = response.servings.ifEmpty { "Not specified" },
                instructions = parseInstructions(response.instructions),
                ingredientsRaw = response.ingredients,
                instructionsRaw = response.instructions
            )
        }

        private fun parseIngredients(ingredientsString: String): List<String> {
            return ingredientsString.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        private fun parseInstructions(instructionsString: String): List<String> {
            // Try to split by periods for steps
            val steps = instructionsString.split(".")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length > 10 }

            return if (steps.isNotEmpty()) {
                steps
            } else {
                listOf(instructionsString)
            }
        }
    }

    fun getIngredientsPreview(): String {
        return ingredients.take(3).joinToString(", ") +
                if (ingredients.size > 3) "..." else ""
    }

    fun getInstructionsPreview(): String {
        return if (instructions.isNotEmpty()) {
            instructions[0].take(100) + if (instructions[0].length > 100) "..." else ""
        } else {
            "See full instructions"
        }
    }
}