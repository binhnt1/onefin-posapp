package com.onefin.posapp.ui.external

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.models.data.VoidResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.components.PinInputSheet
import com.onefin.posapp.ui.modals.ProcessingDialog
import com.onefin.posapp.ui.modals.SuccessDialog
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.transaction.components.TransactionDetailContent
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RefundState {
    LOADING_TRANSACTION,
    INPUT_PASSWORD,
    SUCCESS,
    ERROR
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

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                RefundScreen(
                    gson = gson,
                    apiService = apiService,
                    storageService = storageService,
                    onCancel = { errorMessage -> cancelAction(errorMessage) },
                    onSuccess = { voidResult -> returnSuccess(voidResult, requestData) },
                )
            }
        }
    }

    private fun cancelAction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            val request = pendingRequest ?: getPaymentAppRequest()
            request?.let {
                val resultIntent = paymentHelper.buildResultIntentError(it, errorMessage)
                setResult(RESULT_OK, resultIntent)
                storageService.clearExternalPaymentContext()
            }
        }
        finish()
    }

    private fun returnSuccess(voidResult: VoidResultData, originalRequest: PaymentAppRequest) {
        val isExternalFlow = storageService.isExternalPaymentFlow()

        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest() ?: originalRequest
            val response = CardHelper.returnVoidResponse(voidResult, pendingRequest)
            val resultIntent = paymentHelper.buildResultIntentSuccess(response)
            setResult(RESULT_OK, resultIntent)
            storageService.clearExternalPaymentContext()
        } else {
            val response = CardHelper.returnVoidResponse(voidResult, originalRequest)
            val resultIntent = paymentHelper.buildResultIntentSuccess(response)
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }
}

