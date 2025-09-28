package com.aariz.expirytracker

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class GS1ParsedData(
    val gtin: String = "",              // (01) Global Trade Item Number
    val expiryDate: String = "",        // (17) Expiry Date (YYMMDD)
    val batchLot: String = "",          // (10) Batch/Lot Number
    val serialNumber: String = "",      // (21) Serial Number
    val productionDate: String = "",    // (11) Production Date (YYMMDD)
    val bestBeforeDate: String = "",    // (15) Best Before Date (YYMMDD)
    val sellByDate: String = "",        // Custom parsing for sell-by dates
    val weight: String = "",            // (30) Variable Weight
    val price: String = "",             // (392n) Price
    val rawData: String = "",           // Original scanned data
    val applicationIdentifiers: Map<String, String> = emptyMap() // All parsed AIs
)

class GS1Parser {

    companion object {
        private const val TAG = "GS1Parser"

        // GS1 Application Identifiers for dates (all in YYMMDD format)
        private const val AI_EXPIRY_DATE = "17"
        private const val AI_PRODUCTION_DATE = "11"
        private const val AI_BEST_BEFORE_DATE = "15"
        private const val AI_SELL_BY_DATE = "16"
        private const val AI_USE_BY_DATE = "17" // Same as expiry

        // Other important AIs
        private const val AI_GTIN = "01"
        private const val AI_BATCH_LOT = "10"
        private const val AI_SERIAL_NUMBER = "21"
        private const val AI_WEIGHT = "30"

        // GS1 separators
        private const val GROUP_SEPARATOR = "\u001D" // ASCII 29
        private const val FNC1 = "]C1" // FNC1 in some formats
    }

