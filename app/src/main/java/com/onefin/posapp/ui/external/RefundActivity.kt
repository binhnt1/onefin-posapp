package com.onefin.posapp.ui.external

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.providers.TransactionProvider
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

enum class RefundState {
    LOADING_TRANSACTION,  // Đang query transaction
    INPUT_PASSWORD,       // Đang nhập mật khẩu
    CALLING_API,          // Đang gọi API refund
    SUCCESS,              // Thành công
    ERROR                 // Lỗi
}

@AndroidEntryPoint
class RefundActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RefundScreen(
                apiService = apiService,
                storageService = storageService,
                gson = gson,
                onSuccess = { response -> returnSuccess(response) },
                onCancel = { errorMessage -> cancelAction(errorMessage) }
            )
        }
    }

    private fun cancelAction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                val resultIntent = paymentHelper.buildResultIntentError(pendingRequest, errorMessage)
                setResult(RESULT_OK, resultIntent)
                storageService.clearExternalPaymentContext()
                finish()
            } else {
                val currentRequest = getPaymentAppRequest()
                if (currentRequest != null) {
                    val resultIntent = paymentHelper.buildResultIntentError(currentRequest, errorMessage)
                    setResult(RESULT_OK, resultIntent)
                    storageService.clearExternalPaymentContext()
                    finish()
                }
            }
        }
        finish()
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
}

