package com.onefin.posapp.ui.transaction.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.Transaction
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun TransactionList(
    transactions: List<Transaction>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoadingMore: Boolean
) {
    // Group transactions by date
    val groupedTransactions = transactions.groupBy { it.getFormattedDate() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .background(Color.White)
            .fillMaxSize()
    ) {
        groupedTransactions.forEach { (date, transactionsForDate) ->
            // Date header - Màu #F0F9FF
            item(key = "header_$date") {
                Text(
                    text = date,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F9FF))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }

            // Transactions for this date
            itemsIndexed(
                items = transactionsForDate,
                key = { index, transaction -> "${date}_${transaction.transactionId}_$index" }
            ) { index, transaction ->
                // Add spacing before first item
                if (index == 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TransactionItem(
                    transaction = transaction,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Add spacing between items
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Loading more indicator
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}