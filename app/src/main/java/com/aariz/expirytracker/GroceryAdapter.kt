package com.aariz.expirytracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onItemClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.tv_item_name)
        val categoryText: TextView = itemView.findViewById(R.id.tv_category)
        val expiryText: TextView = itemView.findViewById(R.id.tv_expiry_date)
        val quantityText: TextView = itemView.findViewById(R.id.tv_quantity)
        val statusText: TextView = itemView.findViewById(R.id.tv_status)
        val daysLeftText: TextView = itemView.findViewById(R.id.tv_days_left)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grocery, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.nameText.text = item.name
        holder.categoryText.text = item.category
        holder.expiryText.text = "Expires: ${item.expiryDate}"
        holder.quantityText.text = "Qty: ${item.quantity}"

        // Set status and days left based on item status
        when (item.status) {
            "fresh" -> {
                holder.statusText.text = "Fresh"
                holder.statusText.setTextColor(Color.parseColor("#10B981")) // Green
                holder.daysLeftText.text = "${item.daysLeft} days left"
                holder.daysLeftText.setTextColor(Color.parseColor("#6B7280"))
            }
            "expiring" -> {
                holder.statusText.text = "Expiring Soon"
                holder.statusText.setTextColor(Color.parseColor("#F59E0B")) // Orange
                holder.daysLeftText.text = when {
                    item.daysLeft == 0 -> "Expires today"
                    item.daysLeft == 1 -> "1 day left"
                    else -> "${item.daysLeft} days left"
                }
                holder.daysLeftText.setTextColor(Color.parseColor("#F59E0B"))
            }
            "expired" -> {
                holder.statusText.text = "Expired"
                holder.statusText.setTextColor(Color.parseColor("#EF4444")) // Red
                holder.daysLeftText.text = when {
                    item.daysLeft == -1 -> "Expired yesterday"
                    item.daysLeft < -1 -> "Expired ${Math.abs(item.daysLeft)} days ago"
                    else -> "Expired"
                }
                holder.daysLeftText.setTextColor(Color.parseColor("#EF4444"))
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}