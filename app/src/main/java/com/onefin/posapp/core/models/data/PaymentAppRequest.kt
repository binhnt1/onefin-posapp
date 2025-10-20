package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PaymentAppRequest(

    /** Loại thanh toán: card / qr / member */
    @SerializedName("type")
    val type: String,

    /** Hành động: 1=Sale, 2=Refund, 3=Change PIN, 4=Check Balance, 5=Reprint Invoice, 6=Query Transaction */
    @SerializedName("action")
    val action: Int,

    @SerializedName("merchant_request_data")
    val merchantRequestData: MerchantRequestData? = null,
): Serializable

data class MerchantRequestData(

    @SerializedName("bill_number")
    val billNumber: String? = null,

    @SerializedName("reference_id")
    val referenceId: String? = null,

    @SerializedName("tid")
    var tid: String? = null,

    @SerializedName("mid")
    var mid: String? = null,

    @SerializedName("ccy")
    val ccy: String? = "704",

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("amount")
    val amount: Long = 0L,

    @SerializedName("tip")
    val tip: Long? = null,

    @SerializedName("is_enter_pin")
    val isEnterPin: Boolean? = null,

    @SerializedName("is_speak")
    val isSpeak: Boolean? = null,

    @SerializedName("additional_data")
    val additionalData: Map<String, Any>? = null
): Serializable
