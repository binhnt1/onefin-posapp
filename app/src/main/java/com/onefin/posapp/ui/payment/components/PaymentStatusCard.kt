package com.onefin.posapp.ui.payment.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.payment.DeviceType
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun PaymentStatusCard(
    statusMessage: String,
    paymentState: PaymentState,
    modifier: Modifier = Modifier,
    currentRequestSale: RequestSale?,
    deviceType: DeviceType, // 🔥 NEW: Device type parameter
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = paymentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "PaymentStateTransition"
            ) { state ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        PaymentState.INITIALIZING,
                        PaymentState.WAITING_CARD -> {
                            WaitingCardContent(
                                statusMessage = statusMessage,
                                deviceType = deviceType // 🔥 Pass device type
                            )
                        }
                        PaymentState.PROCESSING,
                        PaymentState.CARD_DETECTED -> {
                            CardDetectedContent(
                                statusMessage = statusMessage,
                                currentRequestSale = currentRequestSale,
                                isProcessing = paymentState == PaymentState.PROCESSING
                            )
                        }

                        PaymentState.ENTERING_PIN -> {
                            EnteringPinContent(statusMessage)
                        }

                        PaymentState.ERROR -> {
                            ErrorContent(statusMessage)
                        }

                        PaymentState.WAITING_SIGNATURE,
                        PaymentState.SUCCESS -> {
                            SuccessContent(
                                statusMessage = statusMessage,
                                currentRequestSale = currentRequestSale,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SuccessContent(
    statusMessage: String,
    currentRequestSale: RequestSale?
) {
    // ✅ Countdown state
    var countdown by remember { mutableIntStateOf(3) }

    // ✅ Countdown effect
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    val scale by rememberInfiniteTransition(label = "scale").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .background(
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusMessage,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ✅ Countdown display
        Text(
            text = "Tự động đóng sau $countdown giây",
            fontSize = 14.sp,
            color = Color(0xFF999999),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666),
            text = UtilHelper.maskCardNumber(currentRequestSale?.data?.card?.clearPan),
        )

        currentRequestSale?.let { req ->
            Spacer(modifier = Modifier.height(24.dp))
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
                    InfoRow("Request ID", req.requestId)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (req.data.card.mode != null) {
                        InfoRow("Mode", req.data.card.mode)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    InfoRow("Card", req.data.card.type ?: "")
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Amount", UtilHelper.formatCurrency(req.data.payment.transAmount, "đ"))
                }
            }
        }
    }
}