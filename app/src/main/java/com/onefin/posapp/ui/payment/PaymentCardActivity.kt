package com.onefin.posapp.ui.payment

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.managers.SunmiPaymentManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.managers.helpers.PaymentTTSHelper
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ActionButtons
import com.onefin.posapp.ui.payment.components.AmountCard
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.ModernHeader
import com.onefin.posapp.ui.payment.components.PaymentStatusCard
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class PaymentCardActivity : BaseActivity() {

    private val gson = Gson()

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var paymentManager: SunmiPaymentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PaymentCardScreen(
                    apiService = apiService,
                    paymentAppRequest = requestData,
                    onCancel = { cancelTransaction() },
                    onSuccess = { saleResult -> onSuccess(saleResult, requestData) },
                )
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelTransaction()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentManager.cancelReadCard()
    }

    private fun cancelTransaction(errorMessage: String? = null) {
        paymentManager.cancelReadCard()
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                val response = paymentHelper.createPaymentAppResponseCancel(pendingRequest, errorMessage)
                storageService.clearExternalPaymentContext()
                val resultIntent = Intent().apply {
                    putExtra(
                        ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                        gson.toJson(response)
                    )
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                val currentRequest = getPaymentAppRequest()
                if (currentRequest != null) {
                    val response = paymentHelper.createPaymentAppResponseCancel(currentRequest, errorMessage)
                    val resultIntent = Intent().apply {
                        putExtra(
                            ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                            gson.toJson(response)
                        )
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
        finish()
    }

    private fun onSuccess(saleResult: SaleResultData, originalRequest: PaymentAppRequest) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest() ?: originalRequest
            storageService.clearExternalPaymentContext()

            // Return về external app
            val response = CardHelper.returnSaleResponse(saleResult, pendingRequest)
            val resultIntent = Intent().apply {
                putExtra(
                    ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                    gson.toJson(response)
                )
            }
            setResult(android.app.Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            val response = CardHelper.returnSaleResponse(saleResult, originalRequest)
            val resultIntent = Intent().apply {
                putExtra(
                    ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                    gson.toJson(response)
                )
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}

@Composable
fun PaymentCardScreen(
    onCancel: () -> Unit,
    apiService: ApiService,
    onSuccess: (SaleResultData) -> Unit,
    paymentAppRequest: PaymentAppRequest,
) {
    var cardInfo by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Đang khởi tạo...") }
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }

    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as PaymentCardActivity
    val paymentManager = remember { activity.paymentManager }
    val ttsManager = remember { activity.ttsManager }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val amount = paymentAppRequest.merchantRequestData?.amount ?: 0

    fun resetAndRetry() {
        Timber.tag("PaymentCard").d("🔄 Reset and retry")

        // Reset all state
        cardInfo = ""
        errorCode = null
        showErrorDialog = false
        errorDialogMessage = ""
        currentRequestSale = null
        statusMessage = "Đang khởi tạo..."
        paymentState = PaymentState.INITIALIZING

        // Cancel ongoing operation
        paymentManager.cancelReadCard()

        // Trigger LaunchedEffect
        retryTrigger++
    }

    fun startCardReading() {
        paymentState = PaymentState.WAITING_CARD
        statusMessage = "Vui lòng quẹt/chạm/cắm thẻ"

        paymentManager.startReadCard(
            paymentAppRequest = paymentAppRequest,
            onResult = { result ->
                scope.launch {
                    when (result) {
                        is PaymentResult.Success -> {
                            val requestSale = result.requestSale
                            Timber.tag("PaymentCard").d("🎉 CARD READ SUCCESS")

                            // 1. CARD_DETECTED
                            paymentState = PaymentState.CARD_DETECTED
                            currentRequestSale = requestSale
                            cardInfo = UtilHelper.maskCardNumber(requestSale.data.card.clearPan)
                            statusMessage = "Đã phát hiện thẻ"

                            delay(1000)

                            // 2. PROCESSING
                            paymentState = PaymentState.PROCESSING
                            statusMessage = "Đang xử lý giao dịch..."

                            scope.launch {
                                val result = processPayment(apiService, requestSale)
                                result.onSuccess { saleResultData ->
                                    Timber.tag("PaymentCard").d("✅ Payment SUCCESS")

                                    // Check API status code
                                    if (saleResultData.status?.code == "00") {
                                        paymentState = PaymentState.SUCCESS
                                        statusMessage = "Giao dịch thành công"

                                        val ttsMessage = PaymentTTSHelper.getSuccessTTSMessage(amount)
                                        ttsManager.speak(ttsMessage)

                                        // Auto close after 3 seconds
                                        onSuccess(saleResultData)
                                    } else {
                                        // API returned error status
                                        val apiErrorMsg = saleResultData.status?.message
                                            ?: "Giao dịch thất bại"
                                        errorCode = saleResultData.status?.code ?: "API_ERROR"
                                        errorDialogMessage = apiErrorMsg
                                        showErrorDialog = true
                                        ttsManager.speak("Giao dịch thất bại. $apiErrorMsg")
                                    }
                                }

                                result.onFailure { error ->
                                    val errorMessage = error.message ?: "Giao dịch thất bại. Vui lòng thử lại"
                                    showErrorDialog = true
                                    errorCode = "API_ERROR"
                                    errorDialogMessage = errorMessage
                                    ttsManager.speak("Giao dịch thất bại. $errorMessage")
                                }
                            }
                        }

                        is PaymentResult.Error -> {
                            showErrorDialog = true
                            errorCode = result.errorCode
                            errorDialogMessage = result.vietnameseMessage
                            ttsManager.speak(PaymentTTSHelper.getTTSMessage(result.type))
                        }
                    }
                }
            }
        )
    }

    // Initialize once
    LaunchedEffect(retryTrigger) {
        Timber.tag("PaymentCard").d("🔄 Initializing... (trigger: $retryTrigger)")
        paymentManager.initialize(
            onReady = {
                Timber.tag("PaymentCard").d("✅ Payment manager ready")
                startCardReading()
            },
            onError = { error ->
                Timber.tag("PaymentCard").e(
                    "❌ INIT ERROR: ${error.getFullMessage()}"
                )

                paymentState = PaymentState.ERROR
                statusMessage = error.vietnameseMessage
                errorDialogMessage = error.vietnameseMessage
                errorCode = error.errorCode
                showErrorDialog = true

                val ttsMessage = PaymentTTSHelper.getTTSMessage(error.type)
                ttsManager.speak(ttsMessage)
            }
        )
    }

    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ModernHeader(
                billNumber = paymentAppRequest.merchantRequestData?.billNumber,
                referenceId = paymentAppRequest.merchantRequestData?.referenceId
            )

            Spacer(modifier = Modifier.height(16.dp))

            AmountCard(amount = amount)

            Spacer(modifier = Modifier.height(24.dp))

            PaymentStatusCard(
                paymentState = paymentState,
                statusMessage = statusMessage,
                cardInfo = cardInfo,
                currentRequestSale = currentRequestSale,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                paymentState = paymentState,
                onCancel = onCancel
            )
        }

        // Error Dialog
        if (showErrorDialog) {
            ModernErrorDialog(
                message = errorDialogMessage,
                errorCode = errorCode,
                onRetry = {
                    resetAndRetry()
                },
                onCancel = {
                    showErrorDialog = false
                    onCancel()
                }
            )
        }
    }
}

suspend fun processPayment(
    apiService: ApiService,
    requestSale: RequestSale
): Result<SaleResultData> {  // ✅ CHANGED: Return SaleResultData instead of RequestSale
    val gson = Gson()
    Timber.tag("Payment").d("Processing payment:")
    return try {
        // Convert RequestSale thành Map
        val requestBody = mapOf(
            "data" to mapOf(
                "card" to mapOf(
                    "ksn" to requestSale.data.card.ksn,
                    "pin" to requestSale.data.card.pin,
                    "type" to requestSale.data.card.type,
                    "mode" to requestSale.data.card.mode,
                    "newPin" to requestSale.data.card.newPin,
                    "track1" to requestSale.data.card.track1,
                    "track2" to requestSale.data.card.track2,
                    "track3" to requestSale.data.card.track3,
                    "emvData" to requestSale.data.card.emvData,
                    "clearPan" to requestSale.data.card.clearPan,
                    "expiryDate" to requestSale.data.card.expiryDate
                ),
                "device" to mapOf(
                    "posEntryMode" to requestSale.data.device.posEntryMode,
                    "posConditionCode" to requestSale.data.device.posConditionCode
                ),
                "payment" to mapOf(
                    "currency" to requestSale.data.payment.currency,
                    "transAmount" to requestSale.data.payment.transAmount
                ),
                "bank" to requestSale.data.bank,
            ),
            "requestData" to mapOf(
                "type" to requestSale.requestData.type,
                "action" to requestSale.requestData.action,
                "merchant_request_data" to gson.toJson(requestSale.requestData.merchantRequestData)
            ),
            "requestId" to requestSale.requestId,
        )

        // Gọi API /api/card/sale
        val resultApi = apiService.post("/api/card/sale", requestBody) as ResultApi<*>

        // ✅ Parse response as SaleResultData
        val saleResultData = gson.fromJson(
            gson.toJson(resultApi.data),
            SaleResultData::class.java
        )

        if (saleResultData != null) {
            if (saleResultData.status?.code == "00") {
                Result.success(saleResultData)
            } else {
                Result.failure(Exception(saleResultData.status?.message))
            }
        } else {
            Result.failure(Exception("API returned null data"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}