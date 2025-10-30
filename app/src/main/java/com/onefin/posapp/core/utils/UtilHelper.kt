package com.onefin.posapp.core.utils

import androidx.compose.ui.graphics.Color
import com.onefin.posapp.core.models.data.StatusInfo
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.random.Random

object UtilHelper {

    fun maskCardNumber(cardNumber: String?): String {
        if (cardNumber.isNullOrEmpty())
            return ""
        if (cardNumber.length < 12) return cardNumber
        return "${cardNumber.take(6)}******${cardNumber.takeLast(4)}"
    }
    fun getCurrentTimeStamp(): String {
        return System.currentTimeMillis().toString()
    }
    fun generateRandomBillNumber(): String {
        return buildString {
            append("TOT")
            repeat(7) {
                append(Random.nextInt(0, 10))
            }
        }
    }
    fun formatCurrency(input: String, unit: String? = "đ"): String {
        if (input.isEmpty()) return ""

        val cleanInput = input.replace(".", "")
        val number = cleanInput.toLongOrNull() ?: return ""

        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
        if (unit != null) {
            return "${formatter.format(number)} $unit"
        }
        return formatter.format(number)
    }
    fun formatCurrency(number: Long, unit: String? = "đ"): String {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
        if (unit != null) {
            return "${formatter.format(number)} $unit"
        }
        return formatter.format(number)
    }
    fun formatCardNumber(cardNumber: String?): String {
        if (cardNumber.isNullOrEmpty()) return ""
        if (cardNumber.contains("*")) return cardNumber

        return if (cardNumber.length >= 16) {
            "${cardNumber.take(4)} **** **** ${cardNumber.takeLast(4)}"
        } else {
            cardNumber
        }
    }
    fun getStatusInfo(processStatus: Int?): StatusInfo {
        return when (processStatus) {
            -1 -> StatusInfo(
                text = "Đã hủy",
                textColor = Color(0xFFDC2626),
                backgroundColor = Color(0xFFFEE2E2)
            )
            0 -> StatusInfo(
                text = "Khởi tạo",
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
            1 -> StatusInfo(
                text = "Đã tính phí",
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            2 -> StatusInfo(
                text = "Đã kết toán",
                textColor = Color(0xFF16A34A),
                backgroundColor = Color(0xFFDCFCE7)
            )
            else -> StatusInfo(
                text = "Không xác định",
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
        }
    }

    fun getTodayDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getYesterdayDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun getDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun byteArrayToHexString(byteArray: ByteArray): String {
        return byteArray.joinToString("") { "%02X".format(it) }
    }
    fun stringToHexString(input: String): String {
        return input.toByteArray(Charsets.US_ASCII)
            .joinToString("") { "%02X".format(it) }
    }
    fun stringToByteArray(str: String, maxLength: Int): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        return if (bytes.size > maxLength) {
            bytes.copyOf(maxLength)
        } else {
            bytes.copyOf(maxLength) // Pads with zeros
        }
    }

    fun decimalStringToByteArray(decimal: String, length: Int): ByteArray {
        return try {
            val value = decimal.toLongOrNull() ?: 0L
            val result = ByteArray(length)
            var temp = value
            for (i in length - 1 downTo 0) {
                result[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            result
        } catch (e: Exception) {
            ByteArray(length)
        }
    }

    fun stringToByte(str: String): Byte {
        return try {
            str.toIntOrNull()?.toByte() ?: 0.toByte()
        } catch (e: Exception) {
            0.toByte()
        }
    }
}