package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class VoidResultData(

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
}