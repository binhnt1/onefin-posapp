package com.onefin.posapp.core.managers.helpers

object CardTypeDetector {

    // BIN ranges cho các loại thẻ
    private val binRanges = mapOf(
        "VISA" to listOf(
            4, // Visa starts with 4
        ),
        "MASTERCARD" to listOf(
            51, 52, 53, 54, 55, // 51-55
            2221, 2720 // 2221-2720
        ),
        "JCB" to listOf(
            3528, 3589 // 3528-3589
        ),
        "UNIONPAY" to listOf(
            62 // UnionPay starts with 62
        ),
        "AMEX" to listOf(
            34, 37 // AmEx starts with 34 or 37
        ),
        "DISCOVER" to listOf(
            6011, 622126, 644, 645, 646, 647, 648, 649, 65 // Discover ranges
        ),
        "NAPAS" to listOf(
            9704, 9705 // Napas (Vietnamese)
        )
    )

    /**
     * Detect card brand from PAN (BIN)
     * @param pan: Card number
     * @return Card brand name (VISA, MASTERCARD, JCB, UNIONPAY, AMEX, DISCOVER, NAPAS)
     */
    fun detectCardBrand(pan: String?): String {
        if (pan.isNullOrEmpty() || pan.length < 6) return "UNKNOWN"

        val bin = pan.take(6)

        // Check 4-digit BIN first (for ranges like 2221-2720, 3528-3589, etc.)
        val bin4Digit = bin.take(4).toIntOrNull() ?: 0
        for ((brand, bins) in binRanges) {
            if (bins.any { it in 1000..9999 && it == bin4Digit }) {
                return brand
            }
        }

        // Check 6-digit BIN
        val bin6Digit = bin.toIntOrNull() ?: 0
        for ((brand, bins) in binRanges) {
            if (bins.any { it in 100000..999999 && it == bin6Digit }) {
                return brand
            }
        }

        // Check 2-digit BIN (for single digits like 4, 5, 6)
        val firstDigit = pan.first().toString().toIntOrNull() ?: 0
        val firstTwoDigits = pan.take(2).toIntOrNull() ?: 0

        for ((brand, bins) in binRanges) {
            if (bins.any { it == firstDigit || it == firstTwoDigits }) {
                return brand
            }
        }

        return "UNKNOWN"
    }

    /**
     * Get card type enum from brand name
     */
    fun getCardTypeFromBrand(brand: String): String {
        return when (brand.uppercase()) {
            "VISA", "MASTERCARD", "JCB", "UNIONPAY", "AMEX", "DISCOVER", "NAPAS" -> brand.uppercase()
            else -> "UNKNOWN"
        }
    }
}