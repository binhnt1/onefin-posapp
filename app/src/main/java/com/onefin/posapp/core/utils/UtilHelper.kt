package com.onefin.posapp.core.utils

import androidx.compose.ui.graphics.Color
import com.onefin.posapp.core.models.data.StatusInfo
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object UtilHelper {
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
    fun formatCurrency(input: String): String {
        if (input.isEmpty()) return ""

        val cleanInput = input.replace(".", "")
        val number = cleanInput.toLongOrNull() ?: return ""

        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
        return formatter.format(number)
    }
    fun formatCurrency(number: Long): String {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
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
}