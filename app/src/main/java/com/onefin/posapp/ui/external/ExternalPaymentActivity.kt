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
import timber.log.Timber
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

    private val TAG = "ExternalPaymentActivity"

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
                    onFinish = { resultCode, response, errorMessage ->
                        returnResult(resultCode, response, errorMessage)
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
            Timber.tag(TAG).d("responseJson: $responseJson")

            if (responseJson != null) {
                try {
                    val response = gson.fromJson(responseJson, PaymentAppResponse::class.java)
                    Timber.tag(TAG).d("Parsed response: $response")
                    returnResult(resultCode, response, null)
                    return
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error parsing response")
                    returnResult(RESULT_OK, null, "PARSE_ERROR: ${e.message}")
                    return
                }
            } else {
                Timber.tag(TAG).w("responseJson is NULL")
            }
        } else {
            Timber.tag(TAG).w("data Intent is NULL")
        }

        val errorMessage = data?.getStringExtra(ResultConstants.RESULT_ERROR) ?: "PAYMENT_CANCELLED"
        Timber.tag(TAG).d("Returning error: $errorMessage")
        returnResult(RESULT_CANCELED, null, errorMessage)
    }

    private fun returnResult(resultCode: Int, response: PaymentAppResponse?, errorMessage: String?) {
        val resultIntent = Intent().apply {
            if (response != null) {
                putExtra(ResultConstants.RESULT_TYPE, response.type)
                putExtra(ResultConstants.RESULT_ACTION, response.action)
                putExtra(ResultConstants.RESULT_PAYMENT_RESPONSE_DATA, gson.toJson(response.paymentResponseData))
            }
            if (errorMessage != null) {
                putExtra(ResultConstants.RESULT_ERROR, errorMessage)
                putExtra(ResultConstants.RESULT_MESSAGE, errorMessage)
            }
        }
        setResult(resultCode, resultIntent)
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
    onFinish: (Int, PaymentAppResponse?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // String resources
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

    var requestType by remember { mutableStateOf<String?>(null) }
    var requestAction by remember { mutableStateOf<Int?>(null) }

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
                                errorMessage = loginFailedMsg
                                val errorResponse = createErrorResponse(
                                    requestType,
                                    requestAction,
                                    PaymentStatusCode.LOGIN_FAILED,
                                    loginFailedMsg
                                )
                                onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "LOGIN_FAILED")
                                return@launch
                            }
                        } else {
                            errorMessage = pleaseLoginMsg
                            delay(2000)

                            val loginIntent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(loginIntent)
                            val errorResponse = createErrorResponse(
                                requestType,
                                requestAction,
                                PaymentStatusCode.NOT_LOGGED_IN,
                                notLoggedInMsg
                            )
                            onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "NOT_LOGGED_IN")
                            return@launch
                        }
                    }

                    if (account != null) {
                        val paymentRequest = parsePaymentRequest(intent, gson, account)

                        if (paymentRequest != null) {
                            requestType = paymentRequest.type
                            requestAction = paymentRequest.action

                            storageService.setPendingPaymentRequest(paymentRequest)

                            isProcessing = false
                            delay(100)

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
                                            val errorResponse = createErrorResponse(
                                                requestType,
                                                requestAction,
                                                PaymentStatusCode.INVALID_DATA,
                                                invalidPaymentTypeMsg
                                            )
                                            onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_TYPE")
                                        }
                                    }
                                }
                                PaymentAction.REFUND.value -> {
                                    if (paymentRequest.type.lowercase() != "member") {
                                        errorMessage = "Refund chỉ hỗ trợ thẻ thành viên"
                                        val errorResponse = createErrorResponse(
                                            requestType,
                                            requestAction,
                                            PaymentStatusCode.INVALID_TYPE,
                                            "Refund chỉ hỗ trợ thẻ thành viên"
                                        )
                                        onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_TYPE")
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
                                        val errorResponse = createErrorResponse(
                                            requestType,
                                            requestAction,
                                            PaymentStatusCode.INVALID_TYPE,
                                            "Thay đổi PIN chỉ hỗ trợ thẻ thành viên"
                                        )
                                        onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_TYPE")
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
                                        val errorResponse = createErrorResponse(
                                            requestType,
                                            requestAction,
                                            PaymentStatusCode.INVALID_TYPE,
                                            "Kiểm tra số dư chỉ hỗ trợ thẻ thành viên"
                                        )
                                        onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_TYPE")
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
                                        val errorResponse = createErrorResponse(
                                            requestType,
                                            requestAction,
                                            PaymentStatusCode.INVALID_DATA,
                                            "Thiếu số hóa đơn"
                                        )
                                        onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "MISSING_BILL_NUMBER")
                                        return@launch
                                    }

                                    // hard-code
                                    billNumber = "0444784174"
                                    isProcessing = false
                                    delay(100)
                                    scope.launch {
                                        try {
                                            // Lấy account
                                            val account = storageService.getAccount()
                                            if (account == null) {
                                                val errorResponse = createErrorResponse(
                                                    requestType,
                                                    requestAction,
                                                    PaymentStatusCode.ERROR,
                                                    "Không tìm thấy thông tin tài khoản"
                                                )
                                                onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "NO_ACCOUNT")
                                                return@launch
                                            }

                                            // Gọi API lấy transaction
                                            val endpoint = "/api/transaction/$billNumber"
                                            val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                                            val transactionJson = gson.toJson(resultApi.data)
                                            val transaction = gson.fromJson(transactionJson, Transaction::class.java)
                                            if (transaction == null) {
                                                val errorResponse = createErrorResponse(
                                                    requestType,
                                                    requestAction,
                                                    PaymentStatusCode.ERROR,
                                                    "Không tìm thấy giao dịch"
                                                )
                                                onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "TRANSACTION_NOT_FOUND")
                                                return@launch
                                            }

                                            // In hóa đơn
                                            val printResult = receiptPrinter.printReceipt(
                                                transaction = transaction,
                                                terminal = account.terminal
                                            )
                                            if (printResult.isSuccess) {
                                                // Thành công
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
                                                onFinish(android.app.Activity.RESULT_OK, response, null)
                                            } else {
                                                // In thất bại
                                                val errorMessage = printResult.exceptionOrNull()?.message ?: "Lỗi in hóa đơn"
                                                val errorResponse = createErrorResponse(
                                                    requestType,
                                                    requestAction,
                                                    PaymentStatusCode.ERROR,
                                                    "In hóa đơn thất bại: $errorMessage"
                                                )
                                                onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "PRINT_FAILED")
                                            }

                                        } catch (e: Exception) {
                                            Timber.tag("ExternalPaymentActivity").e(e, "Reprint invoice failed")
                                            val errorResponse = createErrorResponse(
                                                requestType,
                                                requestAction,
                                                PaymentStatusCode.ERROR,
                                                "Lỗi: ${e.message}"
                                            )
                                            onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "ERROR: ${e.message}")
                                        }
                                    }
                                }
                            }

                        } else {
                            errorMessage = invalidDataMsg
                            val errorResponse = createErrorResponse(
                                requestType,
                                requestAction,
                                PaymentStatusCode.INVALID_DATA,
                                invalidDataMsg
                            )
                            onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_DATA")
                        }
                    }

                } catch (e: CancellationException) {
                    Timber.tag("ExternalPaymentActivity").d("Coroutine cancelled")
                } catch (e: Exception) {
                    Timber.tag("ExternalPaymentActivity").e(e, "Error processing payment request")
                    val errorText = "$errorMsg: ${e.message}"
                    errorMessage = errorText
                    val errorResponse = createErrorResponse(
                        requestType,
                        requestAction,
                        PaymentStatusCode.ERROR,
                        errorText
                    )
                    onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "ERROR: ${e.message}")
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
        Timber.tag("ExternalPaymentActivity").e(e, "Auto login failed")
        false
    }
}

