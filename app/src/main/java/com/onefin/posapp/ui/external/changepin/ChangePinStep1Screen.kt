package com.onefin.posapp.ui.external.changepin

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
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.managers.helpers.PinInputCallback
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
import com.sunmi.pay.hardware.aidl.AidlConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Step1State {
    WAITING_CARD,      // Đang chờ quẹt thẻ
    CARD_DETECTED,     // Đã detect thẻ
    WAITING_PIN,       // Đang chờ nhập PIN
    VERIFYING_PIN,     // Đang verify PIN
    SUCCESS            // Thành công
}

@Composable
fun ChangePinStep1Screen(
    deviceType: DeviceType,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: StorageService,
    onCancel: () -> Unit,
    onTimeout: () -> Unit,
    onOldPinVerified: (PaymentResult.Success) -> Unit
) {
    val context = LocalContext.current
    var timeRemaining by remember { mutableIntStateOf(60) }
    var step1State by remember { mutableStateOf(Step1State.WAITING_CARD) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinInputDialog by remember { mutableStateOf(false) }
    var onPinEnteredCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var onPinCancelledCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Countdown timer - chỉ chạy khi đang chờ thẻ hoặc chờ PIN
    LaunchedEffect(step1State, timeRemaining) {
        if ((step1State == Step1State.WAITING_CARD || step1State == Step1State.WAITING_PIN) && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining == 0 && step1State != Step1State.SUCCESS) {
            onTimeout()
        }
    }

    // Initialize and start card reading
    LaunchedEffect(Unit) {
        initializeCardReader(
            context = context,
            deviceType = deviceType,
            cardProcessorManager = cardProcessorManager,
            nfcPhoneReaderManager = nfcPhoneReaderManager,
            storageService = storageService,
            onCardDetected = {
                step1State = Step1State.CARD_DETECTED
            },
            onPinRequired = { onPinEntered, onCancelled ->
                // Card đã được detect, giờ cần nhập PIN
                step1State = Step1State.WAITING_PIN
                onPinEnteredCallback = onPinEntered
                onPinCancelledCallback = onCancelled
                showPinInputDialog = true
            },
            onSuccess = { result ->
                step1State = Step1State.SUCCESS
                onOldPinVerified(result)
            },
            onError = { error ->
                step1State = Step1State.WAITING_CARD
                errorMessage = error
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
                text = "Đổi mã PIN",
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

        // Progress Indicator
        StepProgressIndicator()

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

            // Card 3D with depth
            Card3DWithReader(logoUrl = logoUrl)

            Spacer(modifier = Modifier.height(32.dp))

            // Main text - thay đổi theo state
            Text(
                text = when (step1State) {
                    Step1State.WAITING_CARD -> "Đặt thẻ lên đầu đọc NFC"
                    Step1State.CARD_DETECTED -> "Đã nhận diện thẻ"
                    Step1State.WAITING_PIN -> "Nhập mã PIN hiện tại"
                    Step1State.VERIFYING_PIN -> "Đang xác thực PIN..."
                    Step1State.SUCCESS -> "Thành công!"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = when (step1State) {
                    Step1State.WAITING_CARD -> "Giữ thẻ trong 2 giây"
                    Step1State.CARD_DETECTED -> "Đang xử lý..."
                    Step1State.WAITING_PIN -> "Để xác thực thẻ của bạn"
                    Step1State.VERIFYING_PIN -> "Vui lòng đợi"
                    Step1State.SUCCESS -> "Chuyển sang bước tiếp theo"
                },
                fontSize = 14.sp,
                color = when (step1State) {
                    Step1State.CARD_DETECTED,
                    Step1State.VERIFYING_PIN,
                    Step1State.SUCCESS -> Color(0xFF3B82F6)
                    else -> Color(0xFF667085)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Status box with animation
            AnimatedStatusBox(state = step1State)

            Spacer(modifier = Modifier.height(24.dp))

            // Error or Tip text
            if (errorMessage != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡",
                        fontSize = 16.sp
                    )
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
                onClick = onCancel,
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
                step1State = Step1State.WAITING_CARD
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
            }
        )
    }
}

private fun initializeCardReader(
    context: Context,
    deviceType: DeviceType,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: StorageService,
    onCardDetected: () -> Unit,
    onPinRequired: (onPinEntered: (String) -> Unit, onCancelled: () -> Unit) -> Unit,
    onSuccess: (PaymentResult.Success) -> Unit,
    onError: (String) -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Main)
    val pendingRequest = storageService.getPendingPaymentRequest() ?: PaymentAppRequest(
        type = "member",
        action = PaymentAction.CHANGE_PIN.value,
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

    // Setup PIN callback giống PaymentCardActivity
    val pinCallback = object : PinInputCallback {
        override fun requestPinInput(
            onPinEntered: (String) -> Unit,
            onCancelled: () -> Unit
        ) {
            scope.launch(Dispatchers.Main) {
                onCardDetected() // Thông báo đã detect card
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
                    // Only allow MIFARE cards for PIN change
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
        targetValue = timeRemaining.toFloat() / 180,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearEasing
        ),
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
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                ),
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
private fun StepProgressIndicator() {
    val currentStep = 1
    val steps = listOf("Xác thực", "PIN mới", "Hoàn tất")
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.offset(y = 8.dp)
        ) {
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
        text = ")",
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

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
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
            SpinningRefreshIcon()

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = when (state) {
                    Step1State.WAITING_CARD -> "Đang chờ thẻ..."
                    Step1State.CARD_DETECTED -> "Đã nhận diện thẻ"
                    Step1State.WAITING_PIN -> "Vui lòng nhập PIN"
                    Step1State.VERIFYING_PIN -> "Đang xác thực..."
                    Step1State.SUCCESS -> "Thành công!"
                },
                fontSize = 14.sp,
                color = Color(0xFF667085)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = ")))",
                fontSize = 14.sp,
                color = Color(0xFF3B82F6)
            )
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
            .graphicsLayer {
                rotationZ = rotation
            }
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