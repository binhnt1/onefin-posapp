package com.onefin.posapp.ui.transaction

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.SendEmailSheet
import com.onefin.posapp.ui.transaction.components.TransactionDetailContent
import dagger.hilt.android.AndroidEntryPoint
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
    var isPrinting by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(true) }
    var showEmailSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val customGreenColor = Color(0xFF16A34A)
    val customRedColor = Color(0xFFDC2626)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<Account?>(null) }

    val errorLoadingData = stringResource(id = R.string.error_loading_data)
    val errorNoAccountInfo = stringResource(id = R.string.error_no_account_info)
    val errorPrinterNotReady = stringResource(id = R.string.error_printer_not_ready)
    val successPrintReceipt = stringResource(id = R.string.success_print_receipt)
    val errorPrintReceiptFailed = stringResource(id = R.string.error_print_receipt_failed)

    LaunchedEffect(Unit) {
        account = storageService.getAccount()
    }

    LaunchedEffect(transactionId) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val endpoint = "/api/transaction/$transactionId"
                val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                val gson = com.google.gson.Gson()
                val jsonString = gson.toJson(resultApi.data)
                transaction = gson.fromJson(jsonString, Transaction::class.java)
            } catch (e: Exception) {
                errorMessage = e.message ?: errorLoadingData
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (data.visuals.message.contains("thành công") || data.visuals.message.contains("successfully"))
                            customGreenColor
                        else
                            customRedColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(id = R.string.title_transaction_detail),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.content_desc_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    )
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )
            }
        },
        bottomBar = {
            val showSendAndPrint = transaction != null && transaction!!.showButtons()
            val allowCancel = account?.terminal?.systemConfig?.allowCancel == true

            if (showSendAndPrint || allowCancel) {
                Surface(
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (allowCancel) {
                            OutlinedButton(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = customRedColor
                                ),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    width = 1.dp,
                                    brush = SolidColor(customRedColor)
                                ),
                                enabled = !isCancelling
                            ) {
                                if (isCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = customRedColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.btn_cancel),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (showSendAndPrint) {
                            OutlinedButton(
                                onClick = { showEmailSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = customGreenColor
                                ),
                                enabled = isEnabled,
                                border = ButtonDefaults.outlinedButtonBorder(enabled = isEnabled).copy(
                                    width = 1.dp,
                                    brush = SolidColor(customGreenColor)
                                )
                            ) {
                                Text(
                                    text = stringResource(id = R.string.btn_send_bill),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isPrinting = true
                                        if (account == null) {
                                            snackbarHostState.showSnackbar(errorNoAccountInfo)
                                            isPrinting = false
                                            return@launch
                                        }

                                        if (!printerHelper.waitForReady(timeoutMs = 3000)) {
                                            snackbarHostState.showSnackbar(errorPrinterNotReady)
                                            isPrinting = false
                                            return@launch
                                        }

                                        val result = receiptPrinter.printReceipt(
                                            transaction = transaction!!,
                                            terminal = account!!.terminal
                                        )

                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar(successPrintReceipt)
                                        } else {
                                            snackbarHostState.showSnackbar(errorPrintReceiptFailed.format(result.exceptionOrNull()?.message ?: ""))
                                        }
                                        isPrinting = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customGreenColor
                                ),
                                enabled = !isPrinting
                            ) {
                                Text(
                                    text = stringResource(id = R.string.btn_print_receipt),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                .background(Color(0xFFF9FAFB))
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF16A34A))
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 14.sp
                        )
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

    if (showCancelDialog) {
        val successMessage = stringResource(id = R.string.success_cancel_transaction)
        val unknownError = stringResource(id = R.string.error_unknown)
        val failedMessage = stringResource(id = R.string.error_cancel_transaction_failed)
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(id = R.string.dialog_cancel_transaction_title)) },
            text = { Text(stringResource(id = R.string.dialog_cancel_transaction_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isCancelling = true
                            showCancelDialog = false
                            try {
                                val endpoint = "/api/transaction/cancel"
                                val body = mapOf("OrgRefNo" to transaction!!.transactionId)
                                apiService.post(endpoint, body)

                                snackbarHostState.showSnackbar(successMessage)
                                onBackPressed()
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: unknownError
                                snackbarHostState.showSnackbar(failedMessage.format(errorMsg))
                            } finally {
                                isCancelling = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customRedColor)
                ) {
                    Text(stringResource(id = R.string.btn_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(id = R.string.btn_dismiss))
                }
            }
        )
    }

    if (showEmailSheet && transaction != null) {
        val successMessage = stringResource(id = R.string.success_send_email)
        val failedMessage = stringResource(id = R.string.error_send_email_failed)
        SendEmailSheet(
            transactionId = transaction!!.transactionId,
            apiService = apiService,
            storageService = storageService,
            onDismiss = { showEmailSheet = false },
            onSuccess = {
                showEmailSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(successMessage)
                }
            },
            onError = { error ->
                showEmailSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(failedMessage.format(error))
                }
            }
        )
    }
}