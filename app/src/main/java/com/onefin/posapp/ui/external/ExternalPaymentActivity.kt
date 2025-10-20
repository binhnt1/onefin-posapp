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
import com.google.gson.Gson
import com.onefin.posapp.BuildConfig
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.AutoLoginDialog
import com.onefin.posapp.ui.modals.ErrorDialog
import com.onefin.posapp.ui.modals.ProcessingDialog
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

    companion object {
        private const val TAG = "ExternalPaymentActivity"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ACTION = "action"
        const val EXTRA_MERCHANT_REQUEST_DATA = "merchant_request_data"

        // Response constants
        const val RESULT_TYPE = "type"
        const val RESULT_ERROR = "error"
        const val RESULT_ACTION = "action"
        const val RESULT_MESSAGE = "message"
        const val RESULT_PAYMENT_RESPONSE_DATA = "payment_response_data"

        const val REQUEST_CODE_QR_PAYMENT = 1001
        const val REQUEST_CODE_CARD_PAYMENT = 1002
    }

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

        Timber.tag(TAG).d("=== onActivityResult START ===")
        Timber.tag(TAG).d("requestCode: $requestCode")
        Timber.tag(TAG).d("resultCode: $resultCode")
        Timber.tag(TAG).d("data: $data")

        if (data != null) {
            // LOG TẤT CẢ EXTRAS
            val extras = data.extras
            if (extras != null) {
                for (key in extras.keySet()) {
                    Timber.tag(TAG).d("Extra key: $key, value: ${extras.get(key)}")
                }
            } else {
                Timber.tag(TAG).d("No extras in intent")
            }

            val responseJson = data.getStringExtra(RESULT_PAYMENT_RESPONSE_DATA)
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

        val errorMessage = data?.getStringExtra(RESULT_ERROR) ?: "PAYMENT_CANCELLED"
        Timber.tag(TAG).d("Returning error: $errorMessage")
        returnResult(RESULT_CANCELED, null, errorMessage)
    }

    private fun returnResult(resultCode: Int, response: PaymentAppResponse?, errorMessage: String?) {
        val resultIntent = Intent().apply {
            if (response != null) {
                putExtra(RESULT_TYPE, response.type)
                putExtra(RESULT_ACTION, response.action)
                putExtra(RESULT_PAYMENT_RESPONSE_DATA, gson.toJson(response.paymentResponseData))
            }
            if (errorMessage != null) {
                putExtra(RESULT_ERROR, errorMessage)
                putExtra(RESULT_MESSAGE, errorMessage)
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
    storageService: StorageService,
    activity: ExternalPaymentActivity,
    onFinish: (Int, PaymentAppResponse?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAutoLoggingIn by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loginAttempted by remember { mutableStateOf(false) }

    var requestType by remember { mutableStateOf<String?>(null) }
    var requestAction by remember { mutableStateOf<Int?>(null) }

    // Sử dụng DisposableEffect để cleanup
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
                                errorMessage = "Đăng nhập thất bại"
                                val errorResponse = createErrorResponse(
                                    requestType,
                                    requestAction,
                                    PaymentStatusCode.LOGIN_FAILED,
                                    "Đăng nhập thất bại"
                                )
                                onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "LOGIN_FAILED")
                                return@launch
                            }
                        } else {
                            errorMessage = "Vui lòng đăng nhập"
                            delay(2000)

                            val loginIntent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(loginIntent)
                            val errorResponse = createErrorResponse(
                                requestType,
                                requestAction,
                                PaymentStatusCode.NOT_LOGGED_IN,
                                "Chưa đăng nhập"
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

                            // LƯU REQUEST VÀO STORAGE
                            storageService.setPendingPaymentRequest(paymentRequest)

                            isProcessing = false
                            delay(100)

                            when (paymentRequest.type.lowercase()) {
                                "qr" -> {
                                    val qrIntent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                                        putExtra("REQUEST_DATA", paymentRequest)
                                        putExtra("IS_EXTERNAL_PAYMENT", true)
                                    }
                                    activity.startActivityForResult(
                                        qrIntent,
                                        ExternalPaymentActivity.REQUEST_CODE_QR_PAYMENT
                                    )
                                }
                                "card", "member" -> {
                                    val paymentIntent = Intent(context, TransparentPaymentActivity::class.java).apply {
                                        putExtra("REQUEST_DATA", paymentRequest)
                                        putExtra("IS_EXTERNAL_PAYMENT", true)
                                    }
                                    activity.startActivityForResult(
                                        paymentIntent,
                                        ExternalPaymentActivity.REQUEST_CODE_CARD_PAYMENT
                                    )
                                }
                                else -> {
                                    errorMessage = "Loại thanh toán không hợp lệ"
                                    val errorResponse = createErrorResponse(
                                        requestType,
                                        requestAction,
                                        PaymentStatusCode.INVALID_DATA,
                                        "Loại thanh toán không hợp lệ"
                                    )
                                    onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_TYPE")
                                }
                            }
                        } else {
                            errorMessage = "Dữ liệu không hợp lệ"
                            val errorResponse = createErrorResponse(
                                requestType,
                                requestAction,
                                PaymentStatusCode.INVALID_DATA,
                                "Dữ liệu không hợp lệ"
                            )
                            onFinish(android.app.Activity.RESULT_CANCELED, errorResponse, "INVALID_DATA")
                        }
                    }

                } catch (e: CancellationException) {
                    // Bỏ qua exception này, đây là normal cancellation
                    Timber.tag("ExternalPaymentActivity").d("Coroutine cancelled")
                } catch (e: Exception) {
                    Timber.tag("ExternalPaymentActivity").e(e, "Error processing payment request")
                    errorMessage = "Lỗi: ${e.message}"
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
        val type = intent.getStringExtra(ExternalPaymentActivity.EXTRA_TYPE) ?: return null
        val action = intent.getIntExtra(ExternalPaymentActivity.EXTRA_ACTION, -1)
        if (action == -1) return null

        val merchantRequestDataStr = intent.getStringExtra(ExternalPaymentActivity.EXTRA_MERCHANT_REQUEST_DATA)

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