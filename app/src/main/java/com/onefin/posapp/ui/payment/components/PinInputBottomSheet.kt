package com.onefin.posapp.ui.payment.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefin.posapp.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinInputBottomSheet(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit = {}
) {
    var pinValue by remember { mutableStateOf("") }
    val pinLength = 6

    // ⭐ Countdown timer
    var timeRemaining by remember { mutableIntStateOf(60) }

    // ⭐ Countdown effect
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        // Timeout - auto cancel
        if (timeRemaining == 0) {
            onCancel()
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) { },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                // Header with circular countdown timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(56.dp))

                    Text(
                        text = stringResource(R.string.pin_input_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    // ⭐ Circular Countdown Timer
                    CircularCountdownTimer(
                        timeRemaining = timeRemaining,
                        totalTime = 60,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.pin_input_description),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ⭐ PIN Display - Beautiful dots
                PinDotsDisplay(
                    pinLength = pinValue.length,
                    totalDots = pinLength
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Numeric Keyboard
                NumericKeyboard(
                    onNumberClick = { number ->
                        if (pinValue.length < pinLength) {
                            pinValue += number
                        }
                    },
                    onDeleteClick = {
                        if (pinValue.isNotEmpty()) {
                            pinValue = pinValue.dropLast(1)
                        }
                    },
                    onClearClick = {
                        pinValue = ""
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = {
                            pinValue = ""
                            onCancel()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1E3A8A)
                        )
                    ) {
                        Text(
                            stringResource(R.string.pin_input_cancel),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Confirm Button
                    Button(
                        onClick = {
                            if (pinValue.length == pinLength) {
                                onConfirm(pinValue)
                                pinValue = ""
                            }
                        },
                        enabled = pinValue.length == pinLength,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text(
                            stringResource(R.string.pin_input_confirm),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ⭐ NEW: Circular Countdown Timer Component
@Composable
fun CircularCountdownTimer(
    timeRemaining: Int,
    totalTime: Int,
    modifier: Modifier = Modifier
) {
    // Smooth progress animation
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / totalTime.toFloat(),
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearEasing
        ),
        label = "progress"
    )

    // Color based on time remaining
    val progressColor = when {
        timeRemaining <= 10 -> Color(0xFFEF4444) // Red
        timeRemaining <= 30 -> Color(0xFFF59E0B) // Orange
        else -> Color(0xFF3B82F6) // Blue
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f

            // Background circle (gray)
            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = radius - strokeWidth / 2,
                style = Stroke(width = strokeWidth)
            )

            // Progress arc (clockwise from top)
            val sweepAngle = -360f * progress // Negative for clockwise
            drawArc(
                color = progressColor,
                startAngle = -90f, // Start from top (12 o'clock)
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

        // Time text in center
        Text(
            text = "$timeRemaining",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = progressColor
        )
    }
}

@Composable
fun PinDotsDisplay(pinLength: Int, totalDots: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalDots) { index ->
            PinDot(
                isFilled = index < pinLength,
                isActive = index == pinLength
            )
        }
    }
}

@Composable
fun PinDot(
    isFilled: Boolean,
    isActive: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isFilled) Color(0xFF3B82F6)
                else Color.White
            )
            .border(
                width = 2.dp,
                color = if (isFilled) Color(0xFF3B82F6)
                else if (isActive) Color(0xFF60A5FA)
                else Color(0xFFD1D5DB),
                shape = CircleShape
            )
    )
}

@Composable
fun NumericKeyboard(
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
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("4", "5", "6"),
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("7", "8", "9"),
            onKeyClick = onNumberClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeyboardButton(
                text = "C",
                onClick = onClearClick,
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFEF4444)
            )

            KeyboardButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f)
            )

            KeyboardButton(
                text = "⌫",
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFF59E0B)
            )
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        keys.forEach { key ->
            KeyboardButton(
                text = key,
                onClick = { onKeyClick(key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF3B82F6)
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

    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier
            .height(64.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}