package com.onefin.posapp.core.managers

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentRequest
import com.onefin.posapp.core.models.data.PaymentRequestType
import com.onefin.posapp.core.models.data.RabbitNotify
import com.onefin.posapp.core.models.data.RabbitNotifyType
import com.onefin.posapp.core.services.RabbitMQService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.LogoutDialogActivity
import com.onefin.posapp.ui.transaction.TransparentPaymentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RabbitMQManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val paymentHelper: PaymentHelper,
    private val storageService: StorageService,
    private val snackbarManager: SnackbarManager,
    private val rabbitMQService: RabbitMQService,
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
                    RabbitNotifyType.REQUEST_PAYMENT -> {
                        handlePaymentRequest(notify)
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