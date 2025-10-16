package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Payment History Model
 */
data class PaymentHistory(
    @SerializedName("Transactions")
    val transactions: List<Transaction> = emptyList(),

    @SerializedName("Id")
    val id: Long = 0,

    @SerializedName("MID")
    val mid: String = "",

    @SerializedName("PaymentDate")
    val paymentDate: String = "",

    @SerializedName("PaymentDateTimeStamp")
    val paymentDateTimeStamp: Long = 0,

    @SerializedName("TransactionAmount")
    val transactionAmount: Double = 0.0,

    @SerializedName("TransactionCount")
    val transactionCount: Int = 0,

    @SerializedName("FeePayment")
    val feePayment: Double = 0.0,

    @SerializedName("ActuallyAmount")
    val actuallyAmount: Double = 0.0,

    @SerializedName("Bank")
    val bank: String = "",

    @SerializedName("Shift")
    val shift: String = "",

    @SerializedName("Serial")
    val serial: String = ""
) {
    /**
     * Format ngày theo định dạng dd/MM/yyyy
     */
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(paymentDate)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Format giờ theo định dạng HH:mm
     */
    fun getFormattedTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(paymentDate)
            date?.let { outputFormat.format(it) } ?: "00:00"
        } catch (e: Exception) {
            "00:00"
        }
    }

    /**
     * Format giờ và ca: "15:00, Ca 4"
     */
    fun getFormattedTimeAndShift(): String {
        return "${getFormattedTime()}, $shift - $bank"
    }

    /**
     * Format tổng tiền giao dịch với dấu phẩy và đơn vị
     */
    fun getFormattedTransactionAmount(): String {
        val formatter = NumberFormat.getInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "${formatter.format(transactionAmount)}đ"
    }

    /**
     * Format phí giao dịch với dấu phẩy và đơn vị
     */
    fun getFormattedFeePayment(): String {
        val formatter = NumberFormat.getInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "${formatter.format(feePayment)}đ"
    }

    /**
     * Format số tiền thực nhận với dấu phẩy và đơn vị
     */
    fun getFormattedActuallyAmount(): String {
        val formatter = NumberFormat.getInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "${formatter.format(actuallyAmount)}đ"
    }

    /**
     * Format đầy đủ ngày giờ: "dd/MM/yyyy HH:mm"
     */
    fun getFormattedDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(paymentDate)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Response wrapper for payment history list
 */
data class PaymentHistoryListResponse(
    val items: List<PaymentHistory> = emptyList(),
    val total: Int = 0
)