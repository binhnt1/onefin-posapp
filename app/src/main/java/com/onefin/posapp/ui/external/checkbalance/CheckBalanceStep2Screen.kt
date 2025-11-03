package com.onefin.posapp.ui.external.checkbalance

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.data.MemberResultData
import com.onefin.posapp.core.utils.UtilHelper
import kotlinx.coroutines.delay

@Composable
fun CheckBalanceStep2Screen(
    memberResult: MemberResultData,
    onComplete: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(10) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        if (countdown == 0) {
            onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF6FF), Color.White)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kiểm tra số dư",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

        StepProgressIndicator()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = rippleAlpha * 0.3f),
                        radius = size.minDimension / 2 * rippleScale
                    )
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(50.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.5f)
                            lineTo(size.width * 0.4f, size.height * 0.7f)
                            lineTo(size.width * 0.8f, size.height * 0.3f)
                        }
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Kiểm tra thành công!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SuccessItem("Xác thực thẻ thành công")
                    SuccessItem("Mã PIN đã được xác nhận")
                    SuccessItem("Số dư đã được cập nhật")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Số dư hiện tại",
                        fontSize = 14.sp,
                        color = Color(0xFF667085)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = UtilHelper.formatCurrency(memberResult.balance?.toLongOrNull() ?: 0),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "VNĐ", fontSize = 14.sp, color = Color(0xFF667085))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CountdownCircle(
                timeRemaining = countdown,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tự động đóng sau ${countdown}s",
                fontSize = 14.sp,
                color = Color(0xFF667085),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text(
                    text = "Hoàn thành",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SuccessItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = Color(0xFF10B981), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = Color(0xFF374151),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CountdownCircle(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / 10,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f

            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = radius - strokeWidth / 2,
                style = Stroke(width = strokeWidth)
            )

            val sweepAngle = -360f * progress
            drawArc(
                color = Color(0xFF3B82F6),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter - strokeWidth, diameter - strokeWidth)
            )
        }

        Text(
            text = "$timeRemaining",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3B82F6)
        )
    }
}

@Composable
private fun StepProgressIndicator() {
    val currentStep = 2
    val steps = listOf("Xác thực", "Hoàn tất")

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
            val isCompleted = stepNumber <= currentStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isCompleted) Color(0xFF10B981) else Color.White,
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isCompleted) Color(0xFF10B981) else Color(0xFFD0D5DD),
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
                            color = Color(0xFF667085)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (isCompleted) Color(0xFF10B981) else Color(0xFF667085),
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
                            color = if (stepNumber < currentStep) Color(0xFF10B981) else Color(0xFFD0D5DD)
                        )
                )
            }
        }
    }

    HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))
}