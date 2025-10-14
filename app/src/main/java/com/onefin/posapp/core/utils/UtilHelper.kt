package com.onefin.posapp.core.utils

import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentRequest
import com.onefin.posapp.core.models.data.PaymentRequestType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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

}