package com.onefin.posapp.core.managers.helpers

object PaymentTTSHelper {

    /**
     * Get TTS-friendly message for error type
     * These are optimized for speech synthesis
     */
    fun getTTSMessage(errorType: PaymentErrorHandler.ErrorType): String {
        return when (errorType) {
            // Card Reading Errors
            PaymentErrorHandler.ErrorType.CARD_READ_TIMEOUT ->
                "Hết thời gian chờ. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.CARD_READ_CANCELLED ->
                "Đã hủy đọc thẻ"

            PaymentErrorHandler.ErrorType.CARD_REMOVED ->
                "Thẻ đã bị rút ra. Vui lòng giữ thẻ cho đến khi hoàn tất"

            PaymentErrorHandler.ErrorType.CARD_READ_FAILED ->
                "Không đọc được thẻ. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.MAGNETIC_STRIPE_ERROR ->
                "Lỗi đọc từ tính. Vui lòng lau sạch thẻ và thử lại"

            PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT ->
                "Thẻ không hợp lệ"

            // EMV Transaction Errors
            PaymentErrorHandler.ErrorType.EMV_TIMEOUT ->
                "Hết thời gian xử lý. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.EMV_DATA_INVALID ->
                "Dữ liệu thẻ không hợp lệ. Vui lòng thử lại hoặc sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.EMV_APP_BLOCKED ->
                "Ứng dụng thẻ bị khóa. Vui lòng liên hệ ngân hàng"

            PaymentErrorHandler.ErrorType.EMV_NO_APP ->
                "Không tìm thấy ứng dụng thanh toán. Vui lòng sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.EMV_USER_CANCEL ->
                "Người dùng đã hủy giao dịch"

            PaymentErrorHandler.ErrorType.EMV_EXPIRED_CARD ->
                "Thẻ đã hết hạn. Vui lòng sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.EMV_TRANS_NOT_ACCEPTED ->
                "Giao dịch không được chấp nhận"

            PaymentErrorHandler.ErrorType.EMV_TRANSACTION_TERMINATED ->
                "Giao dịch bị gián đoạn. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.EMV_COMMAND_TIMEOUT ->
                "Hết thời gian chờ phản hồi. Vui lòng thử lại"

            // Card Status Errors (Critical - need clear instructions)
            PaymentErrorHandler.ErrorType.CARD_BLOCKED ->
                "Thẻ đã bị khóa. Vui lòng liên hệ ngân hàng để được hỗ trợ"

            PaymentErrorHandler.ErrorType.INSUFFICIENT_FUNDS ->
                "Số dư tài khoản không đủ. Vui lòng nạp tiền hoặc sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.EXPIRED_CARD ->
                "Thẻ đã hết hạn sử dụng. Vui lòng sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.INVALID_CARD ->
                "Số thẻ không hợp lệ"

            PaymentErrorHandler.ErrorType.PIN_TRIES_EXCEEDED ->
                "Nhập sai mã pin quá nhiều lần. Thẻ đã bị khóa. Vui lòng liên hệ ngân hàng"

            PaymentErrorHandler.ErrorType.TRANSACTION_NOT_PERMITTED ->
                "Giao dịch không được phép với thẻ này. Vui lòng sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.EXCEEDS_WITHDRAWAL_LIMIT ->
                "Vượt quá hạn mức giao dịch. Vui lòng giảm số tiền hoặc sử dụng thẻ khác"

            PaymentErrorHandler.ErrorType.RESTRICTED_CARD ->
                "Thẻ bị hạn chế sử dụng. Vui lòng liên hệ ngân hàng"

            PaymentErrorHandler.ErrorType.SECURITY_VIOLATION ->
                "Vi phạm bảo mật. Giao dịch bị từ chối"

            PaymentErrorHandler.ErrorType.SUSPECTED_FRAUD ->
                "Giao dịch bị nghi ngờ gian lận. Vui lòng liên hệ ngân hàng"

            // System Errors
            PaymentErrorHandler.ErrorType.SERVICE_NOT_CONNECTED ->
                "Dịch vụ chưa kết nối. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.SDK_INIT_FAILED ->
                "Lỗi khởi tạo hệ thống. Vui lòng khởi động lại ứng dụng"

            PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED ->
                "Lỗi hệ thống. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED ->
                "Lỗi hệ thống. Vui lòng thử lại"

            PaymentErrorHandler.ErrorType.UNKNOWN_ERROR ->
                "Lỗi không xác định. Vui lòng thử lại"
        }
    }

    /**
     * Get TTS message for success
     */
    fun getSuccessTTSMessage(amount: Long): String {
        return "Giao dịch thành công. Số tiền ${formatAmount(amount)} đồng"
    }

    /**
     * Format amount for TTS (add thousand separators in words if needed)
     */
    private fun formatAmount(amount: Long): String {
        return when {
            amount >= 1_000_000 -> {
                val millions = amount / 1_000_000
                val remainder = (amount % 1_000_000) / 1_000
                if (remainder > 0) {
                    "$millions triệu $remainder nghìn"
                } else {
                    "$millions triệu"
                }
            }
            amount >= 1_000 -> {
                val thousands = amount / 1_000
                "$thousands nghìn"
            }
            else -> amount.toString()
        }
    }

    /**
     * Check if error type should allow retry
     * Some errors (like card blocked) shouldn't show retry button
     */
    fun shouldAllowRetry(errorType: PaymentErrorHandler.ErrorType): Boolean {
        return when (errorType) {
            // Don't allow retry for permanent card issues
            PaymentErrorHandler.ErrorType.CARD_BLOCKED,
            PaymentErrorHandler.ErrorType.PIN_TRIES_EXCEEDED,
            PaymentErrorHandler.ErrorType.EXPIRED_CARD,
            PaymentErrorHandler.ErrorType.INVALID_CARD,
            PaymentErrorHandler.ErrorType.EMV_APP_BLOCKED -> false

            // Allow retry for temporary issues
            else -> true
        }
    }
}