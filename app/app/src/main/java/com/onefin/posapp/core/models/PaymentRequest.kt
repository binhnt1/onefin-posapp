package com.onefin.posapp.core.models

import timber.log.Timber
import java.util.UUID

/**
 * Payment Request Data Model
 */
data class PaymentRequest(
    val requestId: String = generateRequestId(),
    val data: PaymentData
)

data class PaymentData(
    val card: CardInfo,
    val device: DeviceInfo,
    val payment: PaymentInfo
)

data class CardInfo(
    val emv: String? = null,
    val ksn: String? = null,
    val pin: String? = null,
    val type: String? = null,
    val mode: String,  // 1=Manual, 2=Swipe, 3=Insert, 4=Contactless, 5=Fallback
    val newPin: String? = null,
    val track1: String = "",
    val track2: String = "",
    val track3: String = "",
    val emvData: String = "",
    val clearPan: String = "",
    val expiryDate: String = ""
)

data class DeviceInfo(
    val posEntryMode: String,  // VD: "071" = Contactless chip
    val posConditionCode: String = "00"
)

data class PaymentInfo(
    val currency: String = "VND",
    val transAmount: String
)

data class EmvResult(
    val pan: String,              // Số thẻ (từ tag 5A)
    val expiry: String,       // YYMM (từ tag 5F24)
    val track2: String,           // Track2 equivalent (từ tag 57)
    val emvTlvData: String,       // Toàn bộ EMV data (hex)
    val applicationLabel: String? = null, // Tên app: VISA, Mastercard (từ tag 50)
    val cardholderName: String? = null    // Tên chủ thẻ nếu có (từ tag 5F20)
)

/**
 * Card Data Parser - Chuyển đổi CardData thành PaymentRequest
 */
object CardDataParser {

    private const val TAG = "CardDataParser"

    /**
     * Parse CardData thành PaymentRequest
     */
    fun parseToPaymentRequest(
        cardData: CardData,
        amount: String,
        currency: String = "VND"
    ): PaymentRequest {

        val cardInfo = when (cardData.type) {
            CardType.MAGNETIC -> {
                parseMagneticCard(cardData)
            }
            CardType.CHIP -> {
                parseChipCard(cardData)
            }
            CardType.CONTACTLESS -> {
                parseContactlessCard(cardData)
            }
        }

        val deviceInfo = DeviceInfo(
            posEntryMode = getPosEntryMode(cardData),
            posConditionCode = "00"
        )

        val paymentInfo = PaymentInfo(
            currency = currency,
            transAmount = amount
        )

        return PaymentRequest(
            data = PaymentData(
                card = cardInfo,
                device = deviceInfo,
                payment = paymentInfo
            )
        )
    }

    /**
     * Parse thẻ từ
     */
    private fun parseMagneticCard(cardData: CardData): CardInfo {
        val track2 = cardData.track2
        val (pan, expiryDate) = parseTrack2(track2)

        return CardInfo(
            mode = "2", // Swipe
            track1 = cardData.track1,
            track2 = track2,
            track3 = cardData.track3,
            clearPan = pan,
            expiryDate = expiryDate
        )
    }

    /**
     * Parse thẻ chip (cần đọc thêm EMV data)
     */
    private fun parseChipCard(cardData: CardData): CardInfo {
        // Với thẻ chip, bạn cần đọc EMV data sau khi detect
        // Đây là placeholder, bạn cần implement EMV processing

        return CardInfo(
            mode = "3", // Insert/Chip
            emvData = "", // Cần đọc EMV tags
            clearPan = "", // Sẽ lấy từ EMV tag 5A
            expiryDate = "", // Sẽ lấy từ EMV tag 5F24
            type = "EMV"
        )
    }

    /**
     * Parse thẻ contactless
     */
    private fun parseContactlessCard(cardData: CardData): CardInfo {
        // Parse EMV data nếu có
        val emvData = cardData.atr
        val (pan, expiryDate) = if (cardData.ats.isNotEmpty()) {
            parseEmvData(emvData)
        } else {
            Pair("", "")
        }

        return CardInfo(
            mode = "4", // Contactless
            emvData = emvData,
            clearPan = pan,
            expiryDate = expiryDate,
            track2 = "", // Contactless có thể không có track data
            type = "CONTACTLESS"
        )
    }

