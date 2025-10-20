package com.onefin.posapp.ui.payment.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PinIndicator(
    pinLength: Int = 6,
    currentLength: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pinLength) { index ->
            PinDot(
                isFilled = index < currentLength,
                isActive = index == currentLength
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isActive: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pinDotScale"
    )

    val color = when {
        isFilled -> Color(0xFF10B981) // Green
        isActive -> Color(0xFF3B82F6) // Blue
        else -> Color(0xFFE5E7EB) // Gray
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .background(
                color = color,
                shape = CircleShape
            )
    )
}