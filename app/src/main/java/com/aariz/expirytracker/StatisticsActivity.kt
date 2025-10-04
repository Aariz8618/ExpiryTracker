package com.aariz.expirytracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StatisticsActivity : AppCompatActivity() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    // View references
    private lateinit var tvItemsUsed: TextView
    private lateinit var tvItemsExpired: TextView
    private lateinit var tvLegendUsed: TextView
    private lateinit var tvLegendExpired: TextView
    private lateinit var tvLegendFresh: TextView
    private lateinit var tvMonthSummary: TextView
    private lateinit var tvEfficiencyRate: TextView
    private lateinit var tvTotalItems: TextView
    private lateinit var tvWasteSaved: TextView
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_statistics)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        // Check authentication
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to view statistics", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        loadStatistics()
    }

    private fun initViews() {
        tvItemsUsed = findViewById(R.id.tv_items_used)
        tvItemsExpired = findViewById(R.id.tv_items_expired)
        tvLegendUsed = findViewById(R.id.tv_legend_used)
        tvLegendExpired = findViewById(R.id.tv_legend_expired)
        tvLegendFresh = findViewById(R.id.tv_legend_fresh)
        tvMonthSummary = findViewById(R.id.tv_month_summary)
        tvEfficiencyRate = findViewById(R.id.tv_efficiency_rate)
        tvTotalItems = findViewById(R.id.tv_total_items)
        tvWasteSaved = findViewById(R.id.tv_waste_saved)
        pieChart = findViewById(R.id.pie_chart)
        barChart = findViewById(R.id.bar_chart)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.button_export).setOnClickListener {
            exportStatistics()
        }

        // Add click listeners for summary cards
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_items_used).setOnClickListener {
            openFilteredItems("used")
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_items_expired).setOnClickListener {
            openFilteredItems("expired")
        }
    }

    private fun openFilteredItems(filterStatus: String) {
        val intent = Intent(this, FilteredItemsActivity::class.java).apply {
            putExtra("filter_status", filterStatus)
        }
        startActivity(intent)
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()
                    calculateAndDisplayStatistics(items)
                } else {
                    Toast.makeText(
                        this@StatisticsActivity,
                        "Failed to load statistics: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@StatisticsActivity,
                    "Error loading statistics: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun calculateAndDisplayStatistics(items: List<GroceryItem>) {
        // Recalculate status for each item in real-time
        val itemsWithUpdatedStatus = items.map { item ->
            val daysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(daysLeft, item.status)
            item.copy(daysLeft = daysLeft, status = actualStatus)
        }

        // Count items by status
        val usedCount = itemsWithUpdatedStatus.count { it.status == "used" }
        val expiredCount = itemsWithUpdatedStatus.count { it.status == "expired" }
        val freshCount = itemsWithUpdatedStatus.count {
            it.status == "fresh" || it.status == "expiring"
        }
        val totalItems = itemsWithUpdatedStatus.size

        // Calculate this month's data
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val thisMonthItems = itemsWithUpdatedStatus.filter { item ->
            isFromCurrentMonth(item.createdAt, currentMonth, currentYear)
        }

        val monthUsedCount = thisMonthItems.count { it.status == "used" }
        val monthExpiredCount = thisMonthItems.count { it.status == "expired" }

        // Calculate efficiency rate (used items / total non-fresh items)
        val nonFreshItems = usedCount + expiredCount
        val efficiencyRate = if (nonFreshItems > 0) {
            (usedCount.toFloat() / nonFreshItems.toFloat() * 100).toInt()
        } else {
            0
        }

        // Items saved from waste (used items that could have expired)
        val itemsSaved = usedCount

        // Calculate monthly trends (last 4 months)
        val monthlyTrends = calculateMonthlyTrends(itemsWithUpdatedStatus)

        // Update UI
        updateUI(
            usedCount = usedCount,
            expiredCount = expiredCount,
            freshCount = freshCount,
            totalItems = totalItems,
            monthUsedCount = monthUsedCount,
            monthExpiredCount = monthExpiredCount,
            efficiencyRate = efficiencyRate,
            itemsSaved = itemsSaved
        )

        // Setup charts
        setupPieChart(usedCount, expiredCount, freshCount)
        setupBarChart(monthlyTrends)
    }

    private fun calculateMonthlyTrends(items: List<GroceryItem>): List<MonthData> {
        val calendar = Calendar.getInstance()
        val monthDataList = mutableListOf<MonthData>()

        // Get last 4 months
        for (i in 3 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)

            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)

            val monthItems = items.filter { item ->
                val itemCalendar = Calendar.getInstance()
                itemCalendar.time = item.createdAt
                itemCalendar.get(Calendar.MONTH) == month &&
                        itemCalendar.get(Calendar.YEAR) == year
            }

            val used = monthItems.count { it.status == "used" }
            val expired = monthItems.count { it.status == "expired" }

            monthDataList.add(MonthData(monthName, used, expired))
        }

        return monthDataList
    }

    private fun setupPieChart(usedCount: Int, expiredCount: Int, freshCount: Int) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        // Add entries and their corresponding colors
        if (usedCount > 0) {
            entries.add(PieEntry(usedCount.toFloat(), "Used"))
            colors.add(Color.parseColor("#FF9800")) // orange for Used
        }
        if (expiredCount > 0) {
            entries.add(PieEntry(expiredCount.toFloat(), "Expired"))
            colors.add(Color.parseColor("#F44336")) // Red for Expired
        }
        if (freshCount > 0) {
            entries.add(PieEntry(freshCount.toFloat(), "Fresh"))
            colors.add(Color.parseColor("#2E7D32")) // Dark green for Fresh
        }

        // If no data, show placeholder
        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "No Data"))
            colors.add(Color.parseColor("#E0E0E0"))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colors
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE
        dataSet.sliceSpace = 2f

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        // Configure chart
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.setUsePercentValues(true)
        pieChart.isRotationEnabled = false
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.holeRadius = 0f
        pieChart.transparentCircleRadius = 0f
        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    private fun setupBarChart(monthlyData: List<MonthData>) {
        val usedEntries = ArrayList<BarEntry>()
        val expiredEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        monthlyData.forEachIndexed { index, data ->
            usedEntries.add(BarEntry(index.toFloat(), data.used.toFloat()))
            expiredEntries.add(BarEntry(index.toFloat(), data.expired.toFloat()))
            labels.add(data.month)
        }

        val usedDataSet = BarDataSet(usedEntries, "Used").apply {
            color = Color.parseColor("#4CAF50")
            valueTextSize = 10f
            valueFormatter = IntValueFormatter()
        }

        val expiredDataSet = BarDataSet(expiredEntries, "Expired").apply {
            color = Color.parseColor("#F44336")
            valueTextSize = 10f
            valueFormatter = IntValueFormatter()
        }

        val barData = BarData(usedDataSet, expiredDataSet).apply {
            // For proper grouping with 2 datasets: (barWidth + barSpace) * 2 + groupSpace = 1f
            barWidth = 0.30f
        }

        // Configure chart basics
        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.setFitBars(false)
        barChart.animateY(1000)

        // Group bars safely
        val groupSpace = 0.30f
        val barSpace = 0.05f

        if (labels.isNotEmpty()) {
            barChart.xAxis.axisMinimum = 0f
            barChart.groupBars(0f, groupSpace, barSpace)
            val groupWidth = barChart.barData.getGroupWidth(groupSpace, barSpace)
            barChart.xAxis.axisMaximum = 0f + groupWidth * labels.size
        }

        // X-axis configuration
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(true)
        xAxis.setDrawGridLines(false)
        xAxis.textSize = 10f

        // Y-axis configuration
        barChart.axisLeft.setDrawGridLines(true)
        barChart.axisLeft.granularity = 1f
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.valueFormatter = IntValueFormatter()
        barChart.axisRight.isEnabled = false

        // Legend
        barChart.legend.isEnabled = true
        barChart.legend.textSize = 12f

        barChart.invalidate()
    }

    // Format values as integers and hide zeros to reduce clutter on the chart
    private class IntValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return if (value == 0f) "" else value.toInt().toString()
        }
    }

    private fun updateUI(
        usedCount: Int,
        expiredCount: Int,
        freshCount: Int,
        totalItems: Int,
        monthUsedCount: Int,
        monthExpiredCount: Int,
        efficiencyRate: Int,
        itemsSaved: Int
    ) {
        // Summary cards
        tvItemsUsed.text = usedCount.toString()
        tvItemsExpired.text = expiredCount.toString()

        // Legend
        tvLegendUsed.text = "Used ($usedCount)"
        tvLegendExpired.text = "Expired ($expiredCount)"
        tvLegendFresh.text = "Fresh ($freshCount)"

        // Monthly summary
        tvMonthSummary.text = "This month: $monthUsedCount items used, $monthExpiredCount expired."

        // Details
        tvEfficiencyRate.text = "$efficiencyRate%"
        tvTotalItems.text = totalItems.toString()
        tvWasteSaved.text = itemsSaved.toString()
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.isLenient = false

            val expiry = sdf.parse(expiryDate) ?: return 0

            val expiryCalendar = Calendar.getInstance().apply {
                time = expiry
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffInMillis = expiryCalendar.timeInMillis - todayCalendar.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun determineStatus(daysLeft: Int, currentStatus: String): String {
        // Don't change status if item is marked as "used"
        if (currentStatus == "used") return "used"

        return when {
            daysLeft < 0 -> "expired"
            daysLeft == 0 -> "expiring"
            daysLeft <= 3 -> "expiring"
            else -> "fresh"
        }
    }

    private fun isFromCurrentMonth(date: Date, currentMonth: Int, currentYear: Int): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val itemMonth = calendar.get(Calendar.MONTH)
        val itemYear = calendar.get(Calendar.YEAR)

        return itemMonth == currentMonth && itemYear == currentYear
    }

    private fun exportStatistics() {
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    // Recalculate status for each item
                    val itemsWithUpdatedStatus = items.map { item ->
                        val daysLeft = calculateDaysLeft(item.expiryDate)
                        val actualStatus = determineStatus(daysLeft, item.status)
                        item.copy(daysLeft = daysLeft, status = actualStatus)
                    }

                    val usedCount = itemsWithUpdatedStatus.count { it.status == "used" }
                    val expiredCount = itemsWithUpdatedStatus.count { it.status == "expired" }
                    val freshCount = itemsWithUpdatedStatus.count {
                        it.status == "fresh" || it.status == "expiring"
                    }

                    val shareText = buildString {
                        appendLine("📊 My Food Tracking Stats")
                        appendLine()
                        appendLine("🍽️ Total Items: ${itemsWithUpdatedStatus.size}")
                        appendLine("✅ Items Used: $usedCount")
                        appendLine("❌ Items Expired: $expiredCount")
                        appendLine("🌱 Fresh Items: $freshCount")
                        appendLine()

                        val nonFreshItems = usedCount + expiredCount
                        if (nonFreshItems > 0) {
                            val efficiency = (usedCount.toFloat() / nonFreshItems.toFloat() * 100).toInt()
                            appendLine("⚡ Efficiency Rate: $efficiency%")
                        }

                        appendLine()
                        appendLine("Track your groceries with Expiry Tracker!")
                    }

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }

                    startActivity(Intent.createChooser(shareIntent, "Share Statistics"))

                } else {
                    Toast.makeText(
                        this@StatisticsActivity,
                        "Failed to export statistics",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@StatisticsActivity,
                    "Error exporting: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

// Data class for monthly trends
data class MonthData(
    val month: String,
    val used: Int,
    val expired: Int
)