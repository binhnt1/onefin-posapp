package com.onefin.posapp.ui.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.PaymentHistory
import com.onefin.posapp.ui.transaction.components.TransactionItem
import com.onefin.posapp.R

@Composable
fun PaymentHistoryDetailContent(paymentHistory: PaymentHistory) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: Shift name
        item {
            Text(
                text = "${paymentHistory.shift} - ${paymentHistory.bank}",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16A34A),
                textAlign = TextAlign.Center
            )
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Thực nhận
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.payment_history_received),
                            fontSize = 16.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = paymentHistory.getFormattedActuallyAmount(),
                            fontSize = 32.sp,
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    // Giao dịch
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.payment_history_transactions),
                            fontSize = 16.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = paymentHistory.transactionCount.toString().padStart(2, '0'),
                            fontSize = 32.sp,
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Transaction list
        items(paymentHistory.transactions) { transaction ->
            TransactionItem(
                transaction = transaction,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Empty state if no transactions
        if (paymentHistory.transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.payment_history_empty),
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}