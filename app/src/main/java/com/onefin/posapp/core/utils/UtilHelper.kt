package com.onefin.posapp.core.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object UtilHelper {
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