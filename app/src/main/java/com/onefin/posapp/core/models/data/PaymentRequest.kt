package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class PaymentRequest(
    @SerializedName("Type")
    val typeValue: Int = 0,

    @SerializedName("RequestId")
    val requestId: String? = null,

    @SerializedName("Amount")
    val amount: Long = 0L
) {
    val type: PaymentRequestType
        get() = PaymentRequestType.fromValue(typeValue)
}

enum class PaymentRequestType(val value: Int) {
    CARD(1),
    QR(2),
    MEMBER(3),
    UNKNOWN(0);

    companion object {
        fun fromValue(value: Int): PaymentRequestType {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}