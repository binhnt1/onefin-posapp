package com.onefin.posapp.core.models.data

import android.R
import com.google.gson.annotations.SerializedName

data class PaymentRequest(
    val message: String? = null,
    val billNumber: String? = null,
    val referenceId: String? = null,
    val isEnterPin: Boolean? = false,
    val additionalData: Map<String, Any>? = null,

    @SerializedName("Type")
    var typeValue: PaymentRequestType = PaymentRequestType.QR,

    @SerializedName("Action")
    var actionValue: PaymentAction = PaymentAction.SALE,

    @SerializedName("Amount")
    val amount: Long = 0L
) {
    val type: PaymentRequestType
        get() = PaymentRequestType.fromValue(typeValue.value)

    val action: PaymentAction
        get() = PaymentAction.fromValue(actionValue.value) ?: PaymentAction.SALE
}

enum class PaymentAction(val value: Int) {
    SALE(1),
    REFUND(2),
    CHANGE_PIN(3),
    CHECK_BALANCE(4),
    REPRINT_INVOICE(5),
    QUERY_TRANSACTION(6);

    companion object {
        fun fromValue(value: Int): PaymentAction? {
            return entries.find { it.value == value }
        }
    }
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