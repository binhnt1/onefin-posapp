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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.UtilHelper

@Composable
fun CardDetectedContent(
    statusMessage: String,
    currentRequestSale: RequestSale?,
    isProcessing: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = Color(0xFF1976D2),
                strokeWidth = 6.dp
            )
        } else {
            // CARD_DETECTED state - hiển thị checkmark với animation
            val scale by rememberInfiniteTransition(label = "scale").animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scaleAnim"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusMessage,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isProcessing) Color(0xFF1976D2) else Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666),
            text = UtilHelper.maskCardNumber(currentRequestSale?.data?.card?.clearPan),
        )

        currentRequestSale?.let { req ->
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    InfoRow("Request Id", req.requestId)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (req.data.card.mode != null) {
                        InfoRow("Mode", req.data.card.mode)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    InfoRow("Card", req.data.card.type ?: "")
                }
            }
        }
    }
}