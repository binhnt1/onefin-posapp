package com.onefin.posapp.ui.external

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.managers.helpers.PinInputCallback
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
import com.sunmi.pay.hardware.aidl.AidlConstants
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

enum class CheckBalanceState {
    WAITING_CARD,      // Đang chờ quẹt thẻ
    CARD_DETECTED,     // Đã detect thẻ
    WAITING_PIN,       // Đang chờ nhập PIN
    VERIFYING_PIN,     // Đang verify PIN
    CALLING_API,       // Đang gọi API check balance
    SUCCESS,           // Thành công
    ERROR              // Lỗi
}

@AndroidEntryPoint
class CheckBalanceActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var cardProcessorManager: CardProcessorManager

    @Inject
    lateinit var nfcPhoneReaderManager: NfcPhoneReaderManager

    private var deviceType: DeviceType = DeviceType.ANDROID_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceType = detectDeviceType()

        setContent {
            CheckBalanceScreen(
                deviceType = deviceType,
                apiService = apiService,
                storageService = storageService,
                cardProcessorManager = cardProcessorManager,
                nfcPhoneReaderManager = nfcPhoneReaderManager,
                onSuccess = { response -> returnSuccess(response) },
                onCancel = { errorMessage -> cancelAction(errorMessage) }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.disableForegroundDispatch(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.enableForegroundDispatch(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                nfcPhoneReaderManager.handleNfcIntent(tag)
            }
        }
    }

    private fun detectDeviceType(): DeviceType {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()

            val isSunmi = manufacturer.contains("sunmi") ||
                    model.contains("p2") ||
                    model.contains("v2") ||
                    model.contains("p1")

            if (isSunmi) DeviceType.SUNMI_P3 else DeviceType.ANDROID_PHONE
        } catch (e: Exception) {
            DeviceType.ANDROID_PHONE
        }
    }

    private fun cancelAction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                val resultIntent = paymentHelper.buildResultIntentError(pendingRequest, errorMessage)
                setResult(RESULT_OK, resultIntent)
                storageService.clearExternalPaymentContext()
                finish()
            } else {
                val currentRequest = getPaymentAppRequest()
                if (currentRequest != null) {
                    val resultIntent = paymentHelper.buildResultIntentError(currentRequest, errorMessage)
                    setResult(RESULT_OK, resultIntent)
                    storageService.clearExternalPaymentContext()
                    finish()
                }
            }
        }
        finish()
    }

    private fun returnSuccess(response: PaymentAppResponse) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            storageService.clearExternalPaymentContext()
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
fun CheckBalanceScreen(
    deviceType: DeviceType,
    apiService: ApiService,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: com.onefin.posapp.core.services.StorageService,
    onCancel: (String?) -> Unit,
    onSuccess: (PaymentAppResponse) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var timeRemaining by remember { mutableIntStateOf(60) }
    var currentState by remember { mutableStateOf(CheckBalanceState.WAITING_CARD) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinInputDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var apiErrorCode by remember { mutableStateOf<String?>(null) }
    var cardData by remember { mutableStateOf<PaymentResult.Success?>(null) }
    var onPinEnteredCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var onPinCancelledCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Function to call Check Balance API
    suspend fun callCheckBalanceAPI(data: PaymentResult.Success): Result<Map<*, *>> {
        val gson = Gson()
        return try {
            val requestSale = data.requestSale

            val requestBody = mapOf(
                "data" to mapOf(
                    "card" to mapOf(
                        "ksn" to requestSale.data.card.ksn,
                        "pin" to requestSale.data.card.pin,
                        "type" to requestSale.data.card.type,
                        "newPin" to requestSale.data.card.newPin,
                        "track1" to requestSale.data.card.track1,
                        "track2" to requestSale.data.card.track2,
                        "track3" to requestSale.data.card.track3,
                        "emvData" to requestSale.data.card.emvData,
                        "clearPan" to requestSale.data.card.clearPan,
                        "expiryDate" to requestSale.data.card.expiryDate,
                        "holderName" to requestSale.data.card.holderName,
                        "issuerName" to requestSale.data.card.issuerName,
                        "mode" to CardHelper.getCardMode(requestSale.data.card.mode),
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

            val resultApi = apiService.post("/api/card/checkBalance", requestBody) as ResultApi<*>
            val responseData = gson.fromJson(
                gson.toJson(resultApi.data),
                Map::class.java
            ) as? Map<*, *>

            val statusCode = (responseData?.get("status") as? Map<*, *>)?.get("code") as? String
            val statusMessage = (responseData?.get("status") as? Map<*, *>)?.get("message") as? String

            if (statusCode == "00") {
                // Log toàn bộ response để xem cấu trúc
                Timber.d("CheckBalance API Success - Full Response: $responseData")
                Result.success(responseData)
            } else {
                Timber.e("CheckBalance API Failed - Code: $statusCode, Message: $statusMessage")
                Result.failure(Exception(statusMessage ?: "Kiểm tra số dư thất bại"))
            }
        } catch (e: Exception) {
            Timber.e(e, "CheckBalance API Exception")
            Result.failure(e)
        }
    }

    // Function to retry
    fun resetAndRetry() {
        errorMessage = null
        showErrorDialog = false
        errorDialogMessage = ""
        apiErrorCode = null
        cardData = null
        showPinInputDialog = false
        currentState = CheckBalanceState.WAITING_CARD
        timeRemaining = 60

        when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }

        // Re-initialize
        initializeCardReader(
            context = context,
            deviceType = deviceType,
            cardProcessorManager = cardProcessorManager,
            nfcPhoneReaderManager = nfcPhoneReaderManager,
            storageService = storageService,
            onCardDetected = {
                currentState = CheckBalanceState.CARD_DETECTED
            },
            onPinRequired = { onPinEntered, onCancelled ->
                currentState = CheckBalanceState.WAITING_PIN
                onPinEnteredCallback = onPinEntered
                onPinCancelledCallback = onCancelled
                showPinInputDialog = true
            },
            onSuccess = { result ->
                cardData = result
                currentState = CheckBalanceState.CALLING_API

                // Call check balance API
                scope.launch {
                    delay(300)
                    val apiResult = callCheckBalanceAPI(result)

                    apiResult.onSuccess { response ->
                        currentState = CheckBalanceState.SUCCESS
                        // Return success response
                        val paymentRequest = storageService.getPendingPaymentRequest()
                        val successResponse = PaymentAppResponse(
                            type = "member",
                            action = PaymentAction.CHECK_BALANCE.value,
                            paymentResponseData = PaymentResponseData(
                                status = PaymentStatusCode.SUCCESS,
                                description = "Kiểm tra số dư thành công",
                                additionalData = response
                            )
                        )
                        delay(1500) // Show success state briefly
                        onSuccess(successResponse)
                    }

                    apiResult.onFailure { error ->
                        currentState = CheckBalanceState.ERROR
                        apiErrorCode = "API_ERROR"
                        errorDialogMessage = error.message ?: "Kiểm tra số dư thất bại"
                        showErrorDialog = true
                    }
                }
            },
            onError = { error ->
                currentState = CheckBalanceState.ERROR
                errorMessage = error
                apiErrorCode = "CARD_ERROR"
                errorDialogMessage = error
                showErrorDialog = true
            }
        )
    }

    // Countdown timer
    LaunchedEffect(currentState, timeRemaining) {
        if ((currentState == CheckBalanceState.WAITING_CARD || currentState == CheckBalanceState.WAITING_PIN) && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining == 0 && currentState != CheckBalanceState.SUCCESS) {
            onCancel("Hết thời gian")
        }
    }

    // Initialize on start
    LaunchedEffect(Unit) {
        initializeCardReader(
            context = context,
            deviceType = deviceType,
            cardProcessorManager = cardProcessorManager,
            nfcPhoneReaderManager = nfcPhoneReaderManager,
            storageService = storageService,
            onCardDetected = {
                currentState = CheckBalanceState.CARD_DETECTED
            },
            onPinRequired = { onPinEntered, onCancelled ->
                currentState = CheckBalanceState.WAITING_PIN
                onPinEnteredCallback = onPinEntered
                onPinCancelledCallback = onCancelled
                showPinInputDialog = true
            },
            onSuccess = { result ->
                cardData = result
                currentState = CheckBalanceState.CALLING_API

                // Call check balance API
                scope.launch {
                    delay(300)
                    val apiResult = callCheckBalanceAPI(result)

                    apiResult.onSuccess { response ->
                        currentState = CheckBalanceState.SUCCESS
                        // Return success response
                        val successResponse = PaymentAppResponse(
                            type = "member",
                            action = PaymentAction.CHECK_BALANCE.value,
                            paymentResponseData = PaymentResponseData(
                                status = PaymentStatusCode.SUCCESS,
                                description = "Kiểm tra số dư thành công",
                                additionalData = response
                            )
                        )
                        delay(1500) // Show success state briefly
                        onSuccess(successResponse)
                    }

                    apiResult.onFailure { error ->
                        currentState = CheckBalanceState.ERROR
                        apiErrorCode = "API_ERROR"
                        errorDialogMessage = error.message ?: "Kiểm tra số dư thất bại"
                        showErrorDialog = true
                    }
                }
            },
            onError = { error ->
                currentState = CheckBalanceState.ERROR
                errorMessage = error
                apiErrorCode = "CARD_ERROR"
                errorDialogMessage = error
                showErrorDialog = true
            }
        )
    }

    val logoUrl = storageService.getAccount()?.terminal?.logo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFF6FF),
                        Color.White
                    )
                )
            )
    ) {
        // Header with countdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp))

            Text(
                text = "Kiểm tra số dư",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            CircularCountdownTimer(
                timeRemaining = timeRemaining,
                modifier = Modifier.size(40.dp)
            )
        }

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Card 3D with reader
            Card3DWithReader(logoUrl = logoUrl)

            Spacer(modifier = Modifier.height(32.dp))

            // Status icon
            when (currentState) {
                CheckBalanceState.SUCCESS -> {
                    SuccessIcon()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                CheckBalanceState.CALLING_API -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                else -> {}
            }

            // Main text
            Text(
                text = when (currentState) {
                    CheckBalanceState.WAITING_CARD -> "Đặt thẻ lên đầu đọc NFC"
                    CheckBalanceState.CARD_DETECTED -> "Đã nhận diện thẻ"
                    CheckBalanceState.WAITING_PIN -> "Nhập mã PIN"
                    CheckBalanceState.VERIFYING_PIN -> "Đang xác thực PIN..."
                    CheckBalanceState.CALLING_API -> "Đang kiểm tra số dư..."
                    CheckBalanceState.SUCCESS -> "Kiểm tra thành công!"
                    CheckBalanceState.ERROR -> "Có lỗi xảy ra"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when (currentState) {
                    CheckBalanceState.SUCCESS -> Color(0xFF10B981)
                    CheckBalanceState.ERROR -> Color(0xFFEF4444)
                    else -> Color(0xFF101828)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = when (currentState) {
                    CheckBalanceState.WAITING_CARD -> "Giữ thẻ trong 2 giây"
                    CheckBalanceState.CARD_DETECTED -> "Đang xử lý..."
                    CheckBalanceState.WAITING_PIN -> "Để xác thực thẻ của bạn"
                    CheckBalanceState.VERIFYING_PIN -> "Vui lòng đợi"
                    CheckBalanceState.CALLING_API -> "Đang truy vấn thông tin..."
                    CheckBalanceState.SUCCESS -> "Thông tin đã được lưu"
                    CheckBalanceState.ERROR -> "Vui lòng thử lại"
                },
                fontSize = 14.sp,
                color = when (currentState) {
                    CheckBalanceState.CARD_DETECTED,
                    CheckBalanceState.VERIFYING_PIN,
                    CheckBalanceState.CALLING_API,
                    CheckBalanceState.SUCCESS -> Color(0xFF3B82F6)
                    else -> Color(0xFF667085)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Status box
            AnimatedStatusBox(state = currentState)

            Spacer(modifier = Modifier.height(24.dp))

            // Tip text
            if (errorMessage != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444)
                    )
                }
            } else if (currentState != CheckBalanceState.SUCCESS) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đảm bảo thẻ đã được kích hoạt",
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel button
            OutlinedButton(
                onClick = { onCancel(null) },
                enabled = currentState != CheckBalanceState.CALLING_API,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF667085)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFD0D5DD)
                )
            ) {
                Text(
                    text = "Hủy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // PIN Input Dialog
    if (showPinInputDialog) {
        PinInputBottomSheet(
            onCancel = {
                showPinInputDialog = false
                currentState = CheckBalanceState.WAITING_CARD
                onPinCancelledCallback?.invoke()
                onPinEnteredCallback = null
                onPinCancelledCallback = null
            },
            onConfirm = { pin ->
                showPinInputDialog = false
                currentState = CheckBalanceState.VERIFYING_PIN
                onPinEnteredCallback?.invoke(pin)
                onPinEnteredCallback = null
                onPinCancelledCallback = null
            }
        )
    }

    // Error Dialog
    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            errorCode = apiErrorCode,
            onRetry = {
                showErrorDialog = false
                resetAndRetry()
            },
            onCancel = {
                showErrorDialog = false
                onCancel(null)
            }
        )
    }
}

