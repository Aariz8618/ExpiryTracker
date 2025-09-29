package com.aariz.expirytracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onItemClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.GroceryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroceryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grocery, parent, false)
        return GroceryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroceryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class GroceryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_grocery_item)
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val category: TextView = itemView.findViewById(R.id.tv_category)
        private val expiryDate: TextView = itemView.findViewById(R.id.tv_expiry_date)
        private val quantity: TextView = itemView.findViewById(R.id.tv_quantity)
        private val statusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)
        private val statusIndicator: View = itemView.findViewById(R.id.status_indicator)
        private val itemIcon: ImageView = itemView.findViewById(R.id.iv_item_icon)

        fun bind(item: GroceryItem) {
            // Bind all data
            itemName.text = item.name
            category.text = "Category: ${item.category}"
            expiryDate.text = "Expires: ${item.expiryDate}"
            quantity.text = "Qty: ${item.quantity}"

            // Set item icon based on category
            setItemIcon(item.category, item.status)

            // Set status badge text, colors, and indicator based on status
            when (item.status) {
                "expired" -> {
                    statusBadge.text = "Expired"
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_expired)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.red_500))
                }
                "expiring" -> {
                    val daysText = when {
                        item.daysLeft == 0 -> "Expires today"
                        item.daysLeft == 1 -> "Expires in 1 day"
                        else -> "Expires in ${item.daysLeft} days"
                    }

                    statusBadge.text = daysText
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_expiring)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange_500))
                }
                else -> { // "fresh"
                    statusBadge.text = "Fresh (${item.daysLeft} days left)"
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_fresh)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                }
            }

            // Set click listener
            cardView.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun setItemIcon(category: String, status: String) {
            // Set icon based on category (with fallbacks for missing drawables)
            val iconRes = when (category.lowercase()) {
                "fruits", "fruit" -> getDrawableResource("fruit", R.drawable.fruits)
                "dairy" -> getDrawableResource("milk", R.drawable.milk)
                "vegetables", "vegetable" -> getDrawableResource("vegetable", R.drawable.vegetables)
                "meat" -> getDrawableResource("meat", R.drawable.meat)
                "bakery" -> getDrawableResource("breads", R.drawable.bread)
                "frozen" -> getDrawableResource("frozen", R.drawable.frozen)
                "beverages" -> getDrawableResource("beverages", R.drawable.beverages)
                "cereals" -> getDrawableResource("cereals", R.drawable.cereals)
                "sweets" -> getDrawableResource("sweets", R.drawable.sweets)
                else -> getDrawableResource("grocery", R.drawable.ic_grocery)
            }

            itemIcon.setImageResource(iconRes)

            // Set icon tint based on status for visual hierarchy
            val tintColor = when (status) {
                "expired" -> R.color.red_400
                "expiring" -> R.color.orange_400
                else -> R.color.green_400
            }

            try {
                itemIcon.setColorFilter(ContextCompat.getColor(itemView.context, tintColor))
            } catch (e: Exception) {
                // Fallback to gray if color resource doesn't exist
                itemIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.gray_600))
            }
        }

        private fun getDrawableResource(resourceName: String, fallback: Int): Int {
            return try {
                val context = itemView.context
                val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
                if (resourceId != 0) resourceId else fallback
            } catch (e: Exception) {
                fallback
            }
        }
    }
}