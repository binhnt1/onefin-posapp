package com.onefin.posapp.ui.transaction.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.utils.DateTimeFormatter
import com.onefin.posapp.core.utils.UtilHelper
import java.text.DecimalFormat


@Composable
fun TransactionDetailContent(
    transaction: Transaction,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val currencyFormat = DecimalFormat("#,###")
    val statusInfo = transaction.getStatusInfo()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.label_total_amount),
                        fontSize = 16.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${currencyFormat.format(transaction.totalTransAmt)}đ",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )

                    if (transaction.formType == 1 && transaction.accountNumber.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 40.dp)
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFE5E7EB),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(6.dp),
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
                                color = Color(0xFF111827),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.label_status),
                        fontSize = 15.sp,
                        color = Color(0xFF111827)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusInfo.backgroundColor
                    ) {
                        Text(
                            text = statusInfo.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 14.sp,
                            color = statusInfo.textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )

                if (transaction.invoiceNumber.isNotEmpty()) {
                    InfoRow(label = stringResource(id = R.string.label_refno), value = transaction.invoiceNumber)
                }
                if (transaction.formType == 1) {
                    InfoRow(label = stringResource(id = R.string.label_approved_code), value = transaction.remark)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )

                val bankTitle = if (transaction.formType == 2) {
                    stringResource(id = R.string.payment_method_qr)
                } else {
                    transaction.source
                }

                Text(
                    text = bankTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(6.dp))

                InfoRow(label = stringResource(id = R.string.label_batch_number), value = transaction.batchNumber)
                InfoRow(label = stringResource(id = R.string.label_transaction_id), value = transaction.transactionId)
                InfoRow(
                    label = stringResource(id = R.string.label_time),
                    value = DateTimeFormatter.formatCustomDateTime(transaction.transactionDate)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )
                if (transaction.showButtons()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.label_enter_notes),
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            modifier = Modifier
                                .fillMaxSize()
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF16A34A),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                    }
                }
            }
        }
    }
}