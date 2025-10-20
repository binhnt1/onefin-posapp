package com.onefin.posapp.core.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class CurrencyInputFormatter(private val editText: EditText) : TextWatcher {

    private var isFormatting = false
    private val decimalFormat: DecimalFormat

    init {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'
        decimalFormat = DecimalFormat("#,###", symbols)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }

    override fun afterTextChanged(editable: Editable?) {
        if (isFormatting) return

        isFormatting = true

        try {
            val originalText = editable.toString()

            if (originalText.isEmpty()) {
                isFormatting = false
                return
            }

            // Xóa tất cả dấu chấm
            val digits = originalText.replace(".", "")

            // Chỉ giữ lại số
            if (!digits.matches(Regex("^\\d+$"))) {
                editable?.clear()
                editable?.append(digits.replace(Regex("\\D"), ""))
                isFormatting = false
                return
            }

            // Format với dấu phân cách hàng nghìn
            val number = digits.toLongOrNull() ?: 0L
            val formatted = decimalFormat.format(number)

            // Cập nhật text
            editable?.clear()
            editable?.append(formatted)

            // Đặt cursor ở cuối
            editText.setSelection(formatted.length)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isFormatting = false
        }
    }
}

// Extension function để dễ sử dụng
fun EditText.addCurrencyFormatter() {
    this.addTextChangedListener(CurrencyInputFormatter(this))
}