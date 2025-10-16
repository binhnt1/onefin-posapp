package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName
import com.onefin.posapp.R
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.AppContext

data class ReportItem(
    @SerializedName("FormType")
    val formType: Int,  // 1: Card, 2: QR, 3: Member

    @SerializedName("Count")
    val count: Int,

    @SerializedName("Amount")
    val amount: Long
) {
    fun getFormattedAmount(): String {
        return "${UtilHelper.formatCurrency(amount.toString())}${AppContext.getString(R.string.currency_vnd)}"
    }

    fun getTransactionCountText(): String {
        return AppContext.getString(R.string.transaction_count, count)
    }

    fun getFormTypeText(): String {
        return when (formType) {
            1 -> AppContext.getString(R.string.form_type_card)
            2 -> AppContext.getString(R.string.form_type_qr)
            3 -> AppContext.getString(R.string.form_type_member)
            else -> AppContext.getString(R.string.form_type_unknown)
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

    fun getDisplayName(): String {
        return when (this) {
            TODAY -> AppContext.getString(R.string.date_range_today)
            YESTERDAY -> AppContext.getString(R.string.date_range_yesterday)
            LAST_7_DAYS -> AppContext.getString(R.string.date_range_last_7_days)
            LAST_30_DAYS -> AppContext.getString(R.string.date_range_last_30_days)
            CUSTOM -> AppContext.getString(R.string.date_range_custom)
        }
    }
}