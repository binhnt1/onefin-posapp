package com.onefin.posapp.core.managers

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentRequest
import com.onefin.posapp.core.models.data.PaymentRequestType
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.models.data.PaymentSuccessData
import com.onefin.posapp.core.models.data.RabbitNotify
import com.onefin.posapp.core.models.data.RabbitNotifyType
import com.onefin.posapp.core.services.RabbitMQService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.external.ExternalPaymentActivity
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.LogoutDialogActivity
import com.onefin.posapp.ui.payment.PaymentSuccessActivity
import com.onefin.posapp.ui.transaction.TransparentPaymentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RabbitMQManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson,
    private val ttsManager: TTSManager,
    private val paymentHelper: PaymentHelper,
    private val storageService: StorageService,
    private val snackbarManager: SnackbarManager,
    private val rabbitMQService: RabbitMQService,
    private val activityTracker: ActivityTracker,
) {

    companion object {
        private const val TAG = "RabbitMQManager"
    }

    private var isInitialized = false

    fun startAfterLogin() {
        if (isInitialized) {
            return
        }

        val serial = storageService.getSerial()
        if (serial.isNullOrEmpty()) {
            return
        }

        rabbitMQService.setOnMessageListener { messageJson ->
            handleRabbitMessage(messageJson)
        }

        rabbitMQService.startListening(serial)
        isInitialized = true
    }

    fun stop() {
        rabbitMQService.stopListening()
        isInitialized = false
    }

    fun restart() {
        stop()
        startAfterLogin()
    }

    fun isConnected(): Boolean {
        return rabbitMQService.isConnected()
    }

    private fun handleLogout(notify: RabbitNotify) {
        LogoutDialogActivity.start(context, notify.title, notify.content)
        try {
            // Delay 5s để user xem dialog
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stop()
                storageService.clearAll()

                val intent = Intent(context, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
            }, 5000)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during logout")
        }
    }

    private fun handleRabbitMessage(messageJson: String) {
        try {
            val notify = gson.fromJson(messageJson, RabbitNotify::class.java)
            if (notify != null) {
                val notifyType = RabbitNotifyType.fromValue(notify.type)
                when (notifyType) {
                    RabbitNotifyType.LOGOUT,
                    RabbitNotifyType.LOCKED,
                    RabbitNotifyType.LOCK_USER -> {
                        handleLogout(notify)
                    }
                    RabbitNotifyType.QR_SUCCESS -> {
                        handleQRSuccess(notify)
                    }
                    RabbitNotifyType.REQUEST_PAYMENT -> {
                        handlePaymentRequest(notify)
                        snackbarManager.showFromRabbitNotify(notify)
                    }
                    else -> {
                        snackbarManager.showFromRabbitNotify(notify)
                    }
                }
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling rabbit message")
        }
    }

    private fun handleQRSuccess(notify: RabbitNotify) {
        ttsManager.speak(notify.content)

        val jsonObject = notify.jsonObject

        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                // Clear context và request
                storageService.clearExternalPaymentContext()

                // Parse thông tin từ jsonObject (nếu có)
                var transactionId: String? = null
                var transactionTime: String? = null
                var refNo: String? = null
                var additionalData: Map<String, Any>? = null

                if (!jsonObject.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(jsonObject)
                        refNo = json.optString("TransactionId", null)
                        transactionId = json.optString("TransactionId", null)
                        transactionTime = json.optString("TransactionTime", null)

                        // Parse additional data nếu có
                        val additionalMap = mutableMapOf<String, Any>()
                        json.keys().forEach { key ->
                            if (key !in listOf("TransactionId", "TransactionTime", "RefNo")) {
                                json.opt(key)?.let { value ->
                                    additionalMap[key] = value
                                }
                            }
                        }
                        if (additionalMap.isNotEmpty()) {
                            additionalData = additionalMap
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error parsing jsonObject")
                    }
                }

                // Tạo response từ thông tin request đã lưu + thông tin từ QR success
                val responseData = PaymentResponseData(
                    refNo = refNo,
                    transactionId = transactionId,
                    additionalData = additionalData,
                    status = PaymentStatusCode.SUCCESS,
                    description = "Thanh toán thành công",
                    tip = pendingRequest.merchantRequestData.tip,
                    tid = pendingRequest.merchantRequestData.tid,
                    mid = pendingRequest.merchantRequestData.mid,
                    ccy = pendingRequest.merchantRequestData.ccy,
                    amount = pendingRequest.merchantRequestData.amount,
                    transactionTime = transactionTime ?: notify.dateTime,
                    billNumber = pendingRequest.merchantRequestData.billNumber,
                    referenceId = pendingRequest.merchantRequestData.referenceId,
                )

                val response = PaymentAppResponse(
                    type = pendingRequest.type,
                    action = pendingRequest.action,
                    paymentResponseData = responseData
                )

                // Return về external app
                if (activityTracker.isActivityOfType(QRCodeDisplayActivity::class.java)) {
                    val activity = activityTracker.getCurrentActivity()
                    val resultIntent = Intent().apply {
                        putExtra(
                            ExternalPaymentActivity.RESULT_PAYMENT_RESPONSE_DATA,
                            gson.toJson(response)
                        )
                    }
                    activity?.setResult(android.app.Activity.RESULT_OK, resultIntent)
                    activity?.finish()
                    return
                }
            } else {
                // Không tìm thấy pending request
                Timber.tag(TAG).w("No pending request found for external payment")
                storageService.clearExternalPaymentContext()

                val errorResponse = PaymentAppResponse(
                    type = "qr",
                    action = 1,
                    paymentResponseData = PaymentResponseData(
                        status = PaymentStatusCode.ERROR,
                        description = "Không tìm thấy thông tin giao dịch",
                        isSign = false
                    )
                )

                if (activityTracker.isActivityOfType(QRCodeDisplayActivity::class.java)) {
                    val activity = activityTracker.getCurrentActivity()
                    val resultIntent = Intent().apply {
                        putExtra(
                            ExternalPaymentActivity.RESULT_PAYMENT_RESPONSE_DATA,
                            gson.toJson(errorResponse)
                        )
                    }
                    activity?.setResult(android.app.Activity.RESULT_CANCELED, resultIntent)
                    activity?.finish()
                    return
                }
            }
        }

        // Flow bình thường - internal app
        if (!jsonObject.isNullOrEmpty()) {
            try {
                val json = JSONObject(jsonObject)
                val amount = json.optLong("Amount", 0L)
                val transactionId = json.optString("TransactionId", "")
                val transactionTime = json.optString("TransactionTime", notify.dateTime)

                val paymentData = PaymentSuccessData(
                    amount = amount,
                    transactionId = transactionId,
                    transactionTime = transactionTime,
                )

                if (PaymentSuccessActivity.isVisible()) {
                    PaymentSuccessActivity.updateData(paymentData)
                    return
                }

                if (activityTracker.isActivityOfType(QRCodeDisplayActivity::class.java)) {
                    activityTracker.getCurrentActivity()?.finish()
                    PaymentSuccessActivity.start(context, paymentData)
                    return
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error parsing QR success data")
            }
        }

        snackbarManager.showFromRabbitNotify(notify)
    }
    private fun handlePaymentRequest(notify: RabbitNotify) {
        val paymentJson = notify.jsonObject
        if (paymentJson.isNullOrEmpty())
            return

        try {
            val paymentRequest = gson.fromJson(paymentJson, PaymentRequest::class.java)
            when (paymentRequest.type) {
                PaymentRequestType.QR -> {
                    paymentRequest.actionValue = PaymentAction.SALE
                    handleQrPayment(paymentRequest)
                }
                PaymentRequestType.CARD -> {
                    paymentRequest.actionValue = PaymentAction.SALE
                    handleCardPayment(paymentRequest)
                }
                PaymentRequestType.MEMBER -> {
                    paymentRequest.actionValue = PaymentAction.SALE
                    handleCardPayment(paymentRequest)
                }
                PaymentRequestType.UNKNOWN -> {
                    handleQrPayment(paymentRequest)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing payment request")
        }
    }

    private fun handleQrPayment(paymentRequest: PaymentRequest) {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return
            }
            val requestData = paymentHelper.buildPaymentAppRequest(account, paymentRequest)
            val intent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                putExtra("REQUEST_DATA", requestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling card payment")
        }
    }

    private fun handleCardPayment(paymentRequest: PaymentRequest) {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return
            }
            val requestData = paymentHelper.buildPaymentAppRequest(account, paymentRequest)
            val intent = Intent(context, TransparentPaymentActivity::class.java).apply {
                putExtra("REQUEST_DATA", requestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling card payment")
        }
    }
}