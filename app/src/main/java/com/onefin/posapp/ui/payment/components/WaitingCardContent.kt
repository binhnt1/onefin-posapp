package com.onefin.posapp.ui.payment.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.DeviceType

@Composable
fun WaitingCardContent(
    statusMessage: String,
    deviceType: DeviceType,
    timeRemaining: Int? = null // ⭐ Thêm parameter này
) {
    val scale by rememberInfiniteTransition(label = "scale").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val icon = when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> Icons.Default.CreditCard
            DeviceType.ANDROID_PHONE -> Icons.Default.Nfc
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .background(
                    color = Color(0xFF2196F3).copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusMessage,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ⭐ Instruction text - SONG NGỮ
        val instructionText = when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> stringResource(R.string.support_payment_types_pos)
            DeviceType.ANDROID_PHONE -> stringResource(R.string.support_payment_types_phone)
        }

        Text(
            text = instructionText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )

        // ⭐ Countdown timer + Progress bar
        if (timeRemaining != null) {
            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE0E0E0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = timeRemaining / 60f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (timeRemaining <= 10) Color(0xFFEF4444)
                            else Color(0xFF3B82F6)
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Countdown text - SONG NGỮ
            Text(
                text = stringResource(R.string.auto_close_after_seconds, timeRemaining),
                fontSize = 12.sp,
                color = if (timeRemaining <= 10) Color(0xFFEF4444) else Color(0xFF999999),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}