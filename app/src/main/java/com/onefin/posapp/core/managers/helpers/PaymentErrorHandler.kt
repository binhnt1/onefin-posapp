package com.onefin.posapp.core.managers.helpers

/**
 * Centralized error handler for payment operations with Vietnamese messages
 */
object PaymentErrorHandler {

    /**
     * Error types for card operations
     */
    enum class ErrorType {
        // Card Reading Errors
        CARD_READ_TIMEOUT,
        CARD_READ_CANCELLED,
        CARD_REMOVED,
        CARD_READ_FAILED,
        MAGNETIC_STRIPE_ERROR,
        INVALID_CARD_FORMAT,

        // EMV Transaction Errors
        EMV_TIMEOUT,
        EMV_DATA_INVALID,
        EMV_APP_BLOCKED,
        EMV_NO_APP,
        EMV_USER_CANCEL,
        EMV_EXPIRED_CARD,
        EMV_TRANS_NOT_ACCEPTED,
        EMV_TRANSACTION_TERMINATED,
        EMV_COMMAND_TIMEOUT,
        EMV_INIT_FAILED,
        EMV_TRANSACTION_FAILED,

        // Card Status Errors (ISO 8583 Response Codes)
        CARD_BLOCKED,              // 05 - Do not honor
        INSUFFICIENT_FUNDS,        // 51 - Not sufficient funds
        EXPIRED_CARD,              // 54 - Expired card
        INVALID_CARD,              // 14 - Invalid card number
        PIN_TRIES_EXCEEDED,        // 75 - Allowable PIN tries exceeded
        TRANSACTION_NOT_PERMITTED, // 57 - Transaction not permitted to cardholder
        EXCEEDS_WITHDRAWAL_LIMIT,  // 61 - Exceeds withdrawal amount limit
        RESTRICTED_CARD,           // 62 - Restricted card
        SECURITY_VIOLATION,        // 63 - Security violation
        SUSPECTED_FRAUD,           // 59 - Suspected fraud

        // System Errors
        SERVICE_NOT_CONNECTED,
        SDK_INIT_FAILED,
        PAYMENT_REQUEST_NOT_INITIALIZED,
        EMV_PROCESSOR_NOT_INITIALIZED,
        UNKNOWN_ERROR
    }

    /**
     * Get Vietnamese message for error type
     */
    fun getVietnameseMessage(errorType: ErrorType): String {
        return when (errorType) {
            // Card Reading Errors
            ErrorType.CARD_READ_TIMEOUT -> "Hết thời gian chờ đọc thẻ"
            ErrorType.CARD_READ_CANCELLED -> "Đã hủy đọc thẻ"
            ErrorType.CARD_REMOVED -> "Thẻ đã bị rút ra"
            ErrorType.CARD_READ_FAILED -> "Không đọc được thẻ"
            ErrorType.MAGNETIC_STRIPE_ERROR -> "Lỗi đọc từ tính thẻ"
            ErrorType.INVALID_CARD_FORMAT -> "Định dạng thẻ không hợp lệ"

            // EMV Transaction Errors
            ErrorType.EMV_TIMEOUT -> "Hết thời gian xử lý giao dịch"
            ErrorType.EMV_DATA_INVALID -> "Dữ liệu thẻ không hợp lệ"
            ErrorType.EMV_APP_BLOCKED -> "Ứng dụng thẻ bị khóa"
            ErrorType.EMV_NO_APP -> "Không tìm thấy ứng dụng thanh toán trên thẻ"
            ErrorType.EMV_USER_CANCEL -> "Người dùng đã hủy giao dịch"
            ErrorType.EMV_EXPIRED_CARD -> "Thẻ đã hết hạn"
            ErrorType.EMV_TRANS_NOT_ACCEPTED -> "Giao dịch không được chấp nhận"
            ErrorType.EMV_TRANSACTION_TERMINATED -> "Giao dịch bị gián đoạn"
            ErrorType.EMV_COMMAND_TIMEOUT -> "Hết thời gian chờ phản hồi từ thẻ"

            // Card Status Errors
            ErrorType.CARD_BLOCKED -> "Thẻ đã bị khóa. Vui lòng liên hệ ngân hàng"
            ErrorType.INSUFFICIENT_FUNDS -> "Số dư tài khoản không đủ"
            ErrorType.EXPIRED_CARD -> "Thẻ đã hết hạn sử dụng"
            ErrorType.INVALID_CARD -> "Số thẻ không hợp lệ"
            ErrorType.PIN_TRIES_EXCEEDED -> "Nhập sai mã PIN quá số lần cho phép"
            ErrorType.TRANSACTION_NOT_PERMITTED -> "Giao dịch không được phép với thẻ này"
            ErrorType.EXCEEDS_WITHDRAWAL_LIMIT -> "Vượt quá hạn mức giao dịch"
            ErrorType.RESTRICTED_CARD -> "Thẻ bị hạn chế sử dụng"
            ErrorType.SECURITY_VIOLATION -> "Vi phạm bảo mật"
            ErrorType.SUSPECTED_FRAUD -> "Giao dịch bị nghi ngờ gian lận"

            // System Errors
            ErrorType.SERVICE_NOT_CONNECTED -> "Dịch vụ chưa kết nối"
            ErrorType.SDK_INIT_FAILED -> "Lỗi khởi tạo hệ thống thanh toán"
            ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED -> "Yêu cầu thanh toán chưa được khởi tạo"
            ErrorType.EMV_PROCESSOR_NOT_INITIALIZED -> "Bộ xử lý EMV chưa được khởi tạo"
            ErrorType.UNKNOWN_ERROR -> "Lỗi không xác định"
            ErrorType.EMV_INIT_FAILED -> "Lỗi khởi tạo hệ thống thanh toán"
            ErrorType.EMV_TRANSACTION_FAILED ->  "Giao dịch thất bại"
        }
    }

