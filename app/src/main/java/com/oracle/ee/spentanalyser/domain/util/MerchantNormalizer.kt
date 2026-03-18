package com.oracle.ee.spentanalyser.domain.util

import java.util.Locale

object MerchantNormalizer {

    private val PURE_MAPPINGS = mapOf(
        "amazon" to "Amazon",
        "amzn" to "Amazon",
        "flipkart" to "Flipkart",
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "blinkit" to "Blinkit",
        "zepto" to "Zepto",
        "uber" to "Uber",
        "ola" to "Ola",
        "jio" to "Jio",
        "airtel" to "Airtel",
        "myntra" to "Myntra",
        "nykaa" to "Nykaa",
        "ajio" to "Ajio",
        "dmart" to "DMart",
        "bigbasket" to "BigBasket",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "prime" to "Amazon Prime",
        "hotstar" to "Hotstar",
        "irctc" to "IRCTC",
        "makemytrip" to "MakeMyTrip",
        "mmt" to "MakeMyTrip",
        "goibibo" to "Goibibo",
        "cleartrip" to "Cleartrip",
        "bookmyshow" to "BookMyShow",
        "bms" to "BookMyShow",
        "paytm" to "Paytm",
        "phonepe" to "PhonePe",
        "gpay" to "Google Pay",
        "cred" to "CRED"
    )

    private val NOISE_WORDS = listOf(
        "pay", "retail", "pvt", "ltd", "pos", "upi", 
        "india", "private", "limited", "technologies", "app"
    )

    fun normalize(rawMerchant: String): String {
        if (rawMerchant.isBlank() || rawMerchant.equals("unknown", ignoreCase = true)) {
            return "Unknown"
        }

        var cleanMerchant = rawMerchant.trim().lowercase(Locale.ROOT)

        // 1. Exact or Substring Mapping Check (Highly Aggressive)
        // If the merchant string contains a known broad term like "swiggy", collapse it immediately.
        for ((key, normalizedValue) in PURE_MAPPINGS) {
            if (cleanMerchant.contains(key)) {
                return normalizedValue
            }
        }

        // 2. Filter Noise Words
        // If it wasn't a standard top-tier merchant, let's at least clean up the garbage strings.
        var tokens = cleanMerchant.split(Regex("\\s+|[^a-zA-Z0-9]")).filter { it.isNotBlank() }
        
        tokens = tokens.filter { token ->
            !NOISE_WORDS.contains(token)
        }

        if (tokens.isEmpty()) {
            // Unlikely, but if it was pure noise like "UPI POS", return the original capitalized gracefully
            return rawMerchant.trim().capitalizeWords()
        }

        cleanMerchant = tokens.joinToString(" ")
        
        // 3. Title Casing Fallback
        return cleanMerchant.capitalizeWords()
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
}
