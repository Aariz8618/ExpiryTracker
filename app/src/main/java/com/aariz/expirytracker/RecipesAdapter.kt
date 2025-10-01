package com.aariz.expirytracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aariz.expirytracker.data.model.Recipe
import com.google.android.material.card.MaterialCardView

class RecipesAdapter(
    private val recipes: List<Recipe>,
    private val onRecipeClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipesAdapter.RecipeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(recipes[position])
    }

    override fun getItemCount(): Int = recipes.size

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_recipe)
        private val titleText: TextView = itemView.findViewById(R.id.text_recipe_title)
        private val servingsText: TextView = itemView.findViewById(R.id.text_servings)
        private val ingredientsPreview: TextView = itemView.findViewById(R.id.text_ingredients_preview)

        fun bind(recipe: Recipe) {
            titleText.text = recipe.title
            servingsText.text = "Servings: ${recipe.servings}"
            ingredientsPreview.text = recipe.getIngredientsPreview()

            cardView.setOnClickListener {
                onRecipeClick(recipe)
            }
        }
    }
}