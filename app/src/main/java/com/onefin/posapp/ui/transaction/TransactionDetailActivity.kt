package com.onefin.posapp.ui.transaction

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.VoidResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.components.PinInputSheet
import com.onefin.posapp.ui.modals.ProcessingDialog
import com.onefin.posapp.ui.modals.SuccessDialog
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.settlement.SettlementActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.SendEmailSheet
import com.onefin.posapp.ui.transaction.components.TransactionDetailContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransactionDetailActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var printerHelper: PrinterHelper

    @Inject
    lateinit var receiptPrinter: ReceiptPrinter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transactionId = intent.getStringExtra("TRANSACTION_ID") ?: ""

        setContent {
            PosAppTheme {
                TransactionDetailScreen(
                    transactionId = transactionId,
                    apiService = apiService,
                    printerHelper = printerHelper,
                    receiptPrinter = receiptPrinter,
                    storageService = storageService,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    apiService: ApiService,
    printerHelper: PrinterHelper,
    receiptPrinter: ReceiptPrinter,
    storageService: StorageService,
    onBackPressed: () -> Unit
) {
    var transaction by remember { mutableStateOf<Transaction?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(true) }
    var showEmailSheet by remember { mutableStateOf(false) }
    var showPinSheet by remember { mutableStateOf(false) }
    var showProcessingDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var apiErrorCode by remember { mutableStateOf<String?>(null) }
    var isCancelSuccess by remember { mutableStateOf(false) }

    val customGreenColor = Color(0xFF10B981)
    val customRedColor = Color(0xFFEF4444)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<Account?>(null) }

    val errorLoadingData = stringResource(id = R.string.error_loading_data)
    val errorNoAccountInfo = stringResource(id = R.string.error_no_account_info)
    val errorPrinterNotReady = stringResource(id = R.string.error_printer_not_ready)
    val successPrintReceipt = stringResource(id = R.string.success_print_receipt)
    val errorPrintReceiptFailed = stringResource(id = R.string.error_print_receipt_failed)
    val successMessage = stringResource(id = R.string.success_cancel_transaction)

    // Determine password length based on transaction type
    val passwordLength = remember(transaction) {
        if (transaction?.formType ==  1) 4 else if (transaction?.formType == 3) 6 else 0
    }

    LaunchedEffect(Unit) {
        account = storageService.getAccount()
    }

    LaunchedEffect(transactionId) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val gson = Gson()
                val endpoint = "/api/transaction/$transactionId"
                val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                if (resultApi.isSuccess()) {
                    val jsonString = gson.toJson(resultApi.data)
                    transaction = gson.fromJson(jsonString, Transaction::class.java)
                } else {
                    errorMessage = resultApi.description
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: errorLoadingData
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun processRefund(password: String): Result<VoidResultData> {
        delay(1000)
        return try {
            val gson = Gson()
            val requestId = UtilHelper.generateRequestId()
            val type = when (transaction?.formType) {
                1 -> "card"
                2 -> "qr"
                3 -> "member"
                else -> "card"
            }
            val body = mapOf(
                "requestId" to requestId,
                "data" to mapOf(
                    "originalTransId" to (transaction?.transactionId ?: ""),
                    "payment" to mapOf(
                        "currency" to "VND",
                        "transAmount" to (transaction?.totalTransAmt ?: 0),
                    ),
                ),
                "refundapproval" to password,
                "requestData" to mapOf(
                    "type" to type,
                    "action" to PaymentAction.REFUND.value
                )
            )

            val resultApi = apiService.post("/api/card/void", body) as ResultApi<*>
            if (resultApi.isSuccess()) {
                val voidResultData = gson.fromJson(gson.toJson(resultApi.data), VoidResultData::class.java)
                if (voidResultData != null) {
                    if (voidResultData.status?.code == "00") {
                        Result.success(voidResultData)
                    } else {
                        Result.failure(Exception(voidResultData.status?.message))
                    }
                } else Result.failure(Exception("Dữ liệu trả về không hợp lệ"))
            } else {
                Result.failure(Exception(resultApi.description))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (data.visuals.message.contains("thành công") ||
                                data.visuals.message.contains("successfully")
                            )
                                customGreenColor
                            else
                                customRedColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (data.visuals.message.contains("thành công") ||
                                    data.visuals.message.contains("successfully")
                                )
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = data.visuals.message,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            )
        },
        topBar = {
            Surface(
                shadowElevation = 4.dp,
                color = Color.White
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(id = R.string.title_transaction_detail),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackPressed,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(id = R.string.content_desc_back),
                                    tint = Color(0xFF111827)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color(0xFF111827)
                        )
                    )
                }
            }
        },
        bottomBar = {
            // Ẩn nút nếu đã hủy thành công
            if (!isCancelSuccess) {
                val showSendAndPrint = transaction != null && transaction!!.showButtons()
                val allowCancel =
                    account?.terminal?.systemConfig?.allowCancel == true && (transaction?.processStatus == 0 || transaction?.processStatus == 1)

                if (showSendAndPrint || allowCancel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE5E7EB))
                            .padding(top = 1.dp)
                            .background(Color.White)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (allowCancel) {
                                OutlinedButton(
                                    onClick = { showPinSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = customRedColor
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        width = 1.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.btn_cancel),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (showSendAndPrint) {
                                OutlinedButton(
                                    onClick = { showEmailSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = customGreenColor
                                    ),
                                    enabled = isEnabled,
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = isEnabled)
                                        .copy(
                                            width = 1.dp
                                        )
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.btn_send_bill),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            showProcessingDialog = true

                                            if (account == null) {
                                                showProcessingDialog = false
                                                snackbarHostState.showSnackbar(errorNoAccountInfo)
                                                return@launch
                                            }

                                            if (!printerHelper.waitForReady(timeoutMs = 3000)) {
                                                showProcessingDialog = false
                                                snackbarHostState.showSnackbar(errorPrinterNotReady)
                                                return@launch
                                            }

                                            val result = receiptPrinter.printReceipt(
                                                transaction = transaction!!,
                                                terminal = account!!.terminal
                                            )

                                            showProcessingDialog = false

                                            if (result.isSuccess) {
                                                snackbarHostState.showSnackbar(successPrintReceipt)
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    errorPrintReceiptFailed.format(
                                                        result.exceptionOrNull()?.message ?: ""
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customGreenColor
                                    )
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.btn_print_receipt),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF9FAFB),
                            Color(0xFFFFFFFF)
                        )
                    )
                )
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = customGreenColor,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang tải...",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(24.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEE2E2)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = customRedColor,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = customRedColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                transaction != null -> {
                    TransactionDetailContent(
                        transaction = transaction!!,
                        notes = notes,
                        onNotesChange = { notes = it }
                    )
                }
            }
        }
    }

    // PIN Input Sheet for Cancel Transaction
    if (showPinSheet && transaction != null) {
        PinInputSheet(
            title = "Nhập mật khẩu hủy giao dịch",
            subtitle = "Mật khẩu có $passwordLength chữ số",
            pinLength = passwordLength,
            isLoading = showProcessingDialog,
            tipText = "Mật khẩu hủy giao dịch khác với mã PIN thẻ",
            onComplete = { password ->
                scope.launch {
                    showPinSheet = false
                    showProcessingDialog = true
                    val result = processRefund(password)

                    result.onSuccess {
                        showProcessingDialog = false
                        isCancelSuccess = true
                        showSuccessDialog = true
                    }

                    result.onFailure { error ->
                        showProcessingDialog = false
                        apiErrorCode = "API_ERROR"
                        errorDialogMessage = error.message ?: "Hủy giao dịch thất bại"
                        showErrorDialog = true
                    }
                }
            },
            onDismiss = {
                showPinSheet = false
            }
        )
    }

    // Success Dialog with Countdown
    if (showSuccessDialog) {
        SuccessDialog(
            message = successMessage,
            countdownSeconds = 3,
            onCountdownComplete = {
                showSuccessDialog = false
                SettlementActivity.shouldRefresh = true
                TransactionActivity.shouldRefresh = true
                onBackPressed()
            }
        )
    }

    // Error Dialog
    if (showErrorDialog) {
        ModernErrorDialog(
            message = errorDialogMessage,
            errorCode = apiErrorCode,
            onRetry = {
                showErrorDialog = false
                showPinSheet = true
            },
            onCancel = {
                showErrorDialog = false
            }
        )
    }

    if (showProcessingDialog && !showPinSheet) {
        ProcessingDialog()
    }

    if (showEmailSheet && transaction != null) {
        val successEmailMessage = stringResource(id = R.string.success_send_email)
        val failedEmailMessage = stringResource(id = R.string.error_send_email_failed)
        SendEmailSheet(
            transactionId = transaction?.transactionId ?: "",
            apiService = apiService,
            storageService = storageService,
            onDismiss = {
                showEmailSheet = false
            },
            onProcessingStart = {
                showProcessingDialog = true
            },
            onProcessingEnd = {
                showProcessingDialog = false
            },
            onSuccess = {
                showEmailSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(successEmailMessage)
                }
            },
            onError = { error ->
                showEmailSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(failedEmailMessage.format(error))
                }
            }
        )
    }
}