private fun createErrorResponse(
    type: String?,
    action: Int?,
    statusCode: String,
    description: String
): PaymentAppResponse? {
    if (type == null || action == null) return null

    return PaymentAppResponse(
        type = type,
        action = action,
        paymentResponseData = PaymentResponseData(
            status = statusCode,
            description = description,
            isSign = false
        )
    )
}

fun parsePaymentRequest(
    intent: Intent,
    gson: Gson,
    account: Account
): PaymentAppRequest? {
    return try {
        val type = intent.getStringExtra(ResultConstants.RESULT_TYPE) ?: return null
        val action = intent.getIntExtra(ResultConstants.RESULT_ACTION, -1)
        if (action == -1) return null

        val merchantRequestDataStr = intent.getStringExtra(ResultConstants.EXTRA_MERCHANT_REQUEST_DATA)
        val merchantRequestData = if (merchantRequestDataStr != null) {
            try {
                gson.fromJson(merchantRequestDataStr, MerchantRequestData::class.java)
            } catch (e: Exception) {
                Timber.tag("ExternalPaymentActivity").e(e, "Error parsing merchant_request_data")
                null
            }
        } else {
            null
        }

        if (merchantRequestData == null) return null

        if (merchantRequestData.tid == null || merchantRequestData.tid == "")
            merchantRequestData.tid = account.terminal.tid
        if (merchantRequestData.mid == null || merchantRequestData.mid == "")
            merchantRequestData.mid = account.terminal.mid

        // fix additionalData
        merchantRequestData.additionalData = toMapOrNull(merchantRequestData.additionalData)
        PaymentAppRequest(
            type = type,
            action = action,
            merchantRequestData = merchantRequestData
        )
    } catch (e: Exception) {
        Timber.tag("ExternalPaymentActivity").e(e, "Error parsing payment request")
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
            // Validate và filter
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