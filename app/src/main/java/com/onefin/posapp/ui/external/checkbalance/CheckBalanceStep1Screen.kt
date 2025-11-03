package com.onefin.posapp.ui.external.checkbalance

import android.content.Context
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
import androidx.compose.ui.geometry.Offset
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
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.managers.helpers.PinInputCallback
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.MemberResultData
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
import com.sunmi.pay.hardware.aidl.AidlConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Step1State {
    INITIALIZING,
    WAITING_CARD,
    CARD_DETECTED,
    WAITING_PIN,
    VERIFYING_PIN,
    CALLING_API,
    SUCCESS
}

@Composable
fun CheckBalanceStep1Screen(
    deviceType: DeviceType,
    apiService: ApiService,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: StorageService,
    onCancel: () -> Unit,
    onTimeout: () -> Unit,
    onBalanceChecked: (MemberResultData) -> Unit
) {
    val context = LocalContext.current

    var timeRemaining by remember { mutableIntStateOf(60) }
    var step1State by remember { mutableStateOf(Step1State.INITIALIZING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinInputDialog by remember { mutableStateOf(false) }
    var onPinEnteredCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var onPinCancelledCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(step1State, timeRemaining) {
        if ((step1State == Step1State.WAITING_CARD || step1State == Step1State.WAITING_PIN) && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining == 0 && step1State != Step1State.SUCCESS) {
            onTimeout()
        }
    }

    LaunchedEffect(Unit) {
        initializeCardReader(
            context = context,
            deviceType = deviceType,
            apiService = apiService,
            cardProcessorManager = cardProcessorManager,
            nfcPhoneReaderManager = nfcPhoneReaderManager,
            storageService = storageService,
            onInitComplete = {
                step1State = Step1State.WAITING_CARD
            },
            onCardDetected = {
                step1State = Step1State.CARD_DETECTED
            },
            onPinRequired = { onPinEntered, onCancelled ->
                step1State = Step1State.WAITING_PIN
                onPinEnteredCallback = onPinEntered
                onPinCancelledCallback = onCancelled
                showPinInputDialog = true
            },
            onApiCalling = {
                step1State = Step1State.CALLING_API
            },
            onSuccess = { result ->
                step1State = Step1State.SUCCESS
                onBalanceChecked(result)
            },
            onError = { error ->
                step1State = Step1State.WAITING_CARD
                errorMessage = error
            }
        )
    }

    // Show loading screen while initializing
    if (step1State == Step1State.INITIALIZING) {
        InitializingScreen(deviceType = deviceType)
        return
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

        StepProgressIndicator()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            Card3DWithReader(logoUrl = logoUrl)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when (step1State) {
                    Step1State.INITIALIZING -> "Đang khởi tạo..."
                    Step1State.WAITING_CARD -> "Đặt thẻ lên đầu đọc NFC"
                    Step1State.CARD_DETECTED -> "Đã nhận diện thẻ"
                    Step1State.WAITING_PIN -> "Nhập mã PIN"
                    Step1State.VERIFYING_PIN -> "Đang xác thực PIN..."
                    Step1State.CALLING_API -> "Đang kiểm tra số dư..."
                    Step1State.SUCCESS -> "Thành công!"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (step1State) {
                    Step1State.INITIALIZING -> "Vui lòng đợi"
                    Step1State.WAITING_CARD -> "Giữ thẻ trong 2 giây"
                    Step1State.CARD_DETECTED -> "Đang xử lý..."
                    Step1State.WAITING_PIN -> "Để kiểm tra tài khoản"
                    Step1State.VERIFYING_PIN, Step1State.CALLING_API -> "Vui lòng đợi"
                    Step1State.SUCCESS -> "Đã lấy thông tin số dư"
                },
                fontSize = 14.sp,
                color = when (step1State) {
                    Step1State.CARD_DETECTED, Step1State.VERIFYING_PIN,
                    Step1State.CALLING_API, Step1State.SUCCESS -> Color(0xFF3B82F6)
                    else -> Color(0xFF667085)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedStatusBox(state = step1State)

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage!!, fontSize = 12.sp, color = Color(0xFFEF4444))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Hủy", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPinInputDialog) {
        PinInputBottomSheet(
            onCancel = {
                showPinInputDialog = false
                onPinCancelledCallback?.invoke()
                onPinEnteredCallback = null
                onPinCancelledCallback = null
            },
            onConfirm = { pin ->
                showPinInputDialog = false
                step1State = Step1State.VERIFYING_PIN
                onPinEnteredCallback?.invoke(pin)
                onPinEnteredCallback = null
                onPinCancelledCallback = null
            },
        )
    }
}

@Composable
private fun InitializingScreen(deviceType: DeviceType) {
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
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kiểm tra số dư",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Spinning loader
            SpinningLoader(modifier = Modifier.size(80.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Đang khởi tạo thiết bị",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (deviceType) {
                    DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> "Đang kết nối với đầu đọc thẻ Sunmi..."
                    DeviceType.ANDROID_PHONE -> "Đang khởi tạo NFC..."
                },
                fontSize = 14.sp,
                color = Color(0xFF667085),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LoadingDots()
        }
    }
}

@Composable
private fun SpinningLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFF3B82F6),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        easing = LinearEasing,
                        delayMillis = index * 200
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            Text(
                text = "●",
                fontSize = 16.sp,
                color = Color(0xFF3B82F6).copy(alpha = alpha),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private fun initializeCardReader(
    context: Context,
    deviceType: DeviceType,
    apiService: ApiService,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: StorageService,
    onInitComplete: () -> Unit,
    onCardDetected: () -> Unit,
    onPinRequired: (onPinEntered: (String) -> Unit, onCancelled: () -> Unit) -> Unit,
    onApiCalling: () -> Unit,
    onSuccess: (MemberResultData) -> Unit,
    onError: (String) -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Main)
    val pendingRequest = storageService.getPendingPaymentRequest() ?: PaymentAppRequest(
        type = "member",
        action = PaymentAction.CHECK_BALANCE.value,
        merchantRequestData = MerchantRequestData(amount = 1)
    )

    val handleResult: (PaymentResult) -> Unit = { result ->
        when (result) {
            is PaymentResult.Success -> {
                scope.launch {
                    onApiCalling()
                    val balanceResult = processCheckBalance(apiService, result)
                    balanceResult.fold(
                        onSuccess = { data -> onSuccess(data) },
                        onFailure = { e -> onError(e.message ?: "Lỗi kiểm tra số dư") }
                    )
                }
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
        DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
            cardProcessorManager.setPinInputCallback(pinCallback)
            cardProcessorManager.initialize { success, error ->
                if (success) {
                    onInitComplete()
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
            scope.launch {
                delay(500)
                onInitComplete()
                nfcPhoneReaderManager.startPayment(
                    paymentRequest = pendingRequest,
                    onProcessingComplete = handleResult
                )
            }
        }
    }
}

suspend fun processCheckBalance(
    apiService: ApiService,
    data: PaymentResult.Success
): Result<MemberResultData> {
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
        if (resultApi.isSuccess()) {
            val responseData = gson.fromJson(
                gson.toJson(resultApi.data),
                MemberResultData::class.java
            )
            if (responseData.respcode == "00") {
                Result.success(responseData)
            } else {
                Result.failure(Exception(responseData.errorDesc))
            }
        } else {
            Result.failure(Exception(resultApi.description))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Composable
private fun StepProgressIndicator() {
    val currentStep = 1
    val steps = listOf("Xác thực", "Hoàn tất")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val isActive = stepNumber == currentStep
            val isCompleted = stepNumber < currentStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when {
                                isCompleted -> Color(0xFF3B82F6)
                                isActive -> Color(0xFF3B82F6)
                                else -> Color.White
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = when {
                                isCompleted -> Color(0xFF3B82F6)
                                isActive -> Color(0xFF3B82F6)
                                else -> Color(0xFFD0D5DD)
                            },
                            shape = CircleShape
                        )
                ) {
                    if (isCompleted) {
                        Text(
                            text = "✓",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "$stepNumber",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else Color(0xFF667085)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isActive -> Color(0xFF101828)
                        isCompleted -> Color(0xFF3B82F6)
                        else -> Color(0xFF667085)
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .offset(y = (-10).dp)
                        .background(
                            color = if (stepNumber < currentStep) Color(0xFF3B82F6) else Color(0xFFD0D5DD)
                        )
                )
            }
        }
    }

    HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))
}

@Composable
private fun Card3DWithReader(logoUrl: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .height(180.dp)
                .graphicsLayer {
                    rotationX = -10f
                    cameraDistance = 12f * density
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E40AF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFF1E40AF))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
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
                            BlinkingNFCIcon()
                        }
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
        Text(text = "↓↓↓", fontSize = 24.sp, color = Color(0xFF3B82F6).copy(alpha = 0.6f))
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
            .background(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
            .border(width = 2.dp, color = Color(0xFF475569), shape = RoundedCornerShape(8.dp)),
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
private fun AnimatedStatusBox(state: Step1State) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFFD0D5DD), shape = RoundedCornerShape(12.dp))
            .background(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state != Step1State.SUCCESS) {
                SpinningRefreshIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = when (state) {
                    Step1State.INITIALIZING -> "Đang khởi tạo..."
                    Step1State.WAITING_CARD -> "Đang chờ thẻ..."
                    Step1State.CARD_DETECTED -> "Đã nhận diện thẻ"
                    Step1State.WAITING_PIN -> "Vui lòng nhập PIN"
                    Step1State.VERIFYING_PIN -> "Đang xác thực..."
                    Step1State.CALLING_API -> "Đang truy vấn..."
                    Step1State.SUCCESS -> "✓ Hoàn thành"
                },
                fontSize = 14.sp,
                color = if (state == Step1State.SUCCESS) Color(0xFF10B981) else Color(0xFF667085),
                fontWeight = if (state == Step1State.SUCCESS) FontWeight.Bold else FontWeight.Normal
            )
            if (state != Step1State.SUCCESS) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = ")))", fontSize = 14.sp, color = Color(0xFF3B82F6))
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
private fun CircularCountdownTimer(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / 60,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )
    val progressColor = when {
        timeRemaining <= 10 -> Color(0xFFEF4444)
        timeRemaining <= 30 -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0xFFE5E7EB), style = Stroke(width = 3.dp.toPx()))
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = timeRemaining.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = progressColor
        )
    }
}