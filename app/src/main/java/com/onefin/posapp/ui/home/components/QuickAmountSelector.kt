package com.onefin.posapp.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R

@Composable
fun QuickAmountSelector(
    onAmountSelected: (String) -> Unit
) {
    val amounts = listOf(100_000L, 200_000L, 500_000L, 1_000_000L)
    val thousandSuffix = stringResource(R.string.amount_thousand_suffix)
    val millionSuffix = stringResource(R.string.amount_million_suffix)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        amounts.forEach { amount ->
            OutlinedButton(
                onClick = { onAmountSelected(amount.toString()) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF475467)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp)
            ) {
                val displayText = when {
                    amount >= 1_000_000 -> "${amount / 1_000_000}$millionSuffix"
                    else -> "${amount / 1_000}$thousandSuffix"
                }
                Text(
                    text = displayText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}