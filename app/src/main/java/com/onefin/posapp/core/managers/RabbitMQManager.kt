package com.onefin.posapp.core.managers

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.models.data.PaymentSuccessData
import com.onefin.posapp.core.models.data.RabbitNotify
import com.onefin.posapp.core.models.data.RabbitNotifyType
import com.onefin.posapp.core.models.data.SettleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.RabbitMQService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.LogoutDialogActivity
import com.onefin.posapp.ui.modals.ProcessingActivity
import com.onefin.posapp.ui.modals.QRSuccessNotificationActivity
import com.onefin.posapp.ui.modals.ResultDialogActivity
import com.onefin.posapp.ui.payment.PaymentCardActivity
import com.onefin.posapp.ui.payment.PaymentSuccessActivity
import com.onefin.posapp.ui.transaction.TransparentPaymentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RabbitMQManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson,
    private val ttsManager: TTSManager,
    private val apiService: ApiService,
    private val paymentHelper: PaymentHelper,
    private val printerHelper: PrinterHelper,
    private val receiptPrinter: ReceiptPrinter,
    private val storageService: StorageService,
    private val snackbarManager: SnackbarManager,
    private val rabbitMQService: RabbitMQService,
    private val activityTracker: ActivityTracker,
) {

    companion object {
        private const val TAG = "RabbitMQManager"
    }

    private var isInitialized = false
    private val rabbitScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        // Delay 5s để user xem dialog
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                stop()
                storageService.clearAll()

                val intent = Intent(context, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }, 5000)
    }

    private fun handleRabbitMessage(messageJson: String) {
        try {
            val notify = gson.fromJson(messageJson, RabbitNotify::class.java)
            if (notify != null) {
                val notifyType = RabbitNotifyType.fromValue(notify.type)
                when (notifyType) {
                    RabbitNotifyType.LOGOUT,
                    RabbitNotifyType.LOCKED,
                    RabbitNotifyType.LOCK_USER,
                    RabbitNotifyType.CHANGE_TID_MID -> {
                        handleLogout(notify)
                    }
                    RabbitNotifyType.QR_SUCCESS -> {
                        handleQRSuccess(notify)
                    }
                    RabbitNotifyType.REQUEST_PAYMENT -> {
                        handlePaymentRequest(notify)
                        snackbarManager.showFromRabbitNotify(notify)
                    }
                    RabbitNotifyType.SETTLEMENT -> {
                        rabbitScope.launch {
                            handleSettlement(notify)
                        }
                    }
                    RabbitNotifyType.PRINT_INVOICE -> {
                        rabbitScope.launch {
                            handlePrintInvoice(notify)
                        }
                    }
                    else -> {
                        snackbarManager.showFromRabbitNotify(notify)
                    }
                }
            }

        } catch (_: Exception) {
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
            // Kiểm tra nếu đang ở HomeActivity -> Hiển thị notification với countdown
            if (activityTracker.isActivityOfType(com.onefin.posapp.ui.home.HomeActivity::class.java)) {
                QRSuccessNotificationActivity.show(context, notify.content)
                return
            }

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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }

    private suspend fun handleSettlement(notify: RabbitNotify) {
        // Hiển thị ProcessingDialog
        withContext(Dispatchers.Main) {
            ProcessingActivity.show(context)
        }

        try {
            val requestId = UtilHelper.generateRequestId()
            val endpoint = "/api/card/settle"
            val body = mapOf(
                "requestId" to requestId,
                "data" to mapOf("autoBatchUpload" to true)
            )

            val resultApi = apiService.post(endpoint, body) as ResultApi<*>

            // Đóng ProcessingDialog
            delay(2000)
            withContext(Dispatchers.Main) {
                ProcessingActivity.dismiss()
            }

            if (resultApi.isSuccess()) {
                val gson = Gson()
                val jsonString = gson.toJson(resultApi.data)
                val settleData = gson.fromJson(jsonString, SettleResultData::class.java)

                if (settleData?.isSuccess() == true) {
                    withContext(Dispatchers.Main) {
                        ResultDialogActivity.showSuccess(context, "Kết toán giao dịch thành công")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        ResultDialogActivity.showError(
                            context,
                            settleData?.status?.message ?: "Kết toán giao dịch thất bại"
                        )
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    ResultDialogActivity.showError(context, resultApi.description)
                }
            }
        } catch (e: Exception) {
            // Đóng ProcessingDialog nếu có lỗi
            withContext(Dispatchers.Main) {
                ProcessingActivity.dismiss()
                ResultDialogActivity.showError(context, e.message ?: "Có lỗi xảy ra")
            }
        }
    }

    private suspend fun handlePrintInvoice(notify: RabbitNotify) {
        // Hiển thị ProcessingDialog
        withContext(Dispatchers.Main) {
            ProcessingActivity.show(context)
        }

        try {
            val transactionJson = notify.jsonObject
            if (transactionJson.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    ProcessingActivity.dismiss()
                    ResultDialogActivity.showError(context, "Giao dịch không tồn tại. Vui lòng thử lại")
                }
                return
            }

            if (!printerHelper.waitForReady(timeoutMs = 3000)) {
                withContext(Dispatchers.Main) {
                    ProcessingActivity.dismiss()
                    ResultDialogActivity.showError(context, "Máy in chưa sẵn sàng. Vui lòng thử lại")
                }
                return
            }

            val account = storageService.getAccount()
            if (account == null) {
                withContext(Dispatchers.Main) {
                    ProcessingActivity.dismiss()
                    ResultDialogActivity.showError(context, "Không tìm thấy thông tin tài khoản")
                }
                return
            }

            val transaction = gson.fromJson(transactionJson, Transaction::class.java)
            val result = receiptPrinter.printReceipt(
                transaction = transaction,
                terminal = account.terminal
            )

            // Đóng ProcessingDialog
            withContext(Dispatchers.Main) {
                ProcessingActivity.dismiss()
            }

            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    ResultDialogActivity.showSuccess(context, "In hóa đơn thành công")
                }
            } else {
                withContext(Dispatchers.Main) {
                    ResultDialogActivity.showError(
                        context,
                        result.exceptionOrNull()?.message ?: "In hóa đơn thất bại"
                    )
                }
            }
        } catch (e: Exception) {
            // Đóng ProcessingDialog nếu có lỗi
            withContext(Dispatchers.Main) {
                ProcessingActivity.dismiss()
                ResultDialogActivity.showError(context, e.message ?: "Có lỗi xảy ra")
            }
        }
    }

    private fun handleQrPayment(paymentRequest: PaymentAppRequest) {
        try {
            storageService.getAccount() ?: return
            val driverInfo = storageService.getDriverInfo()
            val paymentRequestData = paymentHelper.createPaymentAppRequest(paymentRequest, driverInfo)
            val intent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun handleCardPayment(paymentRequest: PaymentAppRequest) {
        try {
            storageService.getAccount() ?: return
            val driverInfo = storageService.getDriverInfo()
            val paymentRequestData = paymentHelper.createPaymentAppRequest(paymentRequest, driverInfo)
            val intent = Intent(context, PaymentCardActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun handleCardMemberPayment(paymentRequest: PaymentAppRequest) {
        try {
            storageService.getAccount() ?: return
            val driverInfo = storageService.getDriverInfo()
            val paymentRequestData = paymentHelper.createPaymentAppRequest(paymentRequest, driverInfo)
            val intent = Intent(context, TransparentPaymentActivity::class.java).apply {
                putExtra("REQUEST_DATA", paymentRequestData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}