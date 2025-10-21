package com.onefin.posapp.core.models.data

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

enum class PaymentState {
    INITIALIZING,
    WAITING_CARD,
    CARD_DETECTED,
    ENTERING_PIN,
    WAITING_SIGNATURE,
    PROCESSING,
    SUCCESS,
    ERROR
}