private fun initializeCardReader(
    context: Context,
    deviceType: DeviceType,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: com.onefin.posapp.core.services.StorageService,
    onCardDetected: () -> Unit,
    onPinRequired: (onPinEntered: (String) -> Unit, onCancelled: () -> Unit) -> Unit,
    onSuccess: (PaymentResult.Success) -> Unit,
    onError: (String) -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Main)
    val pendingRequest = storageService.getPendingPaymentRequest() ?: PaymentAppRequest(
        type = "member",
        action = PaymentAction.CHECK_BALANCE.value,
        merchantRequestData = MerchantRequestData(
            amount = 1
        )
    )

    val handleResult: (PaymentResult) -> Unit = { result ->
        when (result) {
            is PaymentResult.Success -> {
                onSuccess(result)
            }
            is PaymentResult.Error -> {
                onError(result.vietnameseMessage)
            }
        }
    }

    val pinCallback = object : PinInputCallback {
        override fun requestPinInput(
            onPinEntered: (String) -> Unit,
            onCancelled: () -> Unit
        ) {
            scope.launch(Dispatchers.Main) {
                onCardDetected()
                onPinRequired(onPinEntered, onCancelled)
            }
        }
    }

    when (deviceType) {
        DeviceType.SUNMI_P2,
        DeviceType.SUNMI_P3 -> {
            cardProcessorManager.setPinInputCallback(pinCallback)
            cardProcessorManager.initialize { success, error ->
                if (success) {
                    cardProcessorManager.startPayment(
                        paymentRequest = pendingRequest,
                        onProcessingComplete = handleResult,
                        cardTypes = listOf(AidlConstants.CardType.MIFARE)
                    )
                } else {
                    onError(error ?: "Không thể khởi tạo đầu đọc thẻ")
                }
            }
        }
        DeviceType.ANDROID_PHONE -> {
            nfcPhoneReaderManager.initialize { success, error ->
                if (success) {
                    nfcPhoneReaderManager.startPayment(
                        paymentRequest = pendingRequest,
                        onProcessingComplete = handleResult
                    )
                } else {
                    onError(error ?: "Không thể khởi tạo NFC")
                }
            }
        }
    }
}

