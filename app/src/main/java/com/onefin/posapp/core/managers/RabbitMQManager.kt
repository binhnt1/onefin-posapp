package com.onefin.posapp.core.managers

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.models.data.PaymentSuccessData
import com.onefin.posapp.core.models.data.RabbitNotify
import com.onefin.posapp.core.models.data.RabbitNotifyType
import com.onefin.posapp.core.services.RabbitMQService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.LogoutDialogActivity
import com.onefin.posapp.ui.payment.PaymentCardActivity
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
        if (jsonObject.isNullOrEmpty())
            return

        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                // Clear context và request
                val json = JSONObject(jsonObject)
                storageService.clearExternalPaymentContext()
                val transactionId = json.optString("TransactionId", "")
                val transactionTime = json.optString("TransactionTime", notify.dateTime)

                // Return về external app
                if (activityTracker.isActivityOfType(QRCodeDisplayActivity::class.java)) {
                    val activity = activityTracker.getCurrentActivity()
                    if (activity != null) {
                        val resultIntent = paymentHelper.buildResultIntentSuccess(pendingRequest, transactionId, transactionTime)
                        activity.setResult(RESULT_OK, resultIntent)
                        activity.finish()
                        return
                    }
                }
            } else {
                storageService.clearExternalPaymentContext()
                val errorResponse = PaymentAppResponse(
                    type = "qr",
                    action = 1,
                    paymentResponseData = PaymentResponseData(
                        isSign = false,
                        status = PaymentStatusCode.ERROR,
                        description = "Không tìm thấy thông tin giao dịch",
                    )
                )
                if (activityTracker.isActivityOfType(QRCodeDisplayActivity::class.java)) {
                    val activity = activityTracker.getCurrentActivity()
                    if (activity != null) {
                        val resultIntent = paymentHelper.buildResultIntentError(errorResponse, "Không tìm thấy thông tin giao dịch")
                        activity.setResult(RESULT_OK, resultIntent)
                        activity.finish()
                        return
                    }
                }
            }
        }

        // Flow bình thường - internal app
        try {
            // Hiển thị màn QRSuccess
            val json = JSONObject(jsonObject)
            val amount = json.optLong("Amount", 0L)
            val transactionId = json.optString("TransactionId", "")
            val transactionTime = json.optString("TransactionTime", notify.dateTime)
            val paymentData = PaymentSuccessData(
                amount = amount,
                timeCountDown = 10,
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
    private fun handlePaymentRequest(notify: RabbitNotify) {
        val paymentJson = notify.jsonObject
        if (paymentJson.isNullOrEmpty())
            return

        try {
            val paymentRequest = gson.fromJson(paymentJson, PaymentAppRequest::class.java)
            when (paymentRequest.type.lowercase()) {
                "qr" -> {
                    handleQrPayment(paymentRequest)
                }
                "card" -> {
                    handleCardPayment(paymentRequest)
                }
                "member" -> {
                    handleCardMemberPayment(paymentRequest)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing payment request")
        }
    }

    private fun handleQrPayment(paymentRequest: PaymentAppRequest) {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return
            }
            val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
            val intent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling card payment")
        }
    }

    private fun handleCardPayment(paymentRequest: PaymentAppRequest) {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return
            }
            val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
            val intent = Intent(context, PaymentCardActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling card payment")
        }
    }

    private fun handleCardMemberPayment(paymentRequest: PaymentAppRequest) {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return
            }
            val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
            val intent = Intent(context, TransparentPaymentActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error handling card payment")
        }
    }
}