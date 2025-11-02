package com.onefin.posapp.ui.transaction.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.utils.UtilHelper
import java.text.DecimalFormat


@Composable
fun TransactionDetailContent(
    transaction: Transaction,
    notes: String = "",
    onNotesChange: ((String) -> Unit)? = null,
    showNotes: Boolean = true,
    account: Account? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currencyFormat = DecimalFormat("#,###")
    val statusInfo = transaction.getStatusInfo(context)
    val headerColor = Color(0xFFDCFCE7)

    // Animation for pulsing dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section - Gradient with Amount and Card Number
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF10B981),
                            Color(0xFF059669)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.label_total_amount),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${currencyFormat.format(transaction.totalTransAmt)}₫",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Card Number Section
                if (transaction.formType == 1 && transaction.accountNumber.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 32.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = transaction.getPaymentIconRes()),
                                contentDescription = transaction.source,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = UtilHelper.formatCardNumber(transaction.accountNumber),
                            fontSize = 18.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Status Badge - At top right edge with pulsing dot
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 0.dp, y = (-6).dp),
                shape = RoundedCornerShape(12.dp),
                color = statusInfo.backgroundColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(alpha)
                            .background(
                                color = statusInfo.textColor,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = statusInfo.text,
                        fontSize = 12.sp,
                        color = statusInfo.textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Payment Info Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header with background
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = headerColor,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .padding(16.dp, 8.dp)
                ) {
                    Text(
                        text = "💳",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.label_payment_info),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    InfoRowHorizontal(
                        label = stringResource(id = R.string.label_transaction_id),
                        value = transaction.transactionId
                    )

                    if (transaction.invoiceNumber.isNotEmpty()) {
                        InfoRowHorizontal(
                            label = stringResource(id = R.string.label_invoice_number),
                            value = transaction.invoiceNumber
                        )
                    }

                    if (transaction.formType == 1 && transaction.approvedCode.isNotEmpty()) {
                        InfoRowHorizontal(
                            label = stringResource(id = R.string.label_approved_code),
                            value = transaction.approvedCode
                        )
                    }
                }
            }
        }

        // Transaction Details Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header with background
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = headerColor,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📋",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.label_transaction_details),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    InfoRowHorizontal(
                        label = stringResource(id = R.string.label_transaction_type),
                        value = transaction.getFormTypeText(context)
                    )

                    val bankTitle = if (transaction.formType == 2) {
                        stringResource(id = R.string.payment_method_qr)
                    } else {
                        transaction.source
                    }

                    InfoRowHorizontal(
                        label = stringResource(id = R.string.label_bank),
                        value = bankTitle
                    )

                    // Card number row removed from here since it's now in header

                    InfoRowHorizontal(
                        label = stringResource(id = R.string.label_batch_number),
                        value = transaction.batchNumber
                    )

                    if (transaction.refno.isNotEmpty()) {
                        InfoRowHorizontal(
                            label = stringResource(id = R.string.label_refno),
                            value = transaction.refno
                        )
                    }

                    if (account?.terminal?.tid?.isNotEmpty() == true) {
                        InfoRowHorizontal(
                            label = "TID",
                            value = account.terminal.tid
                        )
                    }

                    if (account?.terminal?.mid?.isNotEmpty() == true) {
                        InfoRowHorizontal(
                            label = "MID",
                            value = account.terminal.mid
                        )
                    }

                    InfoRowHorizontal(
                        label = stringResource(id = R.string.label_time),
                        value = transaction.getFormattedTransactionDateTime()
                    )
                }
            }
        }

        // Notes Card - Only show if showNotes = true and callback exists
        if (showNotes && onNotesChange != null && transaction.showButtons()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header with background
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = headerColor,
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "📝",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.label_enter_notes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                    }

                    // Content
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF16A34A),
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            placeholder = {
                                Text(
                                    text = "Nhập ghi chú...",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRowHorizontal(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color(0xFF111827),
            fontWeight = FontWeight.SemiBold
        )
    }
}