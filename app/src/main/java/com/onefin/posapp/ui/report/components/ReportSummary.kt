package com.onefin.posapp.ui.report.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.R

@Composable
fun ReportSummary(
    totalTransactions: Int,
    totalAmount: Long
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.report_total_transactions, totalTransactions),
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = context.getString(
                R.string.report_total_amount,
                UtilHelper.formatCurrency(totalAmount.toString())
            ),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
        )
    }
}