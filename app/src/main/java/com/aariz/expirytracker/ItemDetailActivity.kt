package com.aariz.expirytracker

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ItemDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_item_detail)

        val name = intent.getStringExtra("name") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val expiry = intent.getStringExtra("expiryDate") ?: ""
        val purchase = intent.getStringExtra("purchaseDate") ?: ""
        val quantity = intent.getIntExtra("quantity", 0)
        val status = intent.getStringExtra("status") ?: "fresh"
        val daysLeft = intent.getIntExtra("daysLeft", 0)

        findViewById<TextView>(R.id.text_name).text = name
        findViewById<TextView>(R.id.text_category_chip).text = category
        findViewById<TextView>(R.id.text_expiry).text = expiry
        findViewById<TextView>(R.id.text_purchase).text = purchase
        findViewById<TextView>(R.id.text_quantity).text = quantity.toString()

        val statusChip = findViewById<TextView>(R.id.text_status_chip)
        when (status) {
            "fresh" -> {
                statusChip.text = "Fresh (${daysLeft} days left)"
                statusChip.setBackgroundResource(R.drawable.chip_green)
                statusChip.setTextColor(Color.parseColor("#2E7D32"))
            }
            "expiring" -> {
                val daysLabel = if (daysLeft == 1) "1 day" else "${daysLeft} days"
                statusChip.text = "Expires in $daysLabel"
                statusChip.setBackgroundResource(R.drawable.chip_yellow)
                statusChip.setTextColor(Color.parseColor("#8D6E00"))
            }
            "expired" -> {
                statusChip.text = "Expired"
                statusChip.setBackgroundResource(R.drawable.chip_red)
                statusChip.setTextColor(Color.parseColor("#B71C1C"))
            }
        }

        // Back is handled by the top bar ImageView via android:onClick="onBackPressed" in screen_item_detail.xml
        findViewById<LinearLayout>(R.id.button_edit).setOnClickListener {
            Toast.makeText(this, "Edit functionality coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.button_mark_used).setOnClickListener {
            Toast.makeText(this, "Item marked as used!", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<LinearLayout>(R.id.button_delete).setOnClickListener {
            Toast.makeText(this, "Item deleted!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}