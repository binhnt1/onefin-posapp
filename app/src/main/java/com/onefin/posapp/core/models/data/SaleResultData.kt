package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.utils.UtilHelper
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

data class SaleResultData(

    @SerializedName("data")
    val data: Data? = null,

    @SerializedName("header")
    val header: Header? = null,

    @SerializedName("status")
    val status: Status? = null,

    @SerializedName("requestId")
    val requestId: String? = null,

    @SerializedName("requestData")
    val requestData: Any? = null,

) : Serializable {

    data class Data(
        @SerializedName("tc")
        val tc: String? = null,

        @SerializedName("aid")
        val aid: String? = null,

        @SerializedName("emv")
        val emv: String? = null,

        @SerializedName("refNo")
        val refNo: String? = null,

        @SerializedName("batchNo")
        val batchNo: String? = null,

        @SerializedName("traceNo")
        val traceNo: String? = null,

        @SerializedName("currency")
        val currency: String? = null,

        @SerializedName("cardBrand")
        val cardBrand: String? = null,

        @SerializedName("cardHolder")
        val cardHolder: String? = null,

        @SerializedName("cardNumber")
        val cardNumber: String? = null,

        @SerializedName("totalAmount")
        val totalAmount: String? = null,

        @SerializedName("approveCode")
        val approveCode: String? = null,

        @SerializedName("isoResponseCode")
        val isoResponseCode: String? = null,

        @SerializedName("cardMode")
        val cardMode: Int = 0, // 1: MAGNETIC, 2: IC, 4: CONTACTLESS
    ) : Serializable

    data class Header(
        @SerializedName("transId")
        val transId: String? = null,

        @SerializedName("provider")
        val provider: String? = null,

        @SerializedName("transType")
        val transType: String? = null,

        @SerializedName("terminalId")
        val terminalId: String? = null,

        @SerializedName("merchantId")
        val merchantId: String? = null,

        @SerializedName("merchantTransId")
        val merchantTransId: String? = null,

        @SerializedName("transmitsDateTime")
        val transmitsDateTime: String? = null
    ) : Serializable

    data class Status(
        @SerializedName("code")
        val code: String? = null,

        @SerializedName("message")
        val message: String? = null
    ) : Serializable

    /**
     * Parse transmitsDateTime from yyMMddHHmmss format to yyyy-MM-dd'T'HH:mm:ss format
     * Example: "260206080921" -> "2026-02-06T08:09:21"
     */
    private fun parseTransmitsDateTime(transmitsDateTime: String?): String {
        if (transmitsDateTime.isNullOrEmpty() || transmitsDateTime.length != 12) {
            return ""
        }

        return try {
            val inputFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(transmitsDateTime)
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun toTransaction(): Transaction {
        val requestData = UtilHelper.toMapOrNull(this.requestData)

        val merchant_request_data = UtilHelper.toMapOrNull(requestData?.get("merchant_request_data"))
        val billNumber = merchant_request_data?.get("bill_number")?.toString() ?: ""
        val type = requestData?.get("type")?.toString() ?: ""
        val formType = when (type) {
            "card" -> 1
            "qr" -> 2
            "member" -> 3
            else -> 1
        }
        return Transaction(
            feeTransAmt = 0L,
            settledDate = null,
            formType = formType,
            tc = this.data?.tc ?: "",
            aid = this.data?.aid ?: "",
            invoiceNumber = billNumber,
            refno = this.data?.refNo ?: "",
            traceNo = this.data?.traceNo ?: "",
            remark = this.status?.message ?: "",
            source = this.data?.cardBrand ?: "",
            cardMode = this.data?.cardMode ?: 4,
            batchNumber = this.data?.batchNo ?: "",
            accountName = this.data?.cardHolder ?: "",
            transactionId = this.header?.transId ?: "",
            accountNumber = this.data?.cardNumber ?: "",
            approvedCode = this.data?.approveCode ?: "",
            totalTransAmt = this.data?.totalAmount?.toLongOrNull() ?: 0L,
            transactionDate = parseTransmitsDateTime(this.header?.transmitsDateTime),
            processStatus = when (this.status?.code) {
                "00", "0000" -> 0
                else -> -1
            },
        )
    }
}