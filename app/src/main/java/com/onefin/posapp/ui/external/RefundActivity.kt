package com.onefin.posapp.ui.external

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.home.components.NumberPad
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RefundActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    private val TAG = "RefundActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PosAppTheme {
                    RefundScreen(
                        gson = gson,
                        apiService = apiService,
                        paymentRequest = requestData,
                        onCancel = { cancelAction() },
                        storageService = storageService,
                        onSuccess = { saleResult -> returnSuccess(saleResult) },
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
fun RefundScreen(
    gson: Gson,
    apiService: ApiService,
    storageService: StorageService,
    paymentRequest: PaymentAppRequest,
    onSuccess: (PaymentAppResponse) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Extract refund info
    val amount = 100000L
    val billNumber = paymentRequest.merchantRequestData?.billNumber ?: ""
    val referenceId = paymentRequest.merchantRequestData?.referenceId ?: ""
    val message = paymentRequest.merchantRequestData?.message ?: "Hoàn tiền"

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
            // Header - compact hơn
            RefundHeader()

            Spacer(modifier = Modifier.height(12.dp))

            // Refund Info Card - compact hơn
            RefundInfoCard(
                billNumber = billNumber,
                referenceId = referenceId,
                amount = amount,
                message = message
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN Display
            PinDisplay(pin = pin, maxLength = 6)

            Spacer(modifier = Modifier.height(32.dp))

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
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            NumberPad(
                onNumberClick = { number ->
                    if (pin.length < 6) {
                        pin += number
                        errorMessage = null
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        errorMessage = null
                    }
                },
                onClearClick = {
                    pin = ""
                    errorMessage = null
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons - nhỏ hơn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hủy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                // Confirm Button
                Button(
                    onClick = {
                        if (pin.length == 6) {
                            scope.launch {
                                isProcessing = true
                                errorMessage = null

                                // Simulate API call for refund
                                delay(2000)

                                // TODO: Implement actual refund API call
                                try {
                                    // Call refund API here
                                    val response = PaymentAppResponse(
                                        type = "member",
                                        action = PaymentAction.REFUND.value,
                                        paymentResponseData = PaymentResponseData(
                                            refNo = referenceId,
                                            billNumber = billNumber,
                                            referenceId = referenceId,
                                            status = PaymentStatusCode.SUCCESS,
                                            description = "Hoàn tiền thành công",
                                            additionalData = paymentRequest.merchantRequestData?.additionalData
                                        )
                                    )
                                    onSuccess(response)
                                } catch (e: Exception) {
                                    isProcessing = false
                                    errorMessage = "Lỗi: ${e.message}"
                                    Timber.tag("RefundActivity").e(e, "Refund failed")
                                }
                            }
                        } else {
                            errorMessage = "Vui lòng nhập đủ 6 số PIN"
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    enabled = !isProcessing && pin.length == 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xác nhận", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun RefundHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Refund",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "HOÀN TIỀN",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Nhập mã PIN để xác nhận",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun RefundInfoCard(
    billNumber: String,
    referenceId: String,
    amount: Long,
    message: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Số tiền hoàn",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = UtilHelper.formatCurrency(amount),
                    color = Color(0xFFFFEB3B),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // Bill Number
            InfoRow(label = "Số hóa đơn", value = billNumber)
            Spacer(modifier = Modifier.height(8.dp))

            // Reference ID
            InfoRow(label = "Mã tham chiếu", value = referenceId)

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(label = "Lý do", value = message)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PinDisplay(pin: String, maxLength: Int = 6) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            PinDot(isFilled = index < pin.length)
        }
    }
}

@Composable
fun PinDot(isFilled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isFilled) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "pin_dot_scale"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isFilled) Color.White else Color.White.copy(alpha = 0.3f)
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.6f),
                shape = CircleShape
            )
    )
}

@Composable
fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "action_button_scale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .height(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.15f else 0.08f))
            .clickable(enabled = enabled) {
                isPressed = true
                onClick()
            }
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}