package com.onefin.posapp.ui.payment.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentState

@Composable
fun ActionButtons(
    paymentState: PaymentState,
    onCancel: () -> Unit,
    isPrinting: Boolean = false,
    onPrint: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (paymentState) {
            PaymentState.SUCCESS -> {
                // 🔥 Sau khi ký xong: hiện 2 nút [In] [Đóng]

                // Nút IN
                if (onPrint != null) {
                    Button(
                        onClick = onPrint,
                        enabled = !isPrinting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            disabledContainerColor = Color(0xFF16A34A).copy(alpha = 0.6f)
                        )
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.button_printing), // ⭐ SONG NGỮ
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                stringResource(R.string.button_print_receipt), // ⭐ SONG NGỮ
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Nút ĐÓNG
                if (onClose != null) {
                    OutlinedButton(
                        onClick = onClose,
                        enabled = !isPrinting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
                            width = 2.dp,
                            brush = SolidColor(Color.White)
                        )
                    ) {
                        Text(
                            stringResource(R.string.button_close), // ⭐ SONG NGỮ
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            PaymentState.ERROR -> {
                // Khi lỗi: chỉ có nút Đóng
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    )
                ) {
                    Text(
                        stringResource(R.string.button_close), // ⭐ SONG NGỮ
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            else -> {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                    )
                ) {
                    Text(
                        stringResource(R.string.button_cancel_transaction), // ⭐ SONG NGỮ
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}