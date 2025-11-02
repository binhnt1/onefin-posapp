package com.onefin.posapp.ui.payment.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.UtilHelper
import kotlinx.coroutines.launch

@Composable
private fun AnimatedDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_blink")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .alpha(alpha)
            .background(
                color = color,
                shape = CircleShape
            )
    )
}

@Composable
fun ModernBillCard(
    amount: Long,
    isMemberCard: Boolean,
    billNumber: String?,
    referenceId: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo TO HƠN: 64dp (thay vì 56dp cũ)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE5E7EB),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier.height(28.dp), // TO HƠN: 28dp thay vì 24dp
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // SỐ TIỀN TO HƠN: 32sp (thay vì 24sp cũ)
                    Text(
                        text = UtilHelper.formatCurrency(amount, "đ"),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (billNumber != null) {
                            Text(
                                text = "Bill: $billNumber",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        if (referenceId != null) {
                            Text(
                                text = "• Ref: $referenceId",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcTapAnimation(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_tap")

    // Card offset animation
    val cardOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "card_offset"
    )

    // Scanning line animation
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )

    // Wave animation
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // NFC Phone base
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .background(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(size * 0.1f)
                )
                .border(
                    width = 3.dp,
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(size * 0.1f)
                )
        ) {
            // NFC icon
            Text(
                text = "📱",
                fontSize = (size.value * 0.25f).sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Card animation
        Box(
            modifier = Modifier
                .size(size * 0.5f, size * 0.3f)
                .offset(y = cardOffset.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF60A5FA)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            // Card chip
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp, 20.dp)
                    .background(
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }

        // Scanning line
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.6f)
        ) {
            drawLine(
                color = Color(0xFF60A5FA),
                start = androidx.compose.ui.geometry.Offset(this.size.width * 0.2f, this.size.height * 0.5f + scanLineOffset),
                end = androidx.compose.ui.geometry.Offset(this.size.width * 0.8f, this.size.height * 0.5f + scanLineOffset),
                strokeWidth = 3f
            )
        }

        // Wave rings
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(size * (0.7f + index * 0.2f))
                    .alpha((1f - index * 0.3f) * ((waveOffset % 100) / 100f))
                    .border(
                        width = 2.dp,
                        color = Color(0xFF3B82F6),
                        shape = CircleShape
                    )
            )
        }
    }
}

// ANIMATION KHI PHÁT HIỆN THẺ
@Composable
private fun CardDetectedAnimation(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Scale up animation
        launch {
            scale.animateTo(
                targetValue = 1.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(300)
            )
        }
        // Fade in
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(500)
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Pulse rings
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(size * (0.6f + index * 0.2f) * scale.value)
                    .alpha(alpha.value * (1f - index * 0.3f))
                    .background(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        // Center icon with scale
        Box(
            modifier = Modifier
                .size(size * 0.5f * scale.value)
                .background(
                    color = Color(0xFF10B981),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💳",
                fontSize = (size.value * 0.25f * scale.value).sp
            )
        }
    }
}

@Composable
fun PulseStatusCard(
    paymentState: PaymentState,
    statusMessage: String,
    isMemberCard: Boolean,
    timeRemaining: Int? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (paymentState) {
                    PaymentState.CARD_DETECTED -> {
                        CardDetectedAnimation(size = 140.dp)
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "✓ Đã phát hiện thẻ",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang xử lý...",
                            fontSize = 16.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center
                        )
                    }
                    PaymentState.PROCESSING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(120.dp), // To hơn
                            color = Color(0xFF60A5FA),
                            strokeWidth = 8.dp // Dày hơn
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = statusMessage,
                            fontSize = 24.sp, // Font to hơn
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                            textAlign = TextAlign.Center
                        )
                    }
                    PaymentState.SUCCESS -> {
                        SuccessAnimation(size = 140.dp) // To hơn
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = statusMessage,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            textAlign = TextAlign.Center
                        )

                        // COUNTDOWN TỰ ĐỘNG ĐÓNG
                        if (timeRemaining != null && timeRemaining > 0) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFF0FDF4),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⏱",
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Tự động đóng sau ${timeRemaining}s",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // ANIMATION CHỜ THẺ - TO GẤP ĐÔI
                        NfcTapAnimation(size = 140.dp) // Từ 120dp lên 240dp

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = statusMessage,
                            fontSize = 20.sp, // Font to hơn
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                            textAlign = TextAlign.Center
                        )

                        if (timeRemaining != null && timeRemaining > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFEF3C7),  // Nền vàng nhạt
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⏱",
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "Còn lại ${timeRemaining}s",  // 60s, 59s, 58s...
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD97706)  // Màu vàng cam
                                    )
                                }
                            }
                        }

                        // HIỂN THỊ CARD TYPE ICONS CHO BANK CARD
                        if (!isMemberCard) {
                            Spacer(modifier = Modifier.height(32.dp))
                            CardTypeIcons()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                StepIndicators(paymentState = paymentState)
            }

            // Badge góc phải trên
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(
                        color = if (isMemberCard) Color(0xFFFEF3C7) else Color(0xFFDCEEFB),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedDot(
                        color = if (isMemberCard) Color(0xFFD97706) else Color(0xFF2563EB)
                    )
                    Text(
                        text = if (isMemberCard) "Thẻ thành viên" else "Thẻ ngân hàng",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isMemberCard) Color(0xFFD97706) else Color(0xFF2563EB)
                    )
                }
            }
        }
    }
}

