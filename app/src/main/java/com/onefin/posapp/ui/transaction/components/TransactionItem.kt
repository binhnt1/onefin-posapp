package com.onefin.posapp.ui.transaction.components

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.ui.transaction.TransactionDetailActivity

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusInfo = transaction.getStatusInfo()

    val onDetailClick = {
        val intent = Intent(context, TransactionDetailActivity::class.java)
        intent.putExtra("TRANSACTION_ID", transaction.transactionId)
        context.startActivity(intent)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onDetailClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.getFormattedTime(),
                            fontSize = 15.sp,
                            color = Color(0xFF374151),
                            fontWeight = FontWeight.Normal
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusInfo.backgroundColor
                        ) {
                            Text(
                                text = statusInfo.text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 13.sp,
                                color = statusInfo.textColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = transaction.getFormattedAmount(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = transaction.getPaymentIconRes()
                        ),
                        contentDescription = transaction.source,
                        modifier = Modifier
                            .height(28.dp)
                            .width(42.dp)
                    )

                    Text(
                        text = transaction.getMaskedNumber(),
                        fontSize = 15.sp,
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(48.dp)
                    .height(32.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 10.dp,
                            topEnd = 0.dp,
                            bottomEnd = 12.dp,
                            bottomStart = 0.dp
                        )
                    )
                    .background(Color(0xFFF3F4F6))
                    .clickable { onDetailClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(id = R.string.content_desc_detail),
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}