@Composable
fun RefundScreen(
    gson: Gson,
    apiService: ApiService,
    onCancel: (String?) -> Unit,
    storageService: StorageService,
    onSuccess: (VoidResultData) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var timeRemaining by remember { mutableIntStateOf(60) }
    var currentState by remember { mutableStateOf(RefundState.LOADING_TRANSACTION) }
    var showPinSheet by remember { mutableStateOf(false) }
    var showProcessingDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var apiErrorCode by remember { mutableStateOf<String?>(null) }
    var transactionData by remember { mutableStateOf<SaleResultData?>(null) }
    var autoCloseCountdown by remember { mutableIntStateOf(3) }
    var pendingVoidResult by remember { mutableStateOf<VoidResultData?>(null) }

    val paymentRequest = remember {
        storageService.getPendingPaymentRequest() ?: (context as? RefundActivity)?.getPaymentAppRequest()
    }

    val requestType = paymentRequest?.type ?: "member"
    val passwordLength = if (requestType == "card") 4 else 6
    val billNumber = paymentRequest?.merchantRequestData?.billNumber

    suspend fun queryTransaction(billNumber: String): SaleResultData? {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = "/api/card/transaction/$billNumber"
                val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                if (resultApi.isSuccess()) {
                    val transactionJson = gson.toJson(resultApi.data)
                    gson.fromJson(transactionJson, SaleResultData::class.java)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun processRefund(
        transaction: SaleResultData,
        password: String
    ): Result<VoidResultData> {
        return try {
            val requestId = UtilHelper.generateRequestId()
            val requestBody = mapOf(
                "requestId" to requestId,
                "data" to mapOf(
                    "payment" to mapOf(
                        "currency" to "VND",
                        "transAmount" to (transaction.data?.totalAmount?.toLongOrNull() ?: 0)
                    ),
                    "originalTransId" to (transaction.header?.transId ?: "")
                ),
                "refundapproval" to password,
                "requestData" to mapOf(
                    "type" to requestType,
                    "action" to PaymentAction.REFUND.value,
                    "merchant_request_data" to gson.toJson(paymentRequest?.merchantRequestData)
                )
            )

            val api = "/api/card/void"
            val resultApi = apiService.post(api, requestBody) as ResultApi<*>

            if (resultApi.isSuccess()) {
                val voidResultData = gson.fromJson(gson.toJson(resultApi.data), VoidResultData::class.java)
                if (voidResultData != null) {
                    if (voidResultData.status?.code == "00") {
                        Result.success(voidResultData)
                    } else {
                        Result.failure(Exception(voidResultData.status?.message))
                    }
                } else {
                    Result.failure(Exception("Dữ liệu trả về không hợp lệ"))
                }
            } else {
                Result.failure(Exception(resultApi.description))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    LaunchedEffect(currentState, autoCloseCountdown) {
        if (currentState == RefundState.ERROR && transactionData == null && autoCloseCountdown > 0) {
            delay(1000)
            autoCloseCountdown--
            if (autoCloseCountdown == 0) {
                onCancel("Không tìm thấy giao dịch")
            }
        }
    }

    LaunchedEffect(currentState, timeRemaining) {
        if (currentState == RefundState.INPUT_PASSWORD && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
            if (timeRemaining == 0) {
                onCancel("Hết thời gian")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (billNumber.isNullOrEmpty()) {
            currentState = RefundState.ERROR
            errorDialogMessage = "Không có mã giao dịch"
            showErrorDialog = true
            return@LaunchedEffect
        }

        currentState = RefundState.LOADING_TRANSACTION
        delay(500)

        val transaction = queryTransaction(billNumber)
        if (transaction != null && transaction.status?.code == "00") {
            transactionData = transaction
            currentState = RefundState.INPUT_PASSWORD
            showPinSheet = true
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

            if (currentState == RefundState.INPUT_PASSWORD) {
                CircularCountdownTimer(
                    timeRemaining = timeRemaining,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEAECF0))

        when (currentState) {
            RefundState.LOADING_TRANSACTION -> {
                LoadingContent()
            }
            RefundState.ERROR -> {
                if (transactionData == null) {
                    ErrorContent(countdown = autoCloseCountdown)
                }
            }
            RefundState.INPUT_PASSWORD -> {
                transactionData?.let { transaction ->
                    val convertedTransaction = transaction.toTransaction()
                    TransactionDetailContent(
                        transaction = convertedTransaction,
                        showNotes = false
                    )
                }
            }
            RefundState.SUCCESS -> {
                transactionData?.let { transaction ->
                    val convertedTransaction = transaction.toTransaction()
                    TransactionDetailContent(
                        transaction = convertedTransaction,
                        showNotes = false
                    )
                }
            }
        }
    }

    if (showPinSheet && transactionData != null) {
        PinInputSheet(
            title = "Nhập mật khẩu hoàn tiền",
            subtitle = "Mật khẩu có $passwordLength chữ số",
            pinLength = passwordLength,
            initialValue = "",
            isLoading = showProcessingDialog,
            tipText = "Mật khẩu hoàn tiền khác với mã PIN thẻ",
            onComplete = { password ->
                scope.launch {
                    showPinSheet = false
                    showProcessingDialog = true

                    val result = processRefund(transactionData!!, password)

                    result.onSuccess { voidResultData ->
                        if (voidResultData.status?.code == "00") {
                            showProcessingDialog = false
                            pendingVoidResult = voidResultData
                            currentState = RefundState.SUCCESS
                            showSuccessDialog = true
                        } else {
                            val apiErrorMsg = voidResultData.status?.message ?: "Hoàn tiền thất bại"
                            errorDialogMessage = apiErrorMsg
                            showProcessingDialog = false
                            apiErrorCode = "API_ERROR"
                            showErrorDialog = true
                        }
                    }

                    result.onFailure { error ->
                        showProcessingDialog = false
                        apiErrorCode = "API_ERROR"
                        errorDialogMessage = error.message ?: "Hoàn tiền thất bại"
                        showErrorDialog = true
                    }
                }
            },
            onDismiss = {
                showPinSheet = false
                onCancel(null)
            }
        )
    }

    if (showProcessingDialog && !showPinSheet) {
        ProcessingDialog()
    }

    if (showSuccessDialog) {
        SuccessDialog(
            message = "Hoàn tiền thành công",
            countdownSeconds = 3,
            onCountdownComplete = {
                showSuccessDialog = false
                pendingVoidResult?.let { voidResult ->
                    onSuccess(voidResult)
                }
            }
        )
    }

    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            errorCode = apiErrorCode,
            onRetry = {
                showErrorDialog = false
                showPinSheet = true
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