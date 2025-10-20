package com.onefin.posapp.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.R
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.core.utils.VietQRHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject
import androidx.activity.OnBackPressedCallback
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.utils.PaymentHelper

@AndroidEntryPoint
class QRCodeDisplayActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var paymentHelper: PaymentHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            val account = storageService.getAccount()
            if (account != null) {
                setContent {
                    PosAppTheme {
                        QRCodeDisplayScreen(
                            onCancel = { cancelTransaction() },
                            accountName = account.terminal.accountName,
                            bankNapasId = account.terminal.bankNapasId,
                            accountNumber = account.terminal.accountNumber,
                            amount = requestData.merchantRequestData?.amount ?: 0,
                        )
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelTransaction()
                }
            }
        )
    }

    private fun cancelTransaction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                val response = paymentHelper.createPaymentAppResponseCancel(pendingRequest, errorMessage)
                storageService.clearExternalPaymentContext()
                val resultIntent = Intent().apply {
                    putExtra(
                        ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                        gson.toJson(response)
                    )
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                val currentRequest = getPaymentAppRequest()
                if (currentRequest != null) {
                    val response = paymentHelper.createPaymentAppResponseCancel(currentRequest, errorMessage)
                    val resultIntent = Intent().apply {
                        putExtra(
                            ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                            gson.toJson(response)
                        )
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
        finish()
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeDisplayScreen(
    amount: Long,
    accountName: String,
    bankNapasId: String,
    accountNumber: String,
    onCancel: () -> Unit
) {
    val context = LocalContext.current as Activity

    val vietQRString = remember {
        VietQRHelper.buildVietQRString(
            amount = amount,
            bankNapasId = bankNapasId,
            accountNumber = accountNumber,
            info = context.getString(R.string.qr_payment_description)
        )
    }

    var countdown by remember { mutableIntStateOf(90) }

    LaunchedEffect(key1 = true) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        onCancel()
    }

    val isP2 = remember {
        Build.MODEL.lowercase().contains("p2")
    }

    val logoHeight = if (isP2) 24.dp else 40.dp
    val amountFontSize = if (isP2) 24.sp else 36.sp
    val cancelButtonHeight = if (isP2) 48.dp else 56.dp


    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding( 16.dp)
            ) {
                Button(
                    onClick = {
                        onCancel()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cancelButtonHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEF3F2),
                        contentColor = Color(0xFFD92D20)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qr_display_cancel_transaction),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_small),
                            contentDescription = "Logo",
                            modifier = Modifier.height(logoHeight),
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Image(
                            painter = painterResource(id = R.drawable.vietqr_logo),
                            contentDescription = "VietQR Logo",
                            modifier = Modifier.height(logoHeight)
                        )
                    }
                    var qrSize = LocalConfiguration.current.screenWidthDp.dp - 64.dp
                    if (isP2) {
                        qrSize *= 0.75f
                    }

                    QRCodeImage(
                        data = vietQRString,
                        size = qrSize,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(
                            color = Color(0xFFEAECF0)
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 8.dp else 16.dp))
                        Text(
                            text = accountName.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101828)
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 2.dp else 4.dp))
                        Text(
                            text = accountNumber,
                            fontSize = 16.sp,
                            color = Color(0xFF475467)
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 8.dp else 16.dp))
                        HorizontalDivider(
                            color = Color(0xFFEAECF0)
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 8.dp else 16.dp))
                        Text(
                            text = stringResource(R.string.qr_display_scan_instruction),
                            fontSize = 14.sp,
                            color = Color(0xFF475467),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.qr_display_single_use_note),
                            fontSize = 14.sp,
                            color = Color(0xFF475467),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 8.dp else 16.dp))
                        Text(
                            text = UtilHelper.formatCurrency(amount.toString(), "đ"),
                            fontSize = amountFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF101828)
                        )
                        Spacer(modifier = Modifier.height(if (isP2) 1.dp else 2.dp))
                        Text(
                            text = stringResource(id = R.string.qr_auto_cancel_countdown, countdown),
                            fontSize = 14.sp,
                            color = Color(0xFFD92D20),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}