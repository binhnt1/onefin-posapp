package com.onefin.posapp.ui.external

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.BuildConfig
import com.onefin.posapp.R
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.database.repositories.DriverInfoRepository
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler.ErrorType
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.AutoLoginDialog
import com.onefin.posapp.ui.modals.ErrorDialog
import com.onefin.posapp.ui.modals.ProcessingDialog
import com.onefin.posapp.ui.payment.PaymentCardActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.TransparentPaymentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class ExternalPaymentActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var receiptPrinter: ReceiptPrinter

    @Inject
    lateinit var driverInfoRepository: DriverInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                ExternalPaymentScreen(
                    gson = gson,
                    intent = intent,
                    activity = this,
                    apiService = apiService,
                    paymentHelper = paymentHelper,
                    receiptPrinter = receiptPrinter,
                    storageService = storageService,
                    driverInfoRepository = driverInfoRepository,
                    onFinish = { response, errorMessage ->
                        if (response != null) {
                            returnResult(response, errorMessage)
                        } else {
                            returnResultError(errorMessage)
                        }
                    }
                )
            }
        }
    }

    @Deprecated("")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (data != null) {
            val responseJson = data.getStringExtra(ResultConstants.RESULT_PAYMENT_RESPONSE_DATA)
            if (responseJson != null) {
                try {
                    val response = gson.fromJson(responseJson, PaymentAppResponse::class.java)
                    returnResult(response)
                    return
                } catch (e: Exception) {
                    returnResultError(e.message)
                    return
                }
            }
        }
        val errorMessage = data?.getStringExtra(ResultConstants.RESULT_ERROR)
        returnResultError(errorMessage)
    }

    private fun returnResultError(errorMessage: String? = null) {
        val account = storageService.getAccount()
        val type = intent.getStringExtra(ResultConstants.RESULT_TYPE) ?: ""
        val action = intent.getIntExtra(ResultConstants.RESULT_ACTION, -1)
        val paymentRequest = parsePaymentRequest(intent, gson, account) ?: PaymentAppRequest(
            type = type,
            action = action,
            merchantRequestData = null
        )
        val error = errorMessage ?: PaymentErrorHandler.getVietnameseMessage(ErrorType.UNKNOWN_ERROR)
        val resultIntent = paymentHelper.buildResultIntentError(paymentRequest, error)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun returnResult(response: PaymentAppResponse, errorMessage: String? = null) {
        val resultIntent = paymentHelper.buildResultIntentError(response, errorMessage)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
fun ExternalPaymentScreen(
    gson: Gson,
    intent: Intent,
    apiService: ApiService,
    paymentHelper: PaymentHelper,
    receiptPrinter: ReceiptPrinter,
    storageService: StorageService,
    activity: ExternalPaymentActivity,
    driverInfoRepository: DriverInfoRepository,
    onFinish: (PaymentAppResponse?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loginFailedMsg = stringResource(R.string.error_login_failed)
    val pleaseLoginMsg = stringResource(R.string.error_please_login)
    val notLoggedInMsg = stringResource(R.string.error_not_logged_in)
    val invalidPaymentTypeMsg = stringResource(R.string.error_invalid_payment_type)
    val invalidDataMsg = stringResource(R.string.error_invalid_data)
    val errorMsg = stringResource(R.string.error_general)

    var isAutoLoggingIn by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loginAttempted by remember { mutableStateOf(false) }

    suspend fun registerMerchant(
        paymentRequest: PaymentAppRequest,
        onFinish: (PaymentAppResponse?, String?) -> Unit
    ): Boolean {
        if (BuildConfig.FLAVOR != "mailinh") return true

        val account = storageService.getAccount()
        val tid: String? = account?.terminal?.tid
        val mid: String? = account?.terminal?.mid
        val serial: String? = storageService.getSerial()
        val driverNumber: String? = paymentRequest.merchantRequestData?.tid
        val employeeCode: String? = paymentRequest.merchantRequestData?.mid
        val employeeName: String? = (paymentRequest.merchantRequestData?.additionalData as? Map<*, *>)
            ?.get("driver_name") as? String

        val resultApi = withContext(Dispatchers.IO) {
            driverInfoRepository.ensureDriverInfoRegistered(
                tid = tid,
                mid = mid,
                serial = serial,
                driverNumber = driverNumber,
                employeeCode = employeeCode,
                employeeName = employeeName
            )
        }
        if (!resultApi.isSuccess()) {
            val error = "Đăng ký thiết bị thất bại"
            onFinish(null, error)
            return false
        }
        return true
    }

    DisposableEffect(Unit) {
        @Suppress("DEPRECATION") val job = scope.launch {
            if (!loginAttempted) {
                loginAttempted = true
                storageService.setExternalPaymentContext("EXTERNAL_${System.currentTimeMillis()}")
                try {
                    var account = storageService.getAccount()
                    if (account == null) {
                        val appKey = BuildConfig.APP_KEY
                        if (appKey.isNotEmpty()) {
                            isAutoLoggingIn = true
                            val loginSuccess = withContext(Dispatchers.IO) {
                                performAppKeyLogin(
                                    appKey = appKey,
                                    apiService = apiService,
                                    storageService = storageService
                                )
                            }

                            delay(2000)
                            isAutoLoggingIn = false

                            if (loginSuccess) {
                                account = storageService.getAccount()
                            } else {
                                onFinish(null, loginFailedMsg)
                                return@launch
                            }
                        } else {
                            errorMessage = pleaseLoginMsg
                            delay(2000)

                            val loginIntent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(loginIntent)
                            onFinish(null, notLoggedInMsg)
                            return@launch
                        }
                    }

                    if (account != null) {
                        val paymentRequest = parsePaymentRequest(intent, gson, account)
                        if (paymentRequest != null) {
                            storageService.setPendingPaymentRequest(paymentRequest)
                            isProcessing = false
                            delay(100)

                            if (!registerMerchant(paymentRequest, onFinish)) {
                                return@launch
                            }

                            when (paymentRequest.action) {
                                PaymentAction.SALE.value -> {
                                    when (paymentRequest.type.lowercase()) {
                                        "qr" -> {
                                            val qrIntent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                                                putExtra("REQUEST_DATA", paymentRequest)
                                                putExtra("IS_EXTERNAL_PAYMENT", true)
                                            }
                                            activity.startActivityForResult(qrIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                        }
                                        "card" -> {
                                            val paymentIntent = Intent(context, PaymentCardActivity::class.java).apply {
                                                putExtra("REQUEST_DATA", paymentRequest)
                                                putExtra("IS_EXTERNAL_PAYMENT", true)
                                            }
                                            activity.startActivityForResult(paymentIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                        }
                                        "member" -> {
                                            val paymentIntent = Intent(context, TransparentPaymentActivity::class.java).apply {
                                                putExtra("REQUEST_DATA", paymentRequest)
                                                putExtra("IS_EXTERNAL_PAYMENT", true)
                                            }
                                            activity.startActivityForResult(paymentIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                        }
                                        else -> {
                                            errorMessage = invalidPaymentTypeMsg
                                            onFinish(null, errorMessage)
                                        }
                                    }
                                }
                                PaymentAction.REFUND.value -> {
                                    if (paymentRequest.type.lowercase() != "member") {
                                        errorMessage = "Refund chỉ hỗ trợ thẻ thành viên"
                                        onFinish(null, errorMessage)
                                        return@launch
                                    }
                                    isProcessing = false
                                    delay(100)
                                    val refundIntent = Intent(context, RefundActivity::class.java).apply {
                                        putExtra("REQUEST_DATA", paymentRequest)
                                        putExtra("IS_EXTERNAL_PAYMENT", true)
                                    }
                                    activity.startActivityForResult(refundIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                }

                                PaymentAction.CHANGE_PIN.value -> {
                                    if (paymentRequest.type.lowercase() != "member") {
                                        errorMessage = "Thay đổi PIN chỉ hỗ trợ thẻ thành viên"
                                        onFinish(null, errorMessage)
                                        return@launch
                                    }
                                    isProcessing = false
                                    delay(100)
                                    val changePinIntent = Intent(context, ChangePinActivity::class.java).apply {
                                        putExtra("REQUEST_DATA", paymentRequest)
                                        putExtra("IS_EXTERNAL_PAYMENT", true)
                                    }
                                    activity.startActivityForResult(changePinIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                }

                                PaymentAction.CHECK_BALANCE.value -> {
                                    if (paymentRequest.type.lowercase() != "member") {
                                        errorMessage = "Kiểm tra số dư chỉ hỗ trợ thẻ thành viên"
                                        onFinish(null, errorMessage)
                                        return@launch
                                    }
                                    isProcessing = false
                                    delay(100)
                                    val checkBalanceIntent = Intent(context, CheckBalanceActivity::class.java).apply {
                                        putExtra("REQUEST_DATA", paymentRequest)
                                        putExtra("IS_EXTERNAL_PAYMENT", true)
                                    }
                                    activity.startActivityForResult(checkBalanceIntent, ResultConstants.REQUEST_CODE_PAYMENT)
                                }

                                PaymentAction.REPRINT_INVOICE.value -> {
                                    var billNumber = paymentRequest.merchantRequestData?.billNumber
                                    if (billNumber.isNullOrEmpty()) {
                                        errorMessage = "Thiếu số hóa đơn"
                                        onFinish(null, errorMessage)
                                        return@launch
                                    }

                                    billNumber = "0444784174"
                                    isProcessing = false
                                    delay(100)
                                    scope.launch {
                                        try {
                                            val account = storageService.getAccount()
                                            if (account == null) {
                                                val errorMessage = "Không tìm thấy thông tin tài khoản"
                                                onFinish(null, errorMessage)
                                                return@launch
                                            }

                                            val endpoint = "/api/transaction/$billNumber"
                                            val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                                            val transactionJson = gson.toJson(resultApi.data)
                                            val transaction = gson.fromJson(transactionJson, Transaction::class.java)
                                            if (transaction == null) {
                                                val errorMessage = "Không tìm thấy giao dịch"
                                                onFinish(null, errorMessage)
                                                return@launch
                                            }

                                            val printResult = receiptPrinter.printReceipt(
                                                transaction = transaction,
                                                terminal = account.terminal
                                            )
                                            if (printResult.isSuccess) {
                                                val response = PaymentAppResponse(
                                                    type = paymentRequest.type,
                                                    action = PaymentAction.REPRINT_INVOICE.value,
                                                    paymentResponseData = PaymentResponseData(
                                                        billNumber = billNumber,
                                                        status = PaymentStatusCode.SUCCESS,
                                                        description = "In lại hóa đơn thành công",
                                                        transactionId = transaction.transactionId
                                                    )
                                                )
                                                onFinish(response, null)
                                            } else {
                                                val errorMessage = printResult.exceptionOrNull()?.message ?: "Lỗi in hóa đơn"
                                                onFinish(null, errorMessage)
                                            }

                                        } catch (e: Exception) {
                                            val errorMessage = "Lỗi: ${e.message}"
                                            onFinish(null, errorMessage)
                                        }
                                    }
                                }
                            }

                        } else {
                            errorMessage = invalidDataMsg
                            onFinish(null, errorMessage)
                        }
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = "$errorMsg: ${e.message}"
                    onFinish(null, errorMessage)
                }
            }
        }

        onDispose {
            job.cancel()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (isAutoLoggingIn) {
            AutoLoginDialog()
        } else if (isProcessing) {
            ProcessingDialog()
        } else if (errorMessage != null) {
            ErrorDialog(message = errorMessage!!)
        }
    }
}

suspend fun performAppKeyLogin(
    appKey: String,
    apiService: ApiService,
    storageService: StorageService
): Boolean {
    return try {
        val body = mapOf("AppKey" to appKey)
        val resultApi = apiService.post("/api/security/AppSignIn", body) as ResultApi<*>

        val account = Gson().fromJson(
            Gson().toJson(resultApi.data),
            Account::class.java
        )

        storageService.saveToken(account.token.accessToken)
        storageService.saveAccount(account)
        true
    } catch (e: Exception) {
        false
    }
}

fun parsePaymentRequest(intent: Intent, gson: Gson, account: Account?): PaymentAppRequest? {
    return try {
        val type = intent.getStringExtra(ResultConstants.RESULT_TYPE) ?: return null
        val action = intent.getIntExtra(ResultConstants.RESULT_ACTION, -1)
        if (action == -1) return null

        val merchantRequestDataStr = intent.getStringExtra(ResultConstants.EXTRA_MERCHANT_REQUEST_DATA)
        val merchantRequestData = if (merchantRequestDataStr != null) {
            try {
                gson.fromJson(merchantRequestDataStr, MerchantRequestData::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (merchantRequestData == null) return null

        if (merchantRequestData.tid == null || merchantRequestData.tid == "")
            merchantRequestData.tid = account?.terminal?.tid
        if (merchantRequestData.mid == null || merchantRequestData.mid == "")
            merchantRequestData.mid = account?.terminal?.mid
        merchantRequestData.additionalData = toMapOrNull(merchantRequestData.additionalData)
        PaymentAppRequest(
            type = type,
            action = action,
            merchantRequestData = merchantRequestData
        )
    } catch (e: Exception) {
        null
    }
}

fun toMapOrNull(obj: Any?): Map<String, Any>? {
    return when (obj) {
        null -> null
        is String -> {
            try {
                Gson().fromJson(
                    obj,
                    object : TypeToken<Map<String, Any>>() {}.type
                )
            } catch (e: Exception) {
                null
            }
        }
        is Map<*, *> -> {
            try {
                obj.entries.associate { (key, value) ->
                    (key as? String ?: return null) to (value ?: return null)
                }
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }
}