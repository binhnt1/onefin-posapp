package com.onefin.posapp.ui.external.changepin

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.MemberResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Step2State {
    ENTER_NEW_PIN,      // Nhập PIN mới lần 1
    CONFIRM_NEW_PIN,    // Nhập PIN mới lần 2
    VALIDATING,         // Đang validate
    CALLING_API,        // Đang gọi API
    MISMATCH_ERROR      // 2 lần nhập không khớp
}

@Composable
fun ChangePinStep2Screen(
    onCancel: () -> Unit,
    onTimeout: () -> Unit,
    apiService: ApiService,
    cardData: PaymentResult.Success,
    onSuccess: (MemberResultData) -> Unit
) {
    var timeRemaining by remember { mutableIntStateOf(60) }
    var step2State by remember { mutableStateOf(Step2State.ENTER_NEW_PIN) }
    var firstPinValue by remember { mutableStateOf("") }
    var secondPinValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var apiErrorCode by remember { mutableStateOf<String?>(null) }
    val pinLength = 6
    val scope = rememberCoroutineScope()

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        if (timeRemaining == 0) {
            onTimeout()
        }
    }

    // Function to call PIN change API
    suspend fun processChangePin(newPin: String): Result<Any?> {
        val gson = Gson()
        return try {
            val requestSale = cardData.requestSale

            // Build request body similar to processPayment
            val requestBody = mapOf(
                "data" to mapOf(
                    "card" to mapOf(
                        "newPin" to newPin,
                        "ksn" to requestSale.data.card.ksn,
                        "pin" to requestSale.data.card.pin,
                        "type" to requestSale.data.card.type,
                        "mode" to requestSale.data.card.mode,
                        "track1" to requestSale.data.card.track1,
                        "track2" to requestSale.data.card.track2,
                        "track3" to requestSale.data.card.track3,
                        "emvData" to requestSale.data.card.emvData,
                        "clearPan" to requestSale.data.card.clearPan,
                        "expiryDate" to requestSale.data.card.expiryDate,
                        "holderName" to requestSale.data.card.holderName,
                        "issuerName" to requestSale.data.card.issuerName,
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

            val resultApi = apiService.post("/api/card/pinChange", requestBody) as ResultApi<*>
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Title - changes based on state
            AnimatedContent(
                targetState = step2State,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it } + fadeOut()
                    )
                },
                label = "title_animation"
            ) { state ->
                Text(
                    text = when (state) {
                        Step2State.ENTER_NEW_PIN -> "Nhập mã PIN mới"
                        Step2State.CONFIRM_NEW_PIN -> "Xác nhận mã PIN mới"
                        Step2State.VALIDATING -> "Đang xác thực..."
                        Step2State.CALLING_API -> "Đang đổi PIN..."
                        Step2State.MISMATCH_ERROR -> "Mã PIN không khớp"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (state) {
                        Step2State.MISMATCH_ERROR -> Color(0xFFEF4444)
                        else -> Color(0xFF101828)
                    },
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = when (step2State) {
                    Step2State.ENTER_NEW_PIN -> "Mã PIN phải có 6 chữ số"
                    Step2State.CONFIRM_NEW_PIN -> "Nhập lại để xác nhận"
                    Step2State.VALIDATING -> "Vui lòng đợi..."
                    Step2State.CALLING_API -> "Đang xử lý yêu cầu..."
                    Step2State.MISMATCH_ERROR -> "Vui lòng thử lại"
                },
                fontSize = 14.sp,
                color = Color(0xFF667085),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN Display
            val currentPinValue = when (step2State) {
                Step2State.ENTER_NEW_PIN -> firstPinValue
                else -> secondPinValue
            }

            PinDotsDisplay(
                pinLength = currentPinValue.length,
                hasError = step2State == Step2State.MISMATCH_ERROR
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Numeric Keyboard
            NumericKeyboard(
                enabled = step2State != Step2State.VALIDATING && step2State != Step2State.CALLING_API,
                onNumberClick = { number ->
                    when (step2State) {
                        Step2State.ENTER_NEW_PIN -> {
                            if (firstPinValue.length < pinLength) {
                                firstPinValue += number
                            }
                        }
                        Step2State.CONFIRM_NEW_PIN -> {
                            if (secondPinValue.length < pinLength) {
                                secondPinValue += number
                            }
                        }
                        else -> {}
                    }
                },
                onDeleteClick = {
                    when (step2State) {
                        Step2State.ENTER_NEW_PIN -> {
                            if (firstPinValue.isNotEmpty()) {
                                firstPinValue = firstPinValue.dropLast(1)
                            }
                        }
                        Step2State.CONFIRM_NEW_PIN -> {
                            if (secondPinValue.isNotEmpty()) {
                                secondPinValue = secondPinValue.dropLast(1)
                            }
                        }
                        else -> {}
                    }
                },
                onClearClick = {
                    when (step2State) {
                        Step2State.ENTER_NEW_PIN -> {
                            firstPinValue = ""
                        }
                        Step2State.CONFIRM_NEW_PIN -> {
                            secondPinValue = ""
                        }
                        else -> {}
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error message or tip
            if (errorMessage != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (step2State) {
                            Step2State.ENTER_NEW_PIN -> "Chọn mã PIN dễ nhớ nhưng khó đoán"
                            Step2State.CONFIRM_NEW_PIN -> "Nhập lại mã PIN vừa tạo"
                            else -> ""
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF667085),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back/Cancel Button
                OutlinedButton(
                    onClick = {
                        if (step2State == Step2State.CONFIRM_NEW_PIN) {
                            // Go back to first input
                            secondPinValue = ""
                            errorMessage = null
                            step2State = Step2State.ENTER_NEW_PIN
                        } else {
                            // Cancel entire flow
                            onCancel()
                        }
                    },
                    enabled = step2State != Step2State.VALIDATING && step2State != Step2State.CALLING_API,
                    modifier = Modifier
                        .weight(1f)
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
                        text = if (step2State == Step2State.CONFIRM_NEW_PIN) "Quay lại" else "Hủy",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Continue/Confirm Button
                Button(
                    onClick = {
                        when (step2State) {
                            Step2State.ENTER_NEW_PIN -> {
                                if (firstPinValue.length == pinLength) {
                                    step2State = Step2State.CONFIRM_NEW_PIN
                                }
                            }
                            Step2State.CONFIRM_NEW_PIN -> {
                                if (secondPinValue.length == pinLength) {
                                    scope.launch {
                                        step2State = Step2State.VALIDATING
                                        delay(300)

                                        if (firstPinValue == secondPinValue) {
                                            // Call API
                                            step2State = Step2State.CALLING_API
                                            val result = processChangePin(firstPinValue)
                                            result.onSuccess { message ->
                                                onSuccess(message as MemberResultData)
                                            }

                                            result.onFailure { error ->
                                                // Show error dialog
                                                step2State = Step2State.CONFIRM_NEW_PIN
                                                apiErrorCode = "API_ERROR"
                                                errorDialogMessage = error.message ?: "Đổi PIN thất bại"
                                                showErrorDialog = true
                                            }
                                        } else {
                                            // Mismatch error
                                            step2State = Step2State.MISMATCH_ERROR
                                            errorMessage = "Mã PIN không khớp. Vui lòng thử lại"
                                            delay(2000)
                                            // Reset
                                            firstPinValue = ""
                                            secondPinValue = ""
                                            errorMessage = null
                                            step2State = Step2State.ENTER_NEW_PIN
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    },
                    enabled = when (step2State) {
                        Step2State.ENTER_NEW_PIN -> firstPinValue.length == pinLength
                        Step2State.CONFIRM_NEW_PIN -> secondPinValue.length == pinLength
                        else -> false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        disabledContainerColor = Color(0xFFD0D5DD)
                    )
                ) {
                    if (step2State == Step2State.CALLING_API) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = when (step2State) {
                                Step2State.ENTER_NEW_PIN -> "Tiếp tục"
                                Step2State.CONFIRM_NEW_PIN -> "Xác nhận"
                                else -> "Đang xử lý..."
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Error Dialog
    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            errorCode = apiErrorCode,
            onRetry = {
                showErrorDialog = false
                errorDialogMessage = ""
                apiErrorCode = null
                // Reset to confirm state to retry
                secondPinValue = ""
                step2State = Step2State.CONFIRM_NEW_PIN
            },
            onCancel = {
                showErrorDialog = false
                onCancel()
            }
        )
    }
}

@Composable
private fun CircularCountdownTimer(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / 60,
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
    val currentStep = 2
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
private fun PinDotsDisplay(
    pinLength: Int,
    hasError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(6) { index ->
            PinDot(
                isFilled = index < pinLength,
                isActive = index == pinLength,
                hasError = hasError
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isActive: Boolean = false,
    hasError: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dot_scale"
    )

    val offsetX by animateFloatAsState(
        targetValue = if (hasError) 0f else 0f,
        animationSpec = if (hasError) {
            spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            tween(0)
        },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(24.dp)
            .scale(scale)
            .offset(x = offsetX.dp)
            .clip(CircleShape)
            .background(
                if (isFilled) {
                    if (hasError) Color(0xFFEF4444) else Color(0xFF3B82F6)
                } else Color.White
            )
            .border(
                width = 2.dp,
                color = if (isFilled) {
                    if (hasError) Color(0xFFEF4444) else Color(0xFF3B82F6)
                } else if (isActive) Color(0xFF60A5FA)
                else Color(0xFFD1D5DB),
                shape = CircleShape
            )
    )
}

@Composable
private fun NumericKeyboard(
    enabled: Boolean = true,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KeyboardRow(
            keys = listOf("1", "2", "3"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("4", "5", "6"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("7", "8", "9"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeyboardButton(
                text = "C",
                enabled = enabled,
                onClick = onClearClick,
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )

            KeyboardButton(
                text = "0",
                enabled = enabled,
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f)
            )

            KeyboardButton(
                text = "⌫",
                enabled = enabled,
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    keys: List<String>,
    enabled: Boolean = true,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        keys.forEach { key ->
            KeyboardButton(
                text = key,
                enabled = enabled,
                onClick = { onKeyClick(key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSpecialKey: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_press"
    )

    Surface(
        onClick = {
            if (enabled) {
                isPressed = true
                onClick()
            }
        },
        modifier = modifier
            .height(64.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            if (isSpecialKey) Color(0xFFF2F4F7) else Color(0xFFF9FAFB)
        } else {
            Color(0xFFE5E7EB)
        },
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFD0D5DD)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = if (isSpecialKey) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) {
                    if (isSpecialKey) Color(0xFF667085) else Color(0xFF101828)
                } else {
                    Color(0xFF3B82F6)
                }
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}