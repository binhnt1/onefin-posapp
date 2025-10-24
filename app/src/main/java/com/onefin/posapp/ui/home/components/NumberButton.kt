package com.onefin.posapp.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val clearText = stringResource(R.string.numpad_clear)
    val backspaceText = stringResource(R.string.numpad_backspace)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isSpecialKey = text == clearText || text == backspaceText
    val showLongPressHint = text == backspaceText && onLongClick != null

    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isLongPressed -> 0.90f
            isPressed -> 0.95f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val isP2 = remember {
        android.os.Build.MODEL.lowercase().contains("p2")
    }

    val cornerRadius = if (isP2) 6.dp else 12.dp
    val fontSize = if (isP2) 18.sp else 24.sp
    val aspectRatio = if (isP2) 2.2f else 1.8f

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .scale(scale)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                isPressed = true
                                delay(100)
                                isPressed = false
                            }
                            onClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                isLongPressed = true
                                onLongClick()
                                delay(200)
                                isLongPressed = false
                            }
                        }
                    )
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClick()
                            }
                        )
                    }
                }
            )
    ) {
        Surface(
            shape = RoundedCornerShape(cornerRadius),
            color = when {
                isLongPressed -> Color(0xFFEF4444)
                isSpecialKey -> Color(0xFFF2F4F7)
                else -> Color(0xFFF9FAFB)
            },
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isLongPressed) Color(0xFFDC2626) else Color(0xFFD0D5DD)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (showLongPressHint) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = text,
                            modifier = Modifier.size(24.dp),
                            tint = if (isLongPressed) Color.White else Color(0xFF667085)
                        )
                        Text(
                            text = "Giữ để xóa hết",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isLongPressed) Color.White else Color(0xFF667085)
                        )
                    }
                } else {
                    Text(
                        text = text,
                        fontSize = fontSize,
                        fontWeight = if (isSpecialKey) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSpecialKey) Color(0xFF667085) else Color(0xFF101828)
                    )
                }
            }
        }
    }
}