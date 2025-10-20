package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import com.onefin.posapp.BuildConfig
import java.io.Serializable

data class PaymentAppResponse(

    @SerializedName("type")
    val type: String,

    @SerializedName("action")
    val action: Int,

    @SerializedName("payment_response_data")
    val paymentResponseData: PaymentResponseData
) : Serializable

data class PaymentResponseData(

    /** Mã trạng thái: 00=Thành công, 99=Lỗi, 68=Timeout, etc. */
    @SerializedName("status")
    val status: String,

    @SerializedName("bill_number")
    val billNumber: String? = null,

    @SerializedName("reference_id")
    val referenceId: String? = null,

    @SerializedName("ref_no")
    val refNo: String? = null,

    /** 1=Debug, 3=Release */
    @SerializedName("environment")
    val environment: Int = if (BuildConfig.DEBUG) 1 else 3,

    @SerializedName("is_sign")
    val isSign: Boolean = true,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("additional_data")
    val additionalData: Map<String, Any>? = null,

    // Thêm các field hữu ích khác
    @SerializedName("transaction_id")
    val transactionId: String? = null,

    @SerializedName("transaction_time")
    val transactionTime: String? = null,

    @SerializedName("amount")
    val amount: Long? = null,

    @SerializedName("tip")
    val tip: Long? = null,

    @SerializedName("tid")
    val tid: String? = null,

    @SerializedName("mid")
    val mid: String? = null,

    @SerializedName("ccy")
    val ccy: String? = null
) : Serializable

// Status codes constants
object PaymentStatusCode {
    const val SUCCESS = "00"
    const val ERROR = "99"
    const val TIMEOUT = "68"
    const val CANCELLED = "96"
    const val INVALID_DATA = "30"
    const val LOGIN_FAILED = "51"
    const val NOT_LOGGED_IN = "52"
}