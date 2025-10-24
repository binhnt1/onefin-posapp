package com.onefin.posapp.ui.home.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.BuildConfig
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.home.QRCodeDisplayActivity
import com.onefin.posapp.ui.payment.PaymentCardActivity
import com.onefin.posapp.ui.payment.SimpleChipTestActivity
import com.onefin.posapp.ui.transaction.TransparentPaymentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountEntrySheet(
    onDismiss: () -> Unit,
    paymentHelper: PaymentHelper,
    storageService: StorageService
) {
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var loadingButtonType by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val formattedAmount = remember(amountText) {
        UtilHelper.formatCurrency(amountText, "đ")
    }

    val amountValue = remember(amountText) {
        amountText.replace(".", "").toLongOrNull() ?: 0L
    }

    val isValidAmount = amountValue > 0

    val isP2 = remember {
        android.os.Build.MODEL.lowercase().contains("p2")
    }

    val headerPaddingHorizontal = if (isP2) 8.dp else 16.dp
    val headerPaddingVertical = if (isP2) 6.dp else 12.dp
    val contentPadding = if (isP2) 8.dp else 16.dp
    val amountDisplayPaddingVertical = if (isP2) 12.dp else 24.dp
    val defaultSpacerHeight = if (isP2) 6.dp else 12.dp
    val bottomSpacerHeight = if (isP2) 8.dp else 16.dp

    ModalBottomSheet(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        containerColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = headerPaddingHorizontal, vertical = headerPaddingVertical),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_enter_amount),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101828)
                )

                IconButton(
                    onClick = { if (!isProcessing) onDismiss() },
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_close),
                        tint = Color(0xFF667085)
                    )
                }

            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formattedAmount.ifEmpty { stringResource(R.string.amount_placeholder) },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101828),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = amountDisplayPaddingVertical)
                )

                NumberPad(
                    onNumberClick = { number ->
                        if (!isProcessing) {
                            amountText = (amountText + number).take(12)
                        }
                    },
                    onBackspaceClick = {
                        if (!isProcessing && amountText.isNotEmpty()) {
                            amountText = amountText.dropLast(1)
                        }
                    },
                    onClearClick = {
                        if (!isProcessing) {
                            amountText = ""
                        }
                    }
                )

                Spacer(modifier = Modifier.height(defaultSpacerHeight))

                QuickAmountSelector(
                    onAmountSelected = { selectedAmount ->
                        if (!isProcessing) {
                            amountText = selectedAmount
                        }
                    }
                )

                Spacer(modifier = Modifier.height(defaultSpacerHeight))

                Column(
                    verticalArrangement = Arrangement.spacedBy(defaultSpacerHeight)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(defaultSpacerHeight)
                    ) {
                        PaymentOptionButton(
                            text = stringResource(R.string.payment_swipe_card),
                            icon = Icons.Default.CreditCard,
                            onClick = {
                                if (isValidAmount && !isProcessing) {
                                    isProcessing = true
                                    loadingButtonType = "CARD"
                                    val account = storageService.getAccount()
                                    if (account != null) {
                                        val paymentRequest = PaymentAppRequest(
                                            type = "card",
                                            action = PaymentAction.SALE.value,
                                            merchantRequestData = MerchantRequestData(
                                                amount = amountValue
                                            )
                                        )
                                        val sdkType = BuildConfig.SDK_TYPE
                                        val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
                                        if (sdkType == "onefin") {
                                            val intent = Intent(
                                                context,
                                                TransparentPaymentActivity::class.java
                                            ).apply {
                                                putExtra("REQUEST_DATA", paymentRequestData)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(
                                                context,
                                                PaymentCardActivity::class.java
                                            ).apply {
                                                putExtra("REQUEST_DATA", paymentRequestData)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                        onDismiss()
                                    } else {
                                        isProcessing = false
                                        loadingButtonType = null
                                    }
                                }
                            },
                            isPrimary = true,
                            enabled = isValidAmount && !isProcessing,
                            isLoading = loadingButtonType == "CARD",
                            modifier = Modifier.weight(1f)
                        )
                        PaymentOptionButton(
                            text = stringResource(R.string.payment_generate_qr),
                            icon = Icons.Default.QrCode,
                            onClick = {
                                if (isValidAmount && !isProcessing) {
                                    isProcessing = true
                                    loadingButtonType = "QR"
                                    val account = storageService.getAccount()
                                    if (account != null) {
                                        val paymentRequest = PaymentAppRequest(
                                            type = "card",
                                            action = PaymentAction.SALE.value,
                                            merchantRequestData = MerchantRequestData(
                                                amount = amountValue
                                            )
                                        )
                                        val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
                                        val intent = Intent(context, QRCodeDisplayActivity::class.java).apply {
                                            putExtra("REQUEST_DATA", paymentRequestData)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    } else {
                                        isProcessing = false
                                        loadingButtonType = null
                                    }
                                }
                            },
                            enabled = isValidAmount && !isProcessing,
                            isLoading = loadingButtonType == "QR",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PaymentOptionButton(
                        text = stringResource(R.string.payment_membership_card),
                        icon = Icons.Default.CardMembership,
                        onClick = {
                            if (isValidAmount && !isProcessing) {
                                isProcessing = true
                                loadingButtonType = "MEMBER"
                                val account = storageService.getAccount()
                                if (account != null) {
                                    val paymentRequest = PaymentAppRequest(
                                        type = "member",
                                        action = PaymentAction.SALE.value,
                                        merchantRequestData = MerchantRequestData(
                                            amount = amountValue
                                        )
                                    )
                                    val sdkType = BuildConfig.SDK_TYPE
                                    val paymentRequestData = paymentHelper.createPaymentAppRequest(account, paymentRequest)
                                    if (sdkType == "onefin") {
                                        val intent = Intent(
                                            context,
                                            TransparentPaymentActivity::class.java
                                        ).apply {
                                            putExtra("REQUEST_DATA", paymentRequestData)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    } else {
                                        val intent = Intent(
                                            context,
                                            SimpleChipTestActivity::class.java
                                        ).apply {
                                            putExtra("REQUEST_DATA", paymentRequestData)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    }
                                } else {
                                    isProcessing = false
                                    loadingButtonType = null
                                }
                            }
                        },
                        enabled = isValidAmount && !isProcessing,
                        isLoading = loadingButtonType == "MEMBER",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(bottomSpacerHeight))
            }
        }
    }
}