    /**
     * Map card reading error code to ErrorType
     */
    fun mapCardReadError(code: Int): ErrorType {
        return when (code) {
            -1 -> ErrorType.CARD_READ_TIMEOUT
            -2 -> ErrorType.CARD_READ_CANCELLED
            -3 -> ErrorType.CARD_REMOVED
            -4 -> ErrorType.CARD_READ_FAILED
            else -> ErrorType.UNKNOWN_ERROR
        }
    }

    /**
     * Map magnetic stripe error code to ErrorType
     */
    fun mapMagneticStripeError(code: Int): ErrorType {
        return when (code) {
            -1, -2, -3, -4 -> ErrorType.MAGNETIC_STRIPE_ERROR
            else -> ErrorType.UNKNOWN_ERROR
        }
    }

    /**
     * Map EMV result code to ErrorType
     */
    fun mapEmvResultCode(resultCode: Int): ErrorType {
        return when (resultCode) {
            -1 -> ErrorType.EMV_TIMEOUT
            -2 -> ErrorType.EMV_DATA_INVALID
            -3 -> ErrorType.EMV_APP_BLOCKED
            -4 -> ErrorType.EMV_NO_APP
            -5 -> ErrorType.EMV_USER_CANCEL
            -6 -> ErrorType.EMV_EXPIRED_CARD
            -7 -> ErrorType.EMV_TRANS_NOT_ACCEPTED
            -4002 -> ErrorType.EMV_TRANSACTION_TERMINATED
            -4100 -> ErrorType.EMV_COMMAND_TIMEOUT
            else -> ErrorType.UNKNOWN_ERROR
        }
    }

    /**
     * Map ISO 8583 response code to ErrorType
     * Used when online authorization returns response code
     */
    fun mapIsoResponseCode(responseCode: String): ErrorType {
        return when (responseCode) {
            "05" -> ErrorType.CARD_BLOCKED
            "51" -> ErrorType.INSUFFICIENT_FUNDS
            "54" -> ErrorType.EXPIRED_CARD
            "14" -> ErrorType.INVALID_CARD
            "75" -> ErrorType.PIN_TRIES_EXCEEDED
            "57" -> ErrorType.TRANSACTION_NOT_PERMITTED
            "61" -> ErrorType.EXCEEDS_WITHDRAWAL_LIMIT
            "62" -> ErrorType.RESTRICTED_CARD
            "63" -> ErrorType.SECURITY_VIOLATION
            "59" -> ErrorType.SUSPECTED_FRAUD
            else -> ErrorType.UNKNOWN_ERROR
        }
    }

    /**
     * Create error with both Vietnamese and technical message
     */
    data class PaymentError(
        val type: ErrorType,
        val vietnameseMessage: String,
        val technicalMessage: String? = null,
        val errorCode: String? = null
    ) {
        fun getDisplayMessage(): String {
            return vietnameseMessage
        }

        fun getFullMessage(): String {
            return buildString {
                append(vietnameseMessage)
                technicalMessage?.let { append(" ($it)") }
                errorCode?.let { append(" [Code: $it]") }
            }
        }
    }

    /**
     * Create PaymentError from ErrorType
     */
    fun createError(
        errorType: ErrorType,
        technicalMessage: String? = null,
        errorCode: String? = null
    ): PaymentError {
        return PaymentError(
            type = errorType,
            vietnameseMessage = getVietnameseMessage(errorType),
            technicalMessage = technicalMessage,
            errorCode = errorCode
        )
    }
}