@Composable
private fun CircularCountdownTimer(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / 60,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "progress"
    )

    val progressColor = when {
        timeRemaining <= 10 -> Color(0xFFEF4444)
        timeRemaining <= 30 -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f

            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = radius - strokeWidth / 2,
                style = Stroke(width = strokeWidth)
            )

            val sweepAngle = -360f * progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter - strokeWidth, diameter - strokeWidth)
            )
        }

        Text(
            text = "$timeRemaining",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = progressColor
        )
    }
}

@Composable
private fun Card3DWithReader(logoUrl: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.offset(y = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 360.dp, height = 200.dp)
                    .offset(x = 6.dp, y = 6.dp)
                    .background(
                        color = Color(0x20000000),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .size(width = 360.dp, height = 200.dp)
                    .offset(x = 3.dp, y = 3.dp)
                    .background(
                        color = Color(0x30000000),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .size(width = 360.dp, height = 200.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0D7C66),
                                Color(0xFF16A085)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0x40FFFFFF),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!logoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(36.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GreenCard",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Text(
                                    text = "Tập đoàn Mai Linh",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }

                        BlinkingNFCIcon()
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(0xFFFFD700),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFFFFE44D),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "↓↓↓",
            fontSize = 24.sp,
            color = Color(0xFF3B82F6).copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NFCReaderBox()
    }
}

