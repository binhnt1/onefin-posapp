package com.onefin.posapp.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinInputSheet(
    title: String,
    pinLength: Int,
    onComplete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initialValue: String = "",
    showCancelButton: Boolean = true,
    showConfirmButton: Boolean = true,
    confirmButtonText: String = "Xác nhận",
    cancelButtonText: String = "Hủy",
    isLoading: Boolean = false,
    autoSubmitOnComplete: Boolean = false,
    tipText: String? = null
) {
    var pinValue by remember { mutableStateOf(initialValue) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Auto submit when complete
    LaunchedEffect(pinValue) {
        if (autoSubmitOnComplete && pinValue.length == pinLength && !isLoading) {
            onComplete(pinValue)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFFD1D5DB)
                ) {}
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF101828)
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF667085),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // PIN Dots
            PinDotsDisplay(
                pinLength = pinValue.length,
                maxLength = pinLength
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Numeric Keyboard
            NumericKeyboard(
                enabled = !isLoading,
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

            if (tipText != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tipText,
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showCancelButton) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF667085)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFD0D5DD))
                    ) {
                        Text(
                            text = cancelButtonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (showConfirmButton) {
                    Button(
                        onClick = { onComplete(pinValue) },
                        enabled = pinValue.length == pinLength && !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            disabledContainerColor = Color(0xFFD0D5DD)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = confirmButtonText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NumericKeyboard(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showClearButton: Boolean = true,
    buttonHeight: Dp = 56.dp,
    buttonSpacing: Dp = 12.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(buttonSpacing)
    ) {
        KeyboardRow(
            keys = listOf("1", "2", "3"),
            enabled = enabled,
            buttonHeight = buttonHeight,
            buttonSpacing = buttonSpacing,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("4", "5", "6"),
            enabled = enabled,
            buttonHeight = buttonHeight,
            buttonSpacing = buttonSpacing,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("7", "8", "9"),
            enabled = enabled,
            buttonHeight = buttonHeight,
            buttonSpacing = buttonSpacing,
            onKeyClick = onNumberClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
        ) {
            if (showClearButton) {
                KeyboardButton(
                    text = "C",
                    enabled = enabled,
                    onClick = onClearClick,
                    modifier = Modifier.weight(1f),
                    buttonHeight = buttonHeight,
                    isSpecialKey = true
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            KeyboardButton(
                text = "0",
                enabled = enabled,
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f),
                buttonHeight = buttonHeight
            )

            KeyboardButton(
                text = "⌫",
                enabled = enabled,
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                buttonHeight = buttonHeight,
                isSpecialKey = true
            )
        }
    }
}

@Composable
fun PinDotsDisplay(
    pinLength: Int,
    maxLength: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 24.dp,
    spacing: Dp = 8.dp,
    filledColor: Color = Color(0xFF3B82F6),
    emptyColor: Color = Color.White,
    borderColor: Color = Color(0xFFD1D5DB),
    activeBorderColor: Color = Color(0xFF60A5FA)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            PinDot(
                isFilled = index < pinLength,
                isActive = index == pinLength,
                dotSize = dotSize,
                spacing = spacing,
                filledColor = filledColor,
                emptyColor = emptyColor,
                borderColor = borderColor,
                activeBorderColor = activeBorderColor
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isActive: Boolean = false,
    dotSize: Dp = 24.dp,
    spacing: Dp = 8.dp,
    filledColor: Color = Color(0xFF3B82F6),
    emptyColor: Color = Color.White,
    borderColor: Color = Color(0xFFD1D5DB),
    activeBorderColor: Color = Color(0xFF60A5FA)
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
            .padding(horizontal = spacing)
            .size(dotSize)
            .scale(scale)
            .background(
                color = if (isFilled) filledColor else emptyColor,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = if (isFilled) filledColor
                else if (isActive) activeBorderColor
                else borderColor,
                shape = CircleShape
            )
    )
}

@Composable
private fun KeyboardRow(
    keys: List<String>,
    enabled: Boolean = true,
    buttonHeight: Dp = 56.dp,
    buttonSpacing: Dp = 12.dp,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
    ) {
        keys.forEach { key ->
            KeyboardButton(
                text = key,
                enabled = enabled,
                onClick = { onKeyClick(key) },
                modifier = Modifier.weight(1f),
                buttonHeight = buttonHeight
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
    buttonHeight: Dp = 56.dp,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
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
            .height(buttonHeight)
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
                fontSize = if (isSpecialKey) 22.sp else 24.sp,
                fontWeight = if (isSpecialKey) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) {
                    if (isSpecialKey) Color(0xFF667085) else Color(0xFF101828)
                } else {
                    Color(0xFF9CA3AF)
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