    /**
     * Parse Track2 để lấy PAN và Expiry Date
     * Format: PAN=YYMM...
     */
    private fun parseTrack2(track2: String): Pair<String, String> {
        try {
            if (track2.isEmpty()) return Pair("", "")

            // Track2 format: PAN=YYMM... hoặc PAN=YYMM^...
            val parts = track2.split("=", "^", "D", "d")

            if (parts.size < 2) {
                Timber.tag(TAG).w("Invalid Track2 format: $track2")
                return Pair("", "")
            }

            val pan = parts[0].trim()
            val remainder = parts[1]

            // Expiry date: YYMM (4 digits)
            val expiryDate = if (remainder.length >= 4) {
                remainder.substring(0, 4)
            } else {
                ""
            }

            Timber.tag(TAG).d("Parsed - PAN: ${maskPan(pan)}, Expiry: $expiryDate")

            return Pair(pan, expiryDate)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing Track2")
            return Pair("", "")
        }
    }

    /**
     * Parse EMV data để lấy PAN và Expiry
     */
    private fun parseEmvData(emvHex: String): Pair<String, String> {
        try {
            if (emvHex.isEmpty()) return Pair("", "")

            // Tag 5A = Application PAN
            val pan = extractEmvTag(emvHex, "5A") ?: ""

            // Tag 5F24 = Application Expiration Date (YYMMDD)
            val expiryFull = extractEmvTag(emvHex, "5F24") ?: ""
            val expiry = if (expiryFull.length >= 4) expiryFull.substring(0, 4) else ""

            return Pair(pan, expiry)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing EMV data")
            return Pair("", "")
        }
    }

    /**
     * Extract EMV tag từ hex string
     */
    private fun extractEmvTag(emvHex: String, tag: String): String? {
        try {
            val tagIndex = emvHex.indexOf(tag, ignoreCase = true)
            if (tagIndex == -1) return null

            // Vị trí sau tag
            val afterTag = tagIndex + tag.length
            if (afterTag + 2 > emvHex.length) return null

            // Length (1 byte = 2 hex chars)
            val lengthHex = emvHex.substring(afterTag, afterTag + 2)
            val length = lengthHex.toIntOrNull(16) ?: return null

            // Value
            val valueStart = afterTag + 2
            val valueEnd = valueStart + (length * 2)

            if (valueEnd > emvHex.length) return null

            return emvHex.substring(valueStart, valueEnd)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error extracting tag $tag")
            return null
        }
    }

    /**
     * Xác định POS Entry Mode
     */
    private fun getPosEntryMode(cardData: CardData): String {
        return when (cardData.type) {
            CardType.MAGNETIC -> "021" // Magnetic stripe
            CardType.CHIP -> "051" // Chip
            CardType.CONTACTLESS -> "071" // Contactless chip
        }
    }

    /**
     * Mask PAN for logging (show first 6 and last 4)
     */
    private fun maskPan(pan: String): String {
        if (pan.length < 10) return "****"
        return "${pan.take(6)}****${pan.takeLast(4)}"
    }
}

/**
 * Generate random request ID
 */
private fun generateRequestId(): String {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10)
}

data class CardData(
    val type: CardType,
    // Từ
    val cardNumber: String = "",
    val track1: String = "",
    val track2: String = "",
    val track3: String = "",
    val serviceCode: String = "",
    // Chip
    val atr: String = "",
    val cardTypeCode: Int = -1,
    val slotNumber: Int = -1,
    // NFC
    val uuid: String = "",
    val ats: String = "",
    val sak: String = "",
    val atqa: String = "",
    val nfcCardType: String = "",
    // EMV data (THÊM MỚI)
    val emvTlvData: String = ""
)

/**
 * Loại thẻ
 */
enum class CardType {
    MAGNETIC,      // Thẻ từ
    CHIP,          // Thẻ chip (IC)
    CONTACTLESS    // Thẻ không tiếp xúc (NFC)
}