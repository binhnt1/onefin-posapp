package com.onefin.posapp.ui.external

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.MDC.put
import timber.log.Timber
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class CheckBalanceActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    private val TAG = "CheckBalanceActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PosAppTheme {
                    CheckBalanceScreen(
                        gson = gson,
                        apiService = apiService,
                        paymentRequest = requestData,
                        onCancel = { cancelAction() },
                        storageService = storageService,
                        onSuccess = { response -> returnSuccess(response) },
                        onError = { errorMsg -> cancelAction(errorMsg) }
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelAction()
                }
            }
        )
    }

    private fun returnSuccess(response: PaymentAppResponse) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            storageService.clearExternalPaymentContext()
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

    private fun cancelAction(errorMessage: String? = null) {
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

@Composable
fun CheckBalanceScreen(
    gson: Gson,
    apiService: ApiService,
    storageService: StorageService,
    paymentRequest: PaymentAppRequest,
    onSuccess: (PaymentAppResponse) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    var currentStep by remember { mutableStateOf(CheckBalanceStep.WAITING_TAP) }
    var isLoading by remember { mutableStateOf(false) }
    var balance by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Extract info
    val tid = paymentRequest.merchantRequestData?.tid ?: ""
    val mid = paymentRequest.merchantRequestData?.mid ?: ""

    // Simulated card data (sau khi tap)
    val cardHolder = "NGUYEN VAN A"
    val cardNumber = "9704 **** **** 1234"
    val logo = storageService.getAccount()?.terminal?.logo ?: ""

    // Auto simulate tap after 5s
    LaunchedEffect(Unit) {
        delay(5000)
        currentStep = CheckBalanceStep.CARD_DETECTED

        // Simulate loading balance
        isLoading = true
        delay(2000)

        // Simulated balance (random between 100,000 and 10,000,000 VND)
        balance = (100000..10000000).random().toLong()
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF0D47A1),
                        Color(0xFF01579B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            CheckBalanceHeader()

            Spacer(modifier = Modifier.height(12.dp))

            // Terminal Info
            TerminalInfoCard(tid = tid, mid = mid)

            Spacer(modifier = Modifier.height(16.dp))

            // Main content based on step
            when (currentStep) {
                CheckBalanceStep.WAITING_TAP -> {
                    WaitingTapAnimation()
                }
                CheckBalanceStep.CARD_DETECTED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Card Display
                        MembershipCard(
                            logo = logo,
                            cardHolder = cardHolder,
                            cardNumber = cardNumber
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Balance Display
                        BalanceCard(
                            balance = balance,
                            isLoading = isLoading
                        )

                        // Error message
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFF6B6B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Close Button
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp),
                                enabled = !isLoading,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đóng", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Confirm Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val response = PaymentAppResponse(
                                                type = "member",
                                                action = PaymentAction.CHECK_BALANCE.value,
                                                paymentResponseData = PaymentResponseData(
                                                    balance = balance,
                                                    status = PaymentStatusCode.SUCCESS,
                                                    description = "Kiểm tra số dư thành công",
                                                    additionalData = paymentRequest.merchantRequestData?.additionalData?.apply {
                                                        put("balance", balance.toString())
                                                        put("cardNumber", cardNumber)
                                                        put("cardHolder", cardHolder)
                                                    }
                                                )
                                            )
                                            onSuccess(response)
                                        } catch (e: Exception) {
                                            errorMessage = "Lỗi: ${e.message}"
                                            Timber.tag("CheckBalanceActivity").e(e, "Check balance failed")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xác nhận", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

enum class CheckBalanceStep {
    WAITING_TAP,
    CARD_DETECTED
}

@Composable
fun CheckBalanceHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = "Check Balance",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "KIỂM TRA SỐ DƯ",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Xem số dư tài khoản thẻ thành viên",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun BalanceCard(
    balance: Long,
    isLoading: Boolean
) {
    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SỐ DƯ KHẢ DỤNG",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Đang tải...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            } else {
                // Animated balance display
                val animatedBalance by animateFloatAsState(
                    targetValue = balance.toFloat(),
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                    label = "balance_animation"
                )

                Text(
                    text = currencyFormat.format(animatedBalance.toDouble()),
                    color = Color(0xFF4CAF50),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kiểm tra thành công",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}