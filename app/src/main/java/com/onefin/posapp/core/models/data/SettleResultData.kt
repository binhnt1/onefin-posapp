package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class SettleResultData(

    @SerializedName("data")
    val data: Data? = null,

    @SerializedName("header")
    val header: Header? = null,

    @SerializedName("status")
    val status: Status? = null

) : Serializable {

    data class Data(
        @SerializedName("refNo")
        val refNo: String? = null,

        @SerializedName("batchNo")
        val batchNo: String? = null,

        @SerializedName("traceNo")
        val traceNo: String? = null,

        @SerializedName("currency")
        val currency: String? = null,

        @SerializedName("saleAmount")
        val saleAmount: String? = null,

        @SerializedName("voidAmount")
        val voidAmount: String? = null,

        @SerializedName("approveCode")
        val approveCode: String? = null,

        @SerializedName("totalAmount")
        val totalAmount: String? = null,

        @SerializedName("saleQuantity")
        val saleQuantity: String? = null,

        @SerializedName("voidQuantity")
        val voidQuantity: String? = null,

        @SerializedName("totalQuantity")
        val totalQuantity: String? = null,

        @SerializedName("isoResponseCode")
        val isoResponseCode: String? = null
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

    fun isSuccess(): Boolean {
        return status?.code == "00"
    }

    /**
     * Get formatted settlement time
     * Format: YYMMDDHHmmss -> DD/MM/YYYY HH:mm:ss
     */
    fun getFormattedDateTime(): String? {
        val dateTime = header?.transmitsDateTime ?: return null
        if (dateTime.length != 12) return dateTime

        try {
            val year = "20${dateTime.substring(0, 2)}"
            val month = dateTime.substring(2, 4)
            val day = dateTime.substring(4, 6)
            val hour = dateTime.substring(6, 8)
            val minute = dateTime.substring(8, 10)
            val second = dateTime.substring(10, 12)

            return "$day/$month/$year $hour:$minute:$second"
        } catch (e: Exception) {
            return dateTime
        }
    }

    /**
     * Get net amount (sale - void)
     */
    fun getNetAmount(): Long {
        val sale = data?.saleAmount?.toLongOrNull() ?: 0L
        val void = data?.voidAmount?.toLongOrNull() ?: 0L
        return sale - void
    }

    /**
     * Get total transaction count
     */
    fun getTotalCount(): Int {
        return data?.totalQuantity?.toIntOrNull() ?: 0
    }
}