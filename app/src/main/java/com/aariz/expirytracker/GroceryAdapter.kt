package com.aariz.expirytracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onItemClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.image_icon)
        val name: TextView = itemView.findViewById(R.id.text_name)
        val meta: TextView = itemView.findViewById(R.id.text_meta)
        val status: TextView = itemView.findViewById(R.id.text_status)
        val strip: View = itemView.findViewById(R.id.view_status_strip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grocery, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.meta.text = "Expires: ${item.expiryDate}"
        when (item.status) {
            "fresh" -> {
                holder.status.text = "Fresh (${item.daysLeft} days left)"
                holder.status.setBackgroundResource(R.drawable.chip_green)
                holder.status.setTextColor(Color.parseColor("#2E7D32"))
                holder.strip.setBackgroundColor(Color.parseColor("#2E7D32"))
            }
            "expiring" -> {
                val daysLabel = if (item.daysLeft == 1) "1 day" else "${item.daysLeft} days"
                holder.status.text = "Expires in $daysLabel"
                holder.status.setBackgroundResource(R.drawable.chip_yellow)
                holder.status.setTextColor(Color.parseColor("#8D6E00"))
                holder.strip.setBackgroundColor(Color.parseColor("#FFA000"))
            }
            "expired" -> {
                holder.status.text = "Expired"
                holder.status.setBackgroundResource(R.drawable.chip_red)
                holder.status.setTextColor(Color.parseColor("#B71C1C"))
                holder.strip.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}