    fun parseGS1Data(rawData: String): GS1ParsedData {
        Log.d(TAG, "Parsing GS1 data: $rawData")

        return try {
            // Handle different GS1 formats
            val cleanData = preprocessGS1Data(rawData)
            val applicationIdentifiers = extractApplicationIdentifiers(cleanData)

            // Parse dates and convert to readable format
            val expiryDate = parseAndFormatDate(applicationIdentifiers[AI_EXPIRY_DATE])
            val productionDate = parseAndFormatDate(applicationIdentifiers[AI_PRODUCTION_DATE])
            val bestBeforeDate = parseAndFormatDate(applicationIdentifiers[AI_BEST_BEFORE_DATE])
            val sellByDate = parseAndFormatDate(applicationIdentifiers[AI_SELL_BY_DATE])

            // Use the best available date for expiry
            val finalExpiryDate = when {
                expiryDate.isNotEmpty() -> expiryDate
                bestBeforeDate.isNotEmpty() -> bestBeforeDate
                sellByDate.isNotEmpty() -> sellByDate
                else -> ""
            }

            GS1ParsedData(
                gtin = applicationIdentifiers[AI_GTIN] ?: "",
                expiryDate = finalExpiryDate,
                batchLot = applicationIdentifiers[AI_BATCH_LOT] ?: "",
                serialNumber = applicationIdentifiers[AI_SERIAL_NUMBER] ?: "",
                productionDate = productionDate,
                bestBeforeDate = bestBeforeDate,
                sellByDate = sellByDate,
                weight = applicationIdentifiers[AI_WEIGHT] ?: "",
                rawData = rawData,
                applicationIdentifiers = applicationIdentifiers
            ).also {
                Log.d(TAG, "Parsed GS1 data: $it")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GS1 data: ${e.message}", e)
            GS1ParsedData(rawData = rawData)
        }
    }

    private fun preprocessGS1Data(rawData: String): String {
        // Remove common prefixes and clean the data
        var cleanData = rawData

        // Remove FNC1 indicators
        cleanData = cleanData.replace(FNC1, "")
        cleanData = cleanData.replace("]C1", "")
        cleanData = cleanData.replace("]d2", "") // Another FNC1 variant

        // Handle different separator formats
        cleanData = cleanData.replace("<GS>", GROUP_SEPARATOR)
        cleanData = cleanData.replace("\\u001D", GROUP_SEPARATOR)

        Log.d(TAG, "Preprocessed data: $cleanData")
        return cleanData
    }

    private fun extractApplicationIdentifiers(data: String): Map<String, String> {
        val ais = mutableMapOf<String, String>()

        // Split by group separator if present
        val segments = if (data.contains(GROUP_SEPARATOR)) {
            data.split(GROUP_SEPARATOR)
        } else {
            // Parse without separators (more complex)
            parseWithoutSeparators(data)
        }

        for (segment in segments) {
            if (segment.length < 3) continue

            // Try different AI lengths (2, 3, 4 digits)
            for (aiLength in 2..4) {
                if (segment.length > aiLength) {
                    val ai = segment.substring(0, aiLength)
                    val value = segment.substring(aiLength)

                    if (isValidApplicationIdentifier(ai)) {
                        ais[ai] = value
                        Log.d(TAG, "Found AI: $ai = $value")
                        break
                    }
                }
            }
        }

        return ais
    }

    private fun parseWithoutSeparators(data: String): List<String> {
        val segments = mutableListOf<String>()
        var pos = 0

        while (pos < data.length) {
            // Find the next AI
            val ai = findNextApplicationIdentifier(data, pos)
            if (ai != null) {
                val aiLength = ai.length
                val valueLength = getExpectedValueLength(ai, data, pos + aiLength)

                if (pos + aiLength + valueLength <= data.length) {
                    val segment = data.substring(pos, pos + aiLength + valueLength)
                    segments.add(segment)
                    pos += aiLength + valueLength
                } else {
                    // Take the rest of the string
                    segments.add(data.substring(pos))
                    break
                }
            } else {
                break
            }
        }

        return segments
    }

    private fun findNextApplicationIdentifier(data: String, startPos: Int): String? {
        // Check for common AIs starting at the position
        val commonAIs = listOf("01", "10", "11", "15", "17", "21", "30", "392")

        for (ai in commonAIs) {
            if (startPos + ai.length <= data.length) {
                val candidate = data.substring(startPos, startPos + ai.length)
                if (candidate == ai) {
                    return ai
                }
            }
        }

        return null
    }

    private fun getExpectedValueLength(ai: String, data: String, valueStartPos: Int): Int {
        // Fixed length AIs
        return when (ai) {
            "01" -> 14 // GTIN
            "11", "15", "17" -> 6 // Dates (YYMMDD)
            "30" -> 8 // Weight
            else -> {
                // Variable length - find next AI or end of string
                val remaining = data.substring(valueStartPos)
                findNextAIPosition(remaining) ?: remaining.length
            }
        }
    }

    private fun findNextAIPosition(data: String): Int? {
        val commonAIs = listOf("01", "10", "11", "15", "17", "21", "30")

        for (i in 1 until data.length - 1) {
            for (ai in commonAIs) {
                if (i + ai.length <= data.length) {
                    val candidate = data.substring(i, i + ai.length)
                    if (candidate == ai) {
                        return i
                    }
                }
            }
        }

        return null
    }

    private fun isValidApplicationIdentifier(ai: String): Boolean {
        // List of common/valid AIs
        val validAIs = setOf(
            "00", "01", "02", "10", "11", "12", "13", "15", "16", "17", "20", "21",
            "22", "30", "31", "32", "33", "34", "35", "36", "37", "90", "91", "92",
            "93", "94", "95", "96", "97", "98", "99", "240", "241", "242", "243",
            "250", "251", "253", "254", "255", "390", "391", "392", "393", "394",
            "395", "396", "397", "400", "401", "402", "403", "410", "411", "412",
            "413", "414", "415", "416", "417", "420", "421", "422", "423", "424",
            "425", "426", "427"
        )

        return validAIs.contains(ai)
    }

    private fun parseAndFormatDate(dateString: String?): String {
        if (dateString.isNullOrEmpty() || dateString.length != 6) {
            return ""
        }

        return try {
            // Parse YYMMDD format
            val year = dateString.substring(0, 2).toInt()
            val month = dateString.substring(2, 4).toInt()
            val day = dateString.substring(4, 6).toInt()

            // Handle 2-digit year (assume 20xx for 00-49, 19xx for 50-99)
            val fullYear = if (year <= 49) 2000 + year else 1900 + year

            // Validate date components
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                Log.w(TAG, "Invalid date components: year=$fullYear, month=$month, day=$day")
                return ""
            }

            val calendar = Calendar.getInstance().apply {
                set(fullYear, month - 1, day) // Calendar months are 0-based
            }

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            formatter.format(calendar.time)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $dateString", e)
            ""
        }
    }

    /**
     * Check if the scanned data looks like GS1 format
     */
    fun isGS1Format(data: String): Boolean {
        // Check for common GS1 indicators
        return data.contains(GROUP_SEPARATOR) ||
                data.contains(FNC1) ||
                data.contains("]C1") ||
                data.contains("]d2") ||
                startsWithGS1AI(data)
    }

    private fun startsWithGS1AI(data: String): Boolean {
        if (data.length < 2) return false

        // Check if starts with common GS1 AIs
        val commonStartAIs = listOf("01", "10", "11", "15", "17", "21", "30", "90", "91")
        val prefix = data.substring(0, 2)

        return commonStartAIs.contains(prefix) && data.length > 10 // Must have reasonable length
    }

    /**
     * Extract the primary barcode/GTIN for product lookup
     */
    fun extractPrimaryBarcode(gs1Data: GS1ParsedData): String {
        return when {
            gs1Data.gtin.isNotEmpty() -> gs1Data.gtin
            gs1Data.rawData.length >= 12 -> {
                // Try to extract from raw data if GTIN not parsed
                val cleaned = gs1Data.rawData.replace(Regex("[^0-9]"), "")
                if (cleaned.length >= 12) {
                    cleaned.substring(0, minOf(14, cleaned.length))
                } else {
                    gs1Data.rawData
                }
            }
            else -> gs1Data.rawData
        }
    }
}