@Composable
private fun RotatingAnimation(
    icon: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Rotating circles
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(size * (0.6f + index * 0.2f))
                    .graphicsLayer(rotationZ = rotation + index * 120f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = Color(0xFF3B82F6).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        // Center icon
        Box(
            modifier = Modifier
                .size(size * 0.4f)
                .background(
                    color = Color(0xFF3B82F6),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = (size.value * 0.2f).sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SuccessAnimation(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Success circle
        Box(
            modifier = Modifier
                .size(size * 0.8f * scale.value)
                .background(
                    color = Color(0xFF10B981),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = (size.value * 0.4f).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StepIndicators(
    paymentState: PaymentState,
    modifier: Modifier = Modifier
) {
    val currentStep = when (paymentState) {
        PaymentState.WAITING_CARD, PaymentState.INITIALIZING -> 0
        PaymentState.CARD_DETECTED, PaymentState.PROCESSING -> 1
        PaymentState.SUCCESS, PaymentState.WAITING_SIGNATURE -> 2
        else -> 0
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (index <= currentStep) Color(0xFF60A5FA) else Color(0xFFE5E7EB),
                        shape = CircleShape
                    )
            )

            if (index < 2) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            color = if (index < currentStep) Color(0xFF60A5FA) else Color(0xFFE5E7EB)
                        )
                )
            }
        }
    }
}

@Composable
fun CardInfoCard(
    requestSale: RequestSale,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Card icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = Color(0xFF10B981),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💳",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Card type
                val cardType = requestSale.data.card.type?.uppercase() ?: "CARD"
                Text(
                    text = cardType,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Card number - DÙNG UtilHelper.formatCardNumber
                val cardNumber = requestSale.data.card.clearPan
                Text(
                    text = UtilHelper.formatCardNumber(cardNumber),  // ✅ GIỮ NGUYÊN
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    letterSpacing = 1.sp
                )

                val holderName = requestSale.data.card.holderName
                val expiryDate = requestSale.data.card.expiryDate
                if ((holderName != null && holderName.isNotBlank()) || expiryDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Holder name
                        if (holderName != null && holderName.isNotBlank()) {
                            Text(
                                text = holderName,
                                fontSize = 12.sp,
                                color = Color(0xFF374151)
                            )
                        }

                        // Separator
                        if (holderName != null && holderName.isNotBlank() && expiryDate.isNotBlank()) {
                            Text(
                                text = "-",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }

                        // Expiry date - DÙNG UtilHelper.formatExpiryDate
                        if (expiryDate.isNotBlank()) {
                            Text(
                                text = "HSD: ${UtilHelper.formatExpiryDate(expiryDate)}",  // ✅ GIỮ NGUYÊN
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun createWavePath(size: androidx.compose.ui.geometry.Size, offset: Float, baseY: Float): androidx.compose.ui.graphics.Path {
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, baseY + offset)

        val waveLength = size.width / 3
        for (i in 0..3) {
            val x = i * waveLength
            cubicTo(
                x + waveLength * 0.25f, baseY + offset - 10f,
                x + waveLength * 0.75f, baseY + offset + 10f,
                x + waveLength, baseY + offset
            )
        }

        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
}