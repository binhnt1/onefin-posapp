package com.onefin.posapp.ui.payment

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentSuccessData
import com.onefin.posapp.core.utils.DateTimeFormatter
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.Serializable

@AndroidEntryPoint
class PaymentSuccessActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PAYMENT_DATA = "EXTRA_PAYMENT_DATA"
        private var currentInstance: PaymentSuccessActivity? = null

        fun start(context: Context, paymentData: PaymentSuccessData) {
            val intent = Intent(context, PaymentSuccessActivity::class.java).apply {
                putExtra(EXTRA_PAYMENT_DATA, paymentData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }

        fun updateData(paymentData: PaymentSuccessData) {
            currentInstance?.updatePaymentData(paymentData)
        }

        fun isVisible(): Boolean {
            return currentInstance != null
        }
    }

    private var paymentDataState = mutableStateOf<PaymentSuccessData?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        val paymentData = extractPaymentData(intent)
        if (paymentData != null) {
            paymentDataState.value = paymentData
            setContent {
                PosAppTheme {
                    PaymentSuccessScreen(
                        paymentDataState = paymentDataState,
                        onBackClick = { finish() }
                    )
                }
            }
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val paymentData = extractPaymentData(intent)
        if (paymentData != null) {
            paymentDataState.value = paymentData
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInstance = null
    }

    private fun updatePaymentData(paymentData: PaymentSuccessData) {
        paymentDataState.value = paymentData
    }

    private fun extractPaymentData(intent: Intent): PaymentSuccessData? {
        val rawObject: Serializable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PAYMENT_DATA, Serializable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_PAYMENT_DATA)
        }

        @Suppress("UNCHECKED_CAST")
        return rawObject as? PaymentSuccessData
    }
}

@Composable
fun PaymentSuccessScreen(
    paymentDataState: State<PaymentSuccessData?>,
    onBackClick: () -> Unit
) {
    val paymentData = paymentDataState.value ?: return

    var secondsLeft by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
        }
        onBackClick() // tự đóng khi hết 15s
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFFFFFFF))
        ) {
            // Content scrollable
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 90.dp), // Space for button
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                // Logo
                Image(
                    painter = painterResource(id = R.drawable.logo_small),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .height(80.dp)
                        .padding(bottom = 40.dp)
                )

                // Success Icon & Background
                Image(
                    painter = painterResource(id = R.drawable.icon_success),
                    contentDescription = "Success Image",
                    modifier = Modifier
                        .size(160.dp)
                        .padding(bottom = 24.dp)
                )

                // Success Title
                Text(
                    text = stringResource(R.string.payment_success_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101828),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Amount
                Text(
                    text = UtilHelper.formatCurrency(paymentData.amount.toString(), "đ"),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF12B76A),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Transaction Details Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .background(Color(0xFFF9FAFB), shape = RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.payment_success_time),
                                fontSize = 14.sp,
                                color = Color(0xFF667085)
                            )
                            Text(
                                text = DateTimeFormatter.formatDateTime(paymentData.transactionTime),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF101828)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 16.dp),
                            thickness = 1.dp,
                            color = Color(0xFFEAECF0)
                        )

                        // Transaction ID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.payment_success_transaction_id),
                                fontSize = 14.sp,
                                color = Color(0xFF667085)
                            )
                            Text(
                                text = paymentData.transactionId,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF101828)
                            )
                        }
                    }
                }
            }

            // Back Button - Fixed at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 🕒 Countdown
                Text(
                    text = stringResource(R.string.auto_close_message, secondsLeft),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF12B76A)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.payment_success_back_button),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}