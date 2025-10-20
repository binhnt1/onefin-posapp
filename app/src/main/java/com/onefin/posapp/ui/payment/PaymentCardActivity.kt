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
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ActionButtons
import com.onefin.posapp.ui.payment.components.AmountCard
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.ModernHeader
import com.onefin.posapp.ui.payment.components.PaymentStatusCard
import com.onefin.posapp.ui.payment.components.SuccessAnimationScreen
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
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
                    paymentAppRequest = requestData,
                    onCancel = { cancelPayment() },
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
    paymentAppRequest: PaymentAppRequest,
    onCancel: () -> Unit,
    onSuccess: (RequestSale) -> Unit,
    onError: (String, String) -> Unit
) {
    var cardInfo by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Đang khởi tạo...") }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as PaymentCardActivity
    val paymentManager = remember { activity.paymentManager }
    val ttsManager = remember { activity.ttsManager }

    val amount = paymentAppRequest.merchantRequestData?.amount ?: 0

    fun startCardReading() {
        paymentState = PaymentState.WAITING_CARD
        statusMessage = "Vui lòng quẹt/chạm/cắm thẻ"

        paymentManager.startReadCard(
            paymentAppRequest = paymentAppRequest,
            onCardRead = { requestSale ->
                Timber.tag("PaymentCard").d("🎉 CARD READ SUCCESS")

                paymentState = PaymentState.CARD_DETECTED
                currentRequestSale = requestSale
                cardInfo = UtilHelper.maskCardNumber(requestSale.data.card.clearPan)
                statusMessage = "Đã phát hiện thẻ"

                scope.launch {
                    delay(1500)
                    paymentState = PaymentState.PROCESSING
                    statusMessage = "Đang xử lý giao dịch..."

                    processPayment(
                        requestSale = requestSale,
                        onSuccess = { sale ->
                            Timber.tag("PaymentCard").d("✅ Payment SUCCESS")

                            // ✅ THÊM: Hiển thị success animation
                            paymentState = PaymentState.SUCCESS
                            currentRequestSale = sale
                            showSuccessAnimation = true

                            // ✅ Text-to-speech
                            ttsManager.speak("Giao dịch thành công. Số tiền $amount đồng")
                        },
                        onError = { code, msg ->
                            Timber.tag("PaymentCard").e("❌ Payment FAILED: $code - $msg")
                            paymentState = PaymentState.ERROR
                            statusMessage = msg
                            errorDialogMessage = msg
                            showErrorDialog = true

                            ttsManager.speak("Giao dịch thất bại. $msg")
                        }
                    )
                }
            },
            onError = { error ->
                Timber.tag("PaymentCard").e("❌ READ CARD ERROR: $error")
                paymentState = PaymentState.ERROR
                statusMessage = "Lỗi đọc thẻ: $error"
                errorDialogMessage = error
                showErrorDialog = true

                ttsManager.speak("Lỗi đọc thẻ")
            }
        )
    }

    // ✅ Success Animation Overlay
    if (showSuccessAnimation && currentRequestSale != null) {
        SuccessAnimationScreen(
            amount = amount,
            cardNumber = currentRequestSale!!.data.card.clearPan,
            onComplete = {
                onSuccess(currentRequestSale!!)
            }
        )
    }

    // ✅ Error Dialog
    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            onRetry = {
                showErrorDialog = false
                startCardReading()
            },
            onCancel = {
                showErrorDialog = false
                onCancel()
            }
        )
    }

    // Initialize once
    LaunchedEffect(Unit) {
        if (!isInitialized) {
            isInitialized = true
            paymentManager.initialize(
                onReady = {
                    Timber.tag("PaymentCard").d("✅ Payment manager ready")
                    startCardReading()
                },
                onError = { error ->
                    Timber.tag("PaymentCard").e("❌ INIT ERROR: $error")
                    paymentState = PaymentState.ERROR
                    statusMessage = "Lỗi khởi tạo: $error"
                    errorDialogMessage = error
                    showErrorDialog = true
                }
            )
        }
    }

    // ✅ Main UI - Chỉ hiện khi CHƯA success
    if (!showSuccessAnimation) {
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
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun processPayment(
    requestSale: RequestSale,
    onSuccess: (RequestSale) -> Unit,
    onError: (String, String) -> Unit
) {
    val gson = Gson()
    val jsonString = gson.toJson(requestSale)
    Timber.tag("Payment").d("Processing payment:")
    Timber.tag("Payment").d(jsonString)

    GlobalScope.launch {
        delay(2000)
        onSuccess(requestSale)
    }
}