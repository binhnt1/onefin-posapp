package com.onefin.posapp.core.models

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.annotations.SerializedName
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.StatusInfo
import com.onefin.posapp.core.utils.UtilHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transaction Model
 */
data class Transaction(
    @SerializedName("Remark")
    val remark: String = "",

    @SerializedName("FormType")
    val formType: Int = 0, // 1: Card, 2: QR, 3: Member

    @SerializedName("SettledDate")
    val settledDate: String? = null,

    @SerializedName("AccountName")
    val accountName: String = "",

    @SerializedName("AccountNumber")
    val accountNumber: String = "",

    @SerializedName("InvoiceNumber")
    val invoiceNumber: String = "",

    @SerializedName("Refno")
    val refno: String = "",

    @SerializedName("BatchNumber")
    val batchNumber: String = "",

    @SerializedName("TransactionId")
    val transactionId: String = "",

    @SerializedName("TotalTransAmt")
    val totalTransAmt: Long = 0L,

    @SerializedName("FeeTransAmt")
    val feeTransAmt: Long = 0L,

    @SerializedName("Serial")
    val serial: String = "",

    @SerializedName("ApprovedCode")
    val approvedCode: String = "",

    @SerializedName("Source")
    val source: String = "", // visa, master...

    @SerializedName("TransactionDate")
    val transactionDate: String = "",

    @SerializedName("ProcessStatus")
    val processStatus: Int = 0 // -1: Hủy, 0: Khởi tạo, 1: Đã tính phí, 2: Đã kết toán
) {
    fun showButtons(): Boolean {
        return when (processStatus) {
            -1 -> false
            else -> true
        }
    }

    fun getCardTypeText(context: Context): String {
        return when (source.lowercase()) {
            "visa" -> context.getString(R.string.card_type_visa)
            "master", "mastercard" -> context.getString(R.string.card_type_mastercard)
            "jcb" -> context.getString(R.string.card_type_jcb)
            "napas" -> context.getString(R.string.card_type_napas)
            "member" -> context.getString(R.string.card_type_member)
            "vietqr" -> context.getString(R.string.card_type_vietqr)
            else -> context.getString(R.string.card_type_default)
        }
    }

    fun getPaymentIconRes(): Int {
        if (formType == 2) return R.drawable.icon_qr
        return when (source.lowercase()) {
            "visa" -> R.drawable.icon_visa
            "master", "mastercard" -> R.drawable.icon_master
            "jcb" -> R.drawable.icon_jcb
            "napas" -> R.drawable.icon_napas
            "member" -> R.drawable.icon_member
            "vietqr" -> R.drawable.icon_qr
            else -> R.drawable.icon_visa
        }
    }

    fun getFormTypeText(context: Context): String {
        return when (formType) {
            1 -> context.getString(R.string.form_type_card)
            2 -> context.getString(R.string.form_type_qr)
            3 -> context.getString(R.string.form_type_member)
            else -> context.getString(R.string.form_type_unknown)
        }
    }

    fun getMaskedNumber(): String {
        if (accountNumber.length < 4) return accountNumber
        val last4 = accountNumber.takeLast(4)
        val first4 = accountNumber.take(4)
        return "$first4 **** **** $last4"
    }

    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(transactionDate)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getFormattedTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault())
            val date = inputFormat.parse(transactionDate)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getFormattedAmount(): String {
        return UtilHelper.formatCurrency(totalTransAmt, "đ")
    }

    fun getStatusInfo(context: Context): StatusInfo {
        return when (processStatus) {
            -1 -> StatusInfo(
                text = context.getString(R.string.status_canceled),
                textColor = Color(0xFFB42318),
                backgroundColor = Color(0xFFFEF3F2)
            )
            0, 1 -> StatusInfo(
                text = context.getString(R.string.status_success),
                textColor = Color(0xFF067647),
                backgroundColor = Color(0xFFECFDF3)
            )
            -3, -2, 2, 3, 4, 5, 6, 7 -> StatusInfo(
                text = context.getString(R.string.status_settled),
                textColor = Color(0xFF175CD3),
                backgroundColor = Color(0xFFEFF8FF)
            )
            else -> StatusInfo(
                text = context.getString(R.string.status_unknown),
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
        }
    }

    fun getReceiptStatusInfo(context: Context): StatusInfo {
        return when (processStatus) {
            -1 -> StatusInfo(
                text = context.getString(R.string.status_canceled),
                textColor = Color(0xFFB42318),
                backgroundColor = Color(0xFFFEF3F2)
            )
            0, 1 -> StatusInfo(
                text = context.getString(R.string.status_success),
                textColor = Color(0xFF067647),
                backgroundColor = Color(0xFFECFDF3)
            )
            -3, -2, 2, 3, 4, 5, 6, 7 -> StatusInfo(
                text = context.getString(R.string.status_settled),
                textColor = Color(0xFF175CD3),
                backgroundColor = Color(0xFFEFF8FF)
            )
            else -> StatusInfo(
                text = context.getString(R.string.status_unknown),
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
        }
    }

    fun getFormattedSettledDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateString = settledDate ?: ""
            if (dateString.isEmpty())
                return  ""
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getFormattedTransactionDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(transactionDate)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Response wrapper for transaction list
 */
data class TransactionListResponse(
    val items: List<Transaction> = emptyList(),
    val total: Int = 0
)