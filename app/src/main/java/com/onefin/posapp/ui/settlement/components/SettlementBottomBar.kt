package com.onefin.posapp.ui.settlement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.R

@Composable
fun SettlementBottomBar(
    totalAmount: Long,
    isSettling: Boolean,
    onSettleClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Divider
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFFE5E7EB)
        )

        // Bottom bar content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Label and total amount
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = context.getString(R.string.menu_settlement),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = UtilHelper.formatCurrency(totalAmount.toString(), "đ"),
                    fontSize = 22.sp,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold
                )
            }

            // Right side: Settle button
            Button(
                onClick = onSettleClick,
                enabled = !isSettling && totalAmount > 0,
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 120.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFD1D5DB),
                    disabledContentColor = Color(0xFF6B7280)
                )
            ) {
                Text(
                    text = context.getString(R.string.menu_settlement),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}