@Composable
private fun BlinkingNFCIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Text(
        text = "))",
        fontSize = 20.sp,
        color = Color.White.copy(alpha = alpha),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun NFCReaderBox() {
    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 60.dp)
            .background(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 2.dp,
                color = Color(0xFF475569),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        NFCScannerLine(width = 180.dp)
    }
}

@Composable
private fun NFCScannerLine(width: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val barSpacing = 8.dp.toPx()
        val totalBarsWidth = 3 * barSpacing
        val centerX = size.width / 2 + (size.width * offsetX)

        for (i in 0..3) {
            val x = centerX - (totalBarsWidth / 2) + (i * barSpacing)
            drawLine(
                color = Color(0xFF3B82F6),
                start = Offset(x, size.height * 0.2f),
                end = Offset(x, size.height * 0.8f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AnimatedStatusBox(state: CheckBalanceState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFD0D5DD),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = Color(0xFFF9FAFB),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state != CheckBalanceState.SUCCESS) {
                SpinningRefreshIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = when (state) {
                    CheckBalanceState.WAITING_CARD -> "Đang chờ thẻ..."
                    CheckBalanceState.CARD_DETECTED -> "Đã nhận diện thẻ"
                    CheckBalanceState.WAITING_PIN -> "Vui lòng nhập PIN"
                    CheckBalanceState.VERIFYING_PIN -> "Đang xác thực..."
                    CheckBalanceState.CALLING_API -> "Đang truy vấn..."
                    CheckBalanceState.SUCCESS -> "✓ Hoàn thành"
                    CheckBalanceState.ERROR -> "Có lỗi xảy ra"
                },
                fontSize = 14.sp,
                color = when (state) {
                    CheckBalanceState.SUCCESS -> Color(0xFF10B981)
                    CheckBalanceState.ERROR -> Color(0xFFEF4444)
                    else -> Color(0xFF667085)
                },
                fontWeight = if (state == CheckBalanceState.SUCCESS) FontWeight.Bold else FontWeight.Normal
            )

            if (state != CheckBalanceState.SUCCESS && state != CheckBalanceState.ERROR) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ")))",
                    fontSize = 14.sp,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    }
}

@Composable
private fun SpinningRefreshIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFF3B82F6),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun SuccessIcon() {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale.value)
            .background(
                color = Color(0xFF10B981),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✓",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}