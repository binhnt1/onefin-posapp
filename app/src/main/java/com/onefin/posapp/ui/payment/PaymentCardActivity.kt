package com.onefin.posapp.ui.payment

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.onefin.posapp.core.managers.SunmiPaymentManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.managers.helpers.PaymentTTSHelper
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
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
import org.json.JSONObject
import timber.log.Timber
import java.io.Serializable

@AndroidEntryPoint
class PaymentCardActivity : BaseActivity() {

    private val gson = Gson()

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentManager: SunmiPaymentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawObject: Serializable? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("REQUEST_DATA", Serializable::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("REQUEST_DATA")
            }

        @Suppress("UNCHECKED_CAST")
        val requestData = rawObject as? PaymentAppRequest
        if (requestData != null) {
            setContent {
                PaymentCardScreen(
                    apiService = apiService,
                    onCancel = { cancelPayment() },
                    paymentAppRequest = requestData,
                    onSuccess = { requestSale -> finishWithSuccess(requestSale) },
                    onError = { code, message -> finishWithError(code, message) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentManager.cancelReadCard()
    }

    private fun cancelPayment() {
        paymentManager.cancelReadCard()
        finishWithError("USER_CANCELLED", "Người dùng hủy giao dịch")
    }

    private fun finishWithSuccess(requestSale: RequestSale) {
        val paymentJson = gson.toJson(requestSale)
        val result = JSONObject().apply {
            put("status", "success")
            put("request_id", requestSale.requestId)
            put("card_number", requestSale.data.card.clearPan)
            put("masked_pan", UtilHelper.maskCardNumber(requestSale.data.card.clearPan))
            put("expiry_date", requestSale.data.card.expiryDate)
            put("card_mode", requestSale.data.card.mode)
            put("pos_entry_mode", requestSale.data.device.posEntryMode)
            put("amount", requestSale.data.payment.transAmount)
            put("currency", requestSale.data.payment.currency)
            put("transaction_id", CardHelper.generateTransactionId())
            put("timestamp", System.currentTimeMillis())
            put("payment_data", paymentJson)
        }

        val intent = Intent().apply {
            putExtra("action", 1)
            putExtra("type", "card")
            putExtra("payment_response_data", result.toString())
            putExtra("payment_request_json", paymentJson)
        }

        setResult(RESULT_OK, intent)
        finish()
    }

    private fun finishWithError(errorCode: String, errorMessage: String) {
        val result = JSONObject().apply {
            put("status", "error")
            put("error_code", errorCode)
            put("error_message", errorMessage)
            put("timestamp", System.currentTimeMillis())
        }

        val intent = Intent().apply {
            putExtra("action", 0)
            putExtra("type", "card")
            putExtra("payment_response_data", result.toString())
        }

        setResult(RESULT_CANCELED, intent)
        finish()
    }
}

@Composable
fun PaymentCardScreen(
    onCancel: () -> Unit,
    apiService: ApiService,
    onSuccess: (RequestSale) -> Unit,
    onError: (String, String) -> Unit,
    paymentAppRequest: PaymentAppRequest,
) {
    var cardInfo by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Đang khởi tạo...") }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as PaymentCardActivity
    val paymentManager = remember { activity.paymentManager }
    val ttsManager = remember { activity.ttsManager }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val amount = paymentAppRequest.merchantRequestData?.amount ?: 0

    fun resetAndRetry() {
        Timber.tag("PaymentCard").d("🔄 Reset and retry")

        // Reset all state
        showErrorDialog = false
        errorDialogMessage = ""
        errorCode = null
        cardInfo = ""
        statusMessage = "Đang khởi tạo..."
        paymentState = PaymentState.INITIALIZING
        currentRequestSale = null

        // Cancel ongoing operation
        paymentManager.cancelReadCard()

        // ✅ Tăng retryTrigger để trigger LaunchedEffect
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

                            // ✅ 1. CARD_DETECTED - chờ 1s
                            paymentState = PaymentState.CARD_DETECTED
                            currentRequestSale = requestSale
                            cardInfo = UtilHelper.maskCardNumber(requestSale.data.card.clearPan)
                            statusMessage = "Đã phát hiện thẻ"

                            delay(1000) // Chờ 1s

                            // ✅ 2. PROCESSING - chờ 3s
                            paymentState = PaymentState.PROCESSING
                            statusMessage = "Đang xử lý giao dịch..."

                            scope.launch {
                                val result = processPayment(apiService, requestSale)

                                result.onSuccess { sale ->
                                    Timber.tag("PaymentCard").d("✅ Payment SUCCESS")
                                    currentRequestSale = sale
                                    paymentState = PaymentState.SUCCESS
                                    statusMessage = "Giao dịch thành công"

                                    val ttsMessage = PaymentTTSHelper.getSuccessTTSMessage(amount)
                                    ttsManager.speak(ttsMessage)

                                    // Auto close after 3 seconds
                                    delay(3000)
                                    onSuccess(sale)
                                }

                                result.onFailure { error ->
                                    Timber.tag("PaymentCard").e("❌ Payment FAILED: ${error.message}")
                                    val errorMessage = error.message ?: "Giao dịch thất bại. Vui lòng thử lại"
                                    errorCode = "API_ERROR"
                                    errorDialogMessage = errorMessage
                                    showErrorDialog = true
                                    ttsManager.speak("Giao dịch thất bại. $errorMessage")
                                }
                            }
                        }

                        is PaymentResult.Error -> {
                            Timber.tag("PaymentCard").e(
                                "❌ READ CARD ERROR: ${result.type} - ${result.getFullMessage()}"
                            )
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
): Result<RequestSale> {
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
                "merchant_request_data" to Gson().toJson(requestSale.requestData.merchantRequestData)
            ),
            "requestId" to requestSale.requestId,
        )

        // Gọi API /api/card/sale
        val resultApi = apiService.post("/api/card/sale", requestBody) as ResultApi<*>

        // Parse response nếu cần
        val saleResponse = gson.fromJson(
            gson.toJson(resultApi.data),
            RequestSale::class.java
        )
        Result.success(saleResponse ?: requestSale)
    } catch (e: Exception) {
        Timber.tag("Payment").e("❌ API Error: ${e.message}")
        Result.failure(e)
    }
}