@Composable
fun RefundScreen(
    apiService: ApiService,
    storageService: com.onefin.posapp.core.services.StorageService,
    gson: Gson,
    onCancel: (String?) -> Unit,
    onSuccess: (PaymentAppResponse) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var timeRemaining by remember { mutableIntStateOf(60) }
    var currentState by remember { mutableStateOf(RefundState.LOADING_TRANSACTION) }
    var passwordValue by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var apiErrorCode by remember { mutableStateOf<String?>(null) }
    var transactionData by remember { mutableStateOf<SaleResultData?>(null) }
    var autoCloseCountdown by remember { mutableIntStateOf(3) }

    val paymentRequest = remember {
        storageService.getPendingPaymentRequest() ?: (context as? RefundActivity)?.getPaymentAppRequest()
    }

    val requestType = paymentRequest?.type ?: "member"
    val passwordLength = if (requestType == "card") 4 else 6
    val billNumber = paymentRequest?.merchantRequestData?.billNumber

    // Function to query transaction
    suspend fun queryTransaction(billNumber: String): SaleResultData? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = TransactionProvider.CONTENT_URI.buildUpon()
                    .appendPath(billNumber)
                    .build()

                var cursor: Cursor? = null
                try {
                    cursor = context.contentResolver.query(
                        uri,
                        null,
                        null,
                        null,
                        null
                    )

                    if (cursor != null && cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(TransactionProvider.COLUMN_MEMBER_RESPONSE_DATA)
                        if (columnIndex != -1) {
                            val jsonData = cursor.getString(columnIndex)
                            Timber.d("Transaction query result: $jsonData")
                            gson.fromJson(jsonData, SaleResultData::class.java)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } finally {
                    cursor?.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error querying transaction")
                null
            }
        }
    }

    // Function to call Refund API
    suspend fun callRefundAPI(
        transaction: SaleResultData,
        password: String
    ): Result<Map<*, *>> {
        return try {
            val requestId = UUID.randomUUID().toString()
            val requestBody = mapOf(
                "requestId" to requestId,
                "data" to mapOf(
                    "bank" to "",
                    "payment" to mapOf(
                        "currency" to (transaction.requestData?.currency ?: "VND"),
                        "transAmount" to (transaction.requestData?.amount?.toString() ?: "0")
                    ),
                    "refundapproval" to password,
                    "originalTransId" to (transaction.header?.transId ?: "")
                ),
                "refundapproval" to password,
                "requestData" to mapOf(
                    "type" to requestType,
                    "action" to PaymentAction.REFUND.value,
                    "merchant_request_data" to gson.toJson(paymentRequest?.merchantRequestData)
                )
            )

            Timber.d("Refund API Request: $requestBody")

            val api = if (requestType == "member") "api/member/nfcVoid" else "api/card/void"
            val resultApi = apiService.post(api, requestBody) as ResultApi<*>
            val responseData = gson.fromJson(
                gson.toJson(resultApi.data),
                Map::class.java
            )

            val statusCode = (responseData?.get("status") as? Map<*, *>)?.get("code") as? String
            val statusMessage = (responseData?.get("status") as? Map<*, *>)?.get("message") as? String

            if (statusCode == "00") {
                Timber.d("Refund API Success - Full Response: $responseData")
                Result.success(responseData)
            } else {
                Timber.e("Refund API Failed - Code: $statusCode, Message: $statusMessage")
                Result.failure(Exception(statusMessage ?: "Hoàn tiền thất bại"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Refund API Exception")
            Result.failure(e)
        }
    }

    // Auto-close countdown for error state
    LaunchedEffect(currentState, autoCloseCountdown) {
        if (currentState == RefundState.ERROR && transactionData == null && autoCloseCountdown > 0) {
            delay(1000)
            autoCloseCountdown--
            if (autoCloseCountdown == 0) {
                onCancel("Không tìm thấy giao dịch")
            }
        }
    }

    // Main countdown timer
    LaunchedEffect(currentState, timeRemaining) {
        if (currentState != RefundState.LOADING_TRANSACTION &&
            currentState != RefundState.SUCCESS &&
            currentState != RefundState.ERROR &&
            timeRemaining > 0) {
            delay(1000)
            timeRemaining--
            if (timeRemaining == 0) {
                onCancel("Hết thời gian")
            }
        }
    }

    // Query transaction on start
    LaunchedEffect(Unit) {
        if (billNumber.isNullOrEmpty()) {
            currentState = RefundState.ERROR
            errorDialogMessage = "Không có mã giao dịch"
            showErrorDialog = true
            return@LaunchedEffect
        }

        currentState = RefundState.LOADING_TRANSACTION
        delay(500) // Show loading briefly

        val transaction = queryTransaction(billNumber)
        if (transaction != null && transaction.status?.code == "00") {
            transactionData = transaction
            currentState = RefundState.INPUT_PASSWORD

            // Auto-fill password for bank type
            if (requestType == "card") {
                passwordValue = "0000"
            }
        } else {
            currentState = RefundState.ERROR
            transactionData = null
            autoCloseCountdown = 3
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFF6FF),
                        Color.White
                    )
                )
            )
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp))

            Text(
                text = "Hoàn tiền",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            if (currentState == RefundState.INPUT_PASSWORD || currentState == RefundState.CALLING_API) {
                CircularCountdownTimer(
                    timeRemaining = timeRemaining,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

        // Content
        when (currentState) {
            RefundState.LOADING_TRANSACTION -> {
                LoadingContent()
            }
            RefundState.ERROR -> {
                if (transactionData == null) {
                    ErrorContent(countdown = autoCloseCountdown)
                }
            }
            else -> {
                transactionData?.let { transaction ->
                    RefundInputContent(
                        transaction = transaction,
                        requestType = requestType,
                        passwordLength = passwordLength,
                        passwordValue = passwordValue,
                        currentState = currentState,
                        onPasswordChange = { passwordValue = it },
                        onConfirm = {
                            scope.launch {
                                currentState = RefundState.CALLING_API
                                delay(300)

                                val result = callRefundAPI(transaction, passwordValue)

                                result.onSuccess { response ->
                                    currentState = RefundState.SUCCESS
                                    val successResponse = PaymentAppResponse(
                                        type = requestType,
                                        action = PaymentAction.REFUND.value,
                                        paymentResponseData = PaymentResponseData(
                                            status = PaymentStatusCode.SUCCESS,
                                            description = "Hoàn tiền thành công",
                                            additionalData = response
                                        )
                                    )
                                    delay(1500)
                                    onSuccess(successResponse)
                                }

                                result.onFailure { error ->
                                    currentState = RefundState.INPUT_PASSWORD
                                    apiErrorCode = "API_ERROR"
                                    errorDialogMessage = error.message ?: "Hoàn tiền thất bại"
                                    showErrorDialog = true
                                }
                            }
                        },
                        onCancel = { onCancel(null) }
                    )
                }
            }
        }
    }

    // Error Dialog
    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            errorCode = apiErrorCode,
            onRetry = {
                showErrorDialog = false
                passwordValue = if (requestType == "card") "0000" else ""
                currentState = RefundState.INPUT_PASSWORD
            },
            onCancel = {
                showErrorDialog = false
                onCancel(null)
            }
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp, 0.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color(0xFF3B82F6),
            strokeWidth = 5.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Đang tải thông tin giao dịch...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF101828),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Vui lòng đợi",
            fontSize = 14.sp,
            color = Color(0xFF667085),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(countdown: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFFEF4444),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Không tìm thấy giao dịch",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEF4444),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Giao dịch không tồn tại hoặc đã bị hủy",
            fontSize = 14.sp,
            color = Color(0xFF667085),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Tự động đóng sau ${countdown}s",
            fontSize = 14.sp,
            color = Color(0xFF667085),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RefundInputContent(
    transaction: SaleResultData,
    requestType: String,
    passwordLength: Int,
    passwordValue: String,
    currentState: RefundState,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Transaction Info Card
        TransactionInfoCard(transaction = transaction)

        Spacer(modifier = Modifier.height(24.dp))

        // Success state
        if (currentState == RefundState.SUCCESS) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SuccessIcon()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hoàn tiền thành công!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Password Input
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF101828),
                    text = "Nhập mật khẩu hoàn tiền",
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Mật khẩu có $passwordLength chữ số",
                    fontSize = 14.sp,
                    color = Color(0xFF667085),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                PinDotsDisplay(
                    pinLength = passwordValue.length,
                    maxLength = passwordLength
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric Keyboard
                NumericKeyboard(
                    enabled = currentState != RefundState.CALLING_API,
                    onNumberClick = { number ->
                        if (passwordValue.length < passwordLength) {
                            onPasswordChange(passwordValue + number)
                        }
                    },
                    onDeleteClick = {
                        if (passwordValue.isNotEmpty()) {
                            onPasswordChange(passwordValue.dropLast(1))
                        }
                    },
                    onClearClick = {
                        onPasswordChange("")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tip
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mật khẩu hoàn tiền khác với mã PIN thẻ",
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        if (currentState != RefundState.SUCCESS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    enabled = currentState != RefundState.CALLING_API,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF667085)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD0D5DD))
                ) {
                    Text(
                        text = "Hủy",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Confirm button
                Button(
                    onClick = onConfirm,
                    enabled = passwordValue.length == passwordLength && currentState != RefundState.CALLING_API,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        disabledContainerColor = Color(0xFFD0D5DD)
                    )
                ) {
                    if (currentState == RefundState.CALLING_API) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Xác nhận",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TransactionInfoCard(transaction: SaleResultData) {
    val cardBrand = transaction.data?.cardBrand ?: ""
    val totalAmount = transaction.data?.totalAmount ?: "0"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💰",
                    fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Số tiền hoàn",
                    fontSize = 14.sp,
                    color = Color(0xFF667085)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = UtilHelper.formatCurrency(totalAmount.toLongOrNull() ?: 0L, "đ"),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF)
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color(0xFFE5E7EB))

                Spacer(modifier = Modifier.height(16.dp))

                // Transaction details
                TransactionDetailRow(
                    label = "Mã giao dịch",
                    value = transaction.header?.transId ?: "-"
                )

                Spacer(modifier = Modifier.height(8.dp))

                TransactionDetailRow(
                    label = "Mã đơn hàng",
                    value = transaction.requestData?.billNumber ?: "-"
                )

                Spacer(modifier = Modifier.height(8.dp))

                TransactionDetailRow(
                    label = "Thời gian",
                    value = formatTransmitDateTime(transaction.header?.transmitsDateTime)
                )
            }

            // Card brand badge in top-right corner
            if (cardBrand.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6))
                ) {
                    Text(
                        text = cardBrand.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF)
                    )
                }
            }
        }
    }
}

private fun formatTransmitDateTime(dateTime: String?): String {
    if (dateTime.isNullOrEmpty() || dateTime.length < 12) return "-"

    return try {
        // Format: ddMMyyHHmmss
        val day = dateTime.substring(0, 2)
        val month = dateTime.substring(2, 4)
        val year = "20${dateTime.substring(4, 6)}"
        val hour = dateTime.substring(6, 8)
        val minute = dateTime.substring(8, 10)
        val second = dateTime.substring(10, 12)

        "$day/$month/$year $hour:$minute:$second"
    } catch (e: Exception) {
        dateTime
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF667085)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF101828)
        )
    }
}

@Composable
private fun CircularCountdownTimer(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = timeRemaining.toFloat() / 60,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "progress"
    )

    val progressColor = when {
        timeRemaining <= 10 -> Color(0xFFEF4444)
        timeRemaining <= 30 -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f

            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = radius - strokeWidth / 2,
                style = Stroke(width = strokeWidth)
            )

            val sweepAngle = -360f * progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter - strokeWidth, diameter - strokeWidth)
            )
        }

        Text(
            text = "$timeRemaining",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = progressColor
        )
    }
}

@Composable
private fun PinDotsDisplay(
    pinLength: Int,
    maxLength: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            PinDot(
                isFilled = index < pinLength,
                isActive = index == pinLength
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isActive: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(24.dp)
            .scale(scale)
            .background(
                if (isFilled) Color(0xFF3B82F6) else Color.White,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = if (isFilled) Color(0xFF3B82F6)
                else if (isActive) Color(0xFF60A5FA)
                else Color(0xFFD1D5DB),
                shape = CircleShape
            )
    )
}

@Composable
private fun NumericKeyboard(
    enabled: Boolean = true,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KeyboardRow(
            keys = listOf("1", "2", "3"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("4", "5", "6"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        KeyboardRow(
            keys = listOf("7", "8", "9"),
            enabled = enabled,
            onKeyClick = onNumberClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeyboardButton(
                text = "C",
                enabled = enabled,
                onClick = onClearClick,
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )

            KeyboardButton(
                text = "0",
                enabled = enabled,
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f)
            )

            KeyboardButton(
                text = "⌫",
                enabled = enabled,
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    keys: List<String>,
    enabled: Boolean = true,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        keys.forEach { key ->
            KeyboardButton(
                text = key,
                enabled = enabled,
                onClick = { onKeyClick(key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSpecialKey: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_press"
    )

    Surface(
        onClick = {
            if (enabled) {
                isPressed = true
                onClick()
            }
        },
        modifier = modifier
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            if (isSpecialKey) Color(0xFFF2F4F7) else Color(0xFFF9FAFB)
        } else {
            Color(0xFFE5E7EB)
        },
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFD0D5DD)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = if (isSpecialKey) 22.sp else 24.sp,
                fontWeight = if (isSpecialKey) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enabled) {
                    if (isSpecialKey) Color(0xFF667085) else Color(0xFF101828)
                } else {
                    Color(0xFF9CA3AF)
                }
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun SuccessIcon() {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale.value)
            .background(
                color = Color(0xFF10B981),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✓",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}