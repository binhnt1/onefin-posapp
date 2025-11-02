package com.onefin.posapp.ui.payment.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.data.PaymentState

@Composable
fun ActionButtons(
    onCancel: () -> Unit,
    isPrinting: Boolean,
    paymentState: PaymentState,
    onClose: (() -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            paymentState == PaymentState.SUCCESS && onPrint != null -> {
                // Nút In hóa đơn - màu xanh dương
                Button(
                    onClick = onPrint,
                    enabled = !isPrinting,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE5E7EB),
                        disabledContentColor = Color(0xFF9CA3AF)
                    ),
                    // ✨ KHÔNG SHADOW
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isPrinting) "Đang in..." else "In hóa đơn",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Nút Đóng - màu xanh lá
                Button(
                    onClick = { onClose?.invoke() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    // ✨ KHÔNG SHADOW
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "Đóng",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            paymentState == PaymentState.SUCCESS && onClose != null -> {
                // Chỉ có nút Đóng - toàn màn hình
                Button(
                    onClick = { onClose.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    // ✨ KHÔNG SHADOW
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "Đóng",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> {
                // ✨ NÚT HỦY MÀU VÀNG NHẠT NỔI BẬT
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEF3C7), // Vàng rất nhạt
                        contentColor = Color(0xFFD97706) // Text màu vàng đậm
                    ),
                    // ✨ KHÔNG SHADOW
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "Hủy giao dịch",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold // Đậm hơn để nổi bật
                    )
                }
            }
        }
    }
}