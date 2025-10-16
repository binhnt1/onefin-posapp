package com.onefin.posapp.core.models

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.onefin.posapp.R
import com.onefin.posapp.core.utils.UtilHelper

data class ReportItem(
    @SerializedName("FormType")
    val formType: Int,  // 1: Card, 2: QR, 3: Member

    @SerializedName("Count")
    val count: Int,

    @SerializedName("Amount")
    val amount: Long
) {
    fun getFormattedAmount(): String {
        return "${UtilHelper.formatCurrency(amount.toString())}đ"
    }

    fun getTransactionCountText(context: Context): String {
        return context.getString(R.string.transaction_count, count)
    }

    fun getFormTypeText(context: Context): String {
        return when (formType) {
            1 -> context.getString(R.string.form_type_card)
            2 -> context.getString(R.string.form_type_qr)
            3 -> context.getString(R.string.form_type_member)
            else -> context.getString(R.string.form_type_unknown)
        }
    }

    fun getIconResId(): Int {
        return when (formType) {
            1 -> R.drawable.icon_card
            2 -> R.drawable.icon_qr_new
            3 -> R.drawable.icon_card
            else -> R.drawable.icon_card
        }
    }
}

enum class DateRangeType {
    TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM;

    fun getDisplayName(context: Context): String {
        return when (this) {
            TODAY -> context.getString(R.string.date_range_today)
            YESTERDAY -> context.getString(R.string.date_range_yesterday)
            LAST_7_DAYS -> context.getString(R.string.date_range_last_7_days)
            LAST_30_DAYS -> context.getString(R.string.date_range_last_30_days)
            CUSTOM -> context.getString(R.string.date_range_custom)
        }
    }
}