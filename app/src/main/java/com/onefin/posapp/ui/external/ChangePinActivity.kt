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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChangePinActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    private val TAG = "ChangePinActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PosAppTheme {
                    ChangePinScreen(
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
fun ChangePinScreen(
    gson: Gson,
    apiService: ApiService,
    storageService: StorageService,
    paymentRequest: PaymentAppRequest,
    onSuccess: (PaymentAppResponse) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    var currentStep by remember { mutableStateOf(ChangePinStep.WAITING_TAP) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
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
        currentStep = ChangePinStep.CARD_DETECTED
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
            ChangePinHeader()

            Spacer(modifier = Modifier.height(12.dp))

            // Terminal Info
            TerminalInfoCard(tid = tid, mid = mid)

            Spacer(modifier = Modifier.height(16.dp))

            // Main content based on step
            when (currentStep) {
                ChangePinStep.WAITING_TAP -> {
                    WaitingTapAnimation()
                }
                ChangePinStep.CARD_DETECTED -> {
                    // Card Display
                    MembershipCard(
                        logo = logo,
                        cardHolder = cardHolder,
                        cardNumber = cardNumber
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Input Section with TextFields
                    PinInputSection(
                        oldPin = oldPin,
                        onOldPinChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                oldPin = it
                                errorMessage = null
                            }
                        },
                        newPin = newPin,
                        onNewPinChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                newPin = it
                                errorMessage = null
                            }
                        },
                        confirmPin = confirmPin,
                        onConfirmPinChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                confirmPin = it
                                errorMessage = null
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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

                    // Action Buttons
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
                                // Validate
                                when {
                                    oldPin.length < 6 -> {
                                        errorMessage = "Vui lòng nhập đủ 6 số PIN cũ"
                                    }
                                    newPin.length < 6 -> {
                                        errorMessage = "Vui lòng nhập đủ 6 số PIN mới"
                                    }
                                    confirmPin.length < 6 -> {
                                        errorMessage = "Vui lòng nhập đủ 6 số xác nhận PIN"
                                    }
                                    newPin != confirmPin -> {
                                        errorMessage = "PIN mới không khớp"
                                    }
                                    oldPin == newPin -> {
                                        errorMessage = "PIN mới phải khác PIN cũ"
                                    }
                                    else -> {
                                        scope.launch {
                                            isProcessing = true
                                            errorMessage = null

                                            // Simulate API call
                                            delay(2000)

                                            // TODO: Implement actual change PIN API call
                                            try {
                                                val response = PaymentAppResponse(
                                                    type = "member",
                                                    action = PaymentAction.CHANGE_PIN.value,
                                                    paymentResponseData = PaymentResponseData(
                                                        status = PaymentStatusCode.SUCCESS,
                                                        description = "Đổi PIN thành công",
                                                        isSign = true
                                                    )
                                                )
                                                onSuccess(response)
                                            } catch (e: Exception) {
                                                isProcessing = false
                                                errorMessage = "Lỗi: ${e.message}"
                                                Timber.tag("ChangePinActivity").e(e, "Change PIN failed")
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp),
                            enabled = !isProcessing,
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
    }
}

enum class ChangePinStep {
    WAITING_TAP,
    CARD_DETECTED
}

@Composable
fun ChangePinHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Change PIN",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ĐỔI MÃ PIN",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Đổi mã PIN thẻ thành viên",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun TerminalInfoCard(tid: String, mid: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Terminal ID",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tid,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                thickness = DividerDefaults.Thickness, color = Color.White.copy(alpha = 0.2f)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Merchant ID",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mid,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun WaitingTapAnimation() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Card Icon
        val infiniteTransition = rememberInfiniteTransition(label = "tap_animation")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "scale"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .alpha(alpha)
                .background(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = "Tap Card",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Vui lòng đưa thẻ thành viên",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap thẻ vào thiết bị để tiếp tục",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Loading indicator
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color.White,
            strokeWidth = 3.dp
        )
    }
}

@Composable
fun MembershipCard(
    logo: String,
    cardHolder: String,
    cardNumber: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF43A047),
                            Color(0xFF66BB6A),
                            Color(0xFF4CAF50)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section - Card icon/logo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (logo.isNotEmpty()) {
                            AsyncImage(
                                model = logo,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = "Card Icon",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Text(
                            text = "MEMBER",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    // Middle section - Card number
                    Column {
                        Text(
                            text = cardNumber,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    // Bottom section - Cardholder name
                    Column {
                        Text(
                            text = "CARD HOLDER",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cardHolder,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinInputSection(
    oldPin: String,
    onOldPinChange: (String) -> Unit,
    newPin: String,
    onNewPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Old PIN
        PinTextField(
            label = "PIN cũ",
            value = oldPin,
            onValueChange = onOldPinChange
        )

        // New PIN
        PinTextField(
            label = "PIN mới",
            value = newPin,
            onValueChange = onNewPinChange
        )

        // Confirm PIN
        PinTextField(
            label = "Xác nhận PIN mới",
            value = confirmPin,
            onValueChange = onConfirmPinChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword
        ),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp)
    )
}