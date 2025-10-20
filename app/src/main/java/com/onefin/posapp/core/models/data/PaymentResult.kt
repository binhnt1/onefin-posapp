package com.onefin.posapp.core.models.data

/**
 * Sealed class representing the result of a payment operation
 * Replaces simple callback with structured result
 */
sealed class PaymentResult {

    /**
     * Successful payment transaction
     */
    data class Success(
        val requestSale: RequestSale
    ) : PaymentResult()

    /**
     * Failed payment transaction with detailed error information
     */
    data class Error(
        val type: com.onefin.posapp.core.managers.helpers.PaymentErrorHandler.ErrorType,
        val vietnameseMessage: String,
        val technicalMessage: String? = null,
        val errorCode: String? = null
    ) : PaymentResult() {

        /**
         * Get user-friendly message (Vietnamese only)
         */
        fun getDisplayMessage(): String = vietnameseMessage

        /**
         * Get full message with technical details
         */
        fun getFullMessage(): String = buildString {
            append(vietnameseMessage)
            technicalMessage?.let { append(" ($it)") }
            errorCode?.let { append(" [Code: $it]") }
        }

        companion object {
            /**
             * Create Error from PaymentError
             */
            fun from(paymentError: com.onefin.posapp.core.managers.helpers.PaymentErrorHandler.PaymentError): Error {
                return Error(
                    type = paymentError.type,
                    vietnameseMessage = paymentError.vietnameseMessage,
                    technicalMessage = paymentError.technicalMessage,
                    errorCode = paymentError.errorCode
                )
            }

            /**
             * Create Error from ErrorType with optional details
             */
            fun from(
                errorType: com.onefin.posapp.core.managers.helpers.PaymentErrorHandler.ErrorType,
                technicalMessage: String? = null,
                errorCode: String? = null
            ): Error {
                return Error(
                    type = errorType,
                    vietnameseMessage = com.onefin.posapp.core.managers.helpers.PaymentErrorHandler.getVietnameseMessage(errorType),
                    technicalMessage = technicalMessage,
                    errorCode = errorCode
                )
            }
        }
    }
}

/**
 * Extension functions for easier handling
 */
fun PaymentResult.onSuccess(action: (RequestSale) -> Unit): PaymentResult {
    if (this is PaymentResult.Success) {
        action(requestSale)
    }
    return this
}

fun PaymentResult.onError(action: (PaymentResult.Error) -> Unit): PaymentResult {
    if (this is PaymentResult.Error) {
        action(this)
    }
    return this
}

/**
 * Get result or throw exception
 */
fun PaymentResult.getOrThrow(): RequestSale {
    return when (this) {
        is PaymentResult.Success -> requestSale
        is PaymentResult.Error -> throw Exception(getFullMessage())
    }
}

/**
 * Get result or null
 */
fun PaymentResult.getOrNull(): RequestSale? {
    return when (this) {
        is PaymentResult.Success -> requestSale
        is PaymentResult.Error -> null
    }
}