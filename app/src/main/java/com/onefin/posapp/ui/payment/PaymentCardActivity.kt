package com.onefin.posapp.ui.payment

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.R
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.managers.helpers.PaymentTTSHelper
import com.onefin.posapp.core.managers.helpers.PinInputCallback
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ActionButtons
import com.onefin.posapp.ui.payment.components.AmountCard
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.ModernHeader
import com.onefin.posapp.ui.payment.components.PaymentStatusCard
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
import com.onefin.posapp.ui.payment.components.SignatureBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PaymentCardActivity : BaseActivity() {

    private val gson = Gson()

    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var apiService: ApiService
    @Inject lateinit var paymentHelper: PaymentHelper
    @Inject lateinit var printerHelper: PrinterHelper
    @Inject lateinit var receiptPrinter: ReceiptPrinter
    @Inject lateinit var cardProcessorManager: CardProcessorManager
    @Inject lateinit var nfcPhoneReaderManager: NfcPhoneReaderManager

    private var deviceType: DeviceType = DeviceType.ANDROID_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceType = detectDeviceType()

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PaymentCardScreen(
                    deviceType = deviceType,
                    apiService = apiService,
                    printerHelper = printerHelper,
                    receiptPrinter = receiptPrinter,
                    paymentAppRequest = requestData,
                    onCancel = { cancelTransaction() },
                    onSuccess = { saleResult -> onSuccess(saleResult, requestData) },
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelTransaction()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.disableForegroundDispatch(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.enableForegroundDispatch(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
                cardProcessorManager.setPinInputCallback(null)
                cardProcessorManager.cancelPayment()
            }
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                nfcPhoneReaderManager.handleNfcIntent(tag)
            }
        }
    }

    private fun detectDeviceType(): DeviceType {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()
            val isSunmi = manufacturer.contains("sunmi") ||
                    model.contains("p2") || model.contains("v2") || model.contains("p1")
            if (isSunmi) DeviceType.SUNMI_P3 else DeviceType.ANDROID_PHONE
        } catch (e: Exception) {
            DeviceType.ANDROID_PHONE
        }
    }

    private fun cancelTransaction(errorMessage: String? = null) {
        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }

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

    private fun onSuccess(saleResult: SaleResultData, originalRequest: PaymentAppRequest) {
        val isExternalFlow = storageService.isExternalPaymentFlow()

        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            val requestToUse = pendingRequest ?: originalRequest
            val response = CardHelper.returnSaleResponse(saleResult, requestToUse)
            val resultIntent = paymentHelper.buildResultIntentSuccess(response)
            setResult(RESULT_OK, resultIntent)
            storageService.clearExternalPaymentContext()
        } else {
            val response = CardHelper.returnSaleResponse(saleResult, originalRequest)
            val resultIntent = paymentHelper.buildResultIntentSuccess(response)
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }
}

@Composable
fun PaymentCardScreen(
    onCancel: () -> Unit,
    deviceType: DeviceType,
    apiService: ApiService,
    printerHelper: PrinterHelper,
    receiptPrinter: ReceiptPrinter,
    onSuccess: (SaleResultData) -> Unit,
    paymentAppRequest: PaymentAppRequest,
) {
    val context = LocalContext.current
    val activityScope = CoroutineScope(Dispatchers.Main)
    val activity = context as PaymentCardActivity
    val ttsManager = remember { activity.ttsManager }
    val storageService = remember { activity.storageService }
    val cardProcessorManager = remember { activity.cardProcessorManager }
    val nfcPhoneReaderManager = remember { activity.nfcPhoneReaderManager }

    var isPrinting by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var showPinInputDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.payment_initializing)) }
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }
    var timeRemaining by remember { mutableIntStateOf(60) }
    var isCountdownActive by remember { mutableStateOf(false) }
    var customerSignature by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSaleResult by remember { mutableStateOf<SaleResultData?>(null) }
    var onPinCancelledCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onPinEnteredCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val amount = paymentAppRequest.merchantRequestData?.amount ?: 0
    val isMemberCard = paymentAppRequest.type.lowercase() == "member"

    val waitingCardMessage = when (deviceType) {
        DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> stringResource(R.string.payment_waiting_card_pos)
        DeviceType.ANDROID_PHONE -> stringResource(R.string.payment_waiting_card_phone)
    }

    LaunchedEffect(isCountdownActive, timeRemaining) {
        if (isCountdownActive && timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else if (isCountdownActive && timeRemaining == 0) {
            isCountdownActive = false
            when (deviceType) {
                DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
                DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
            }
            ttsManager.speak(context.getString(R.string.tts_transaction_timeout))
            delay(300)
            onCancel()
        }
    }

    fun resetAndRetry() {
        errorCode = null
        isPrinting = false
        showErrorDialog = false
        errorDialogMessage = ""
        pendingSaleResult = null
        customerSignature = null
        currentRequestSale = null
        showPinInputDialog = false
        showSignatureDialog = false
        statusMessage = context.getString(R.string.payment_initializing)
        paymentState = PaymentState.INITIALIZING
        timeRemaining = 60
        isCountdownActive = false

        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }
        retryTrigger++
    }

    fun handlePaymentResult(result: PaymentResult) {
        activityScope.launch {
            when (result) {
                is PaymentResult.Success -> {
                    isCountdownActive = false
                    val requestSale = result.requestSale

                    paymentState = PaymentState.CARD_DETECTED
                    statusMessage = context.getString(R.string.payment_card_detected)
                    currentRequestSale = requestSale
                    delay(1000)

                    paymentState = PaymentState.PROCESSING
                    statusMessage = context.getString(R.string.payment_processing)

                    activityScope.launch {
                        val result = processPayment(apiService, requestSale)
                        result.onSuccess { saleResultData ->
                            if (saleResultData.status?.code == "00") {
                                pendingSaleResult = saleResultData
                                paymentState = PaymentState.WAITING_SIGNATURE
                                statusMessage = context.getString(R.string.payment_waiting_signature)
                                val ttsMessage = context.getString(R.string.tts_transaction_success_signature)
                                ttsManager.speak(ttsMessage)
                                showSignatureDialog = true
                            } else {
                                val apiErrorMsg = saleResultData.status?.message
                                    ?: context.getString(R.string.payment_failed)
                                errorCode = saleResultData.status?.code ?: "API_ERROR"
                                errorDialogMessage = apiErrorMsg
                                showErrorDialog = true
                                ttsManager.speak(context.getString(R.string.tts_transaction_failed, apiErrorMsg))
                            }
                        }

                        result.onFailure { error ->
                            val errorMessage = error.message ?: context.getString(R.string.error_transaction_failed_retry)
                            showErrorDialog = true
                            errorCode = "API_ERROR"
                            errorDialogMessage = errorMessage
                            ttsManager.speak(context.getString(R.string.tts_transaction_failed, errorMessage))
                        }
                    }
                }
                is PaymentResult.Error -> {
                    showErrorDialog = true
                    isCountdownActive = false
                    errorCode = result.errorCode
                    errorDialogMessage = result.vietnameseMessage
                    ttsManager.speak(PaymentTTSHelper.getTTSMessage(result.type))
                }
            }
        }
    }

    fun startCardReading() {
        paymentState = PaymentState.WAITING_CARD
        statusMessage = waitingCardMessage
        timeRemaining = 60
        isCountdownActive = true

        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
                cardProcessorManager.startPayment(
                    paymentRequest = paymentAppRequest,
                    onProcessingComplete = { result -> handlePaymentResult(result) }
                )
            }
            DeviceType.ANDROID_PHONE -> {
                nfcPhoneReaderManager.startPayment(
                    paymentRequest = paymentAppRequest,
                    onProcessingComplete = { result -> handlePaymentResult(result) }
                )
            }
        }
    }

    LaunchedEffect(retryTrigger) {
        paymentState = PaymentState.INITIALIZING
        val pinCallback = object : PinInputCallback {
            override fun requestPinInput(onPinEntered: (String) -> Unit, onCancelled: () -> Unit) {
                activityScope.launch(Dispatchers.Main) {
                    onPinCancelledCallback = onCancelled
                    onPinEnteredCallback = onPinEntered
                    showPinInputDialog = true
                }
            }
        }

        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
                cardProcessorManager.setPinInputCallback(pinCallback)
                cardProcessorManager.initialize { success, error ->
                    if (success) {
                        startCardReading()
                    } else {
                        val paymentError = PaymentResult.Error.from(
                            technicalMessage = error,
                            errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED
                        )
                        showErrorDialog = true
                        errorCode = paymentError.errorCode
                        paymentState = PaymentState.ERROR
                        statusMessage = paymentError.vietnameseMessage
                        errorDialogMessage = paymentError.vietnameseMessage
                        ttsManager.speak(PaymentTTSHelper.getTTSMessage(paymentError.type))
                    }
                }
            }
            DeviceType.ANDROID_PHONE -> {
                nfcPhoneReaderManager.initialize { success, error ->
                    if (success) {
                        startCardReading()
                    } else {
                        val paymentError = PaymentResult.Error.from(
                            technicalMessage = error,
                            errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED
                        )
                        showErrorDialog = true
                        errorCode = paymentError.errorCode
                        paymentState = PaymentState.ERROR
                        statusMessage = paymentError.vietnameseMessage
                        errorDialogMessage = paymentError.vietnameseMessage
                        ttsManager.speak(PaymentTTSHelper.getTTSMessage(paymentError.type))
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (deviceType == DeviceType.SUNMI_P2 || deviceType == DeviceType.SUNMI_P3) {
                ModernHeader(
                    billNumber = paymentAppRequest.merchantRequestData?.billNumber,
                    referenceId = paymentAppRequest.merchantRequestData?.referenceId
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AmountCard(amount = amount, isMemberCard = isMemberCard)

            Spacer(modifier = Modifier.height(24.dp))

            if (paymentState == PaymentState.INITIALIZING) {
                InitializingCard(modifier = Modifier.weight(1f))
            } else {
                PaymentStatusCard(
                    deviceType = deviceType,
                    paymentState = paymentState,
                    statusMessage = statusMessage,
                    currentRequestSale = currentRequestSale,
                    timeRemaining = if (isCountdownActive) timeRemaining else null,
                    onAutoClose = if (paymentState == PaymentState.SUCCESS) {
                        {
                            pendingSaleResult?.let { saleResult ->
                                val ttsMessage = PaymentTTSHelper.getSuccessTTSMessage(amount)
                                ttsManager.speak(ttsMessage)
                                onSuccess(saleResult)
                            }
                        }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                onCancel = onCancel,
                isPrinting = isPrinting,
                paymentState = paymentState,
                onClose = if (paymentState == PaymentState.SUCCESS) {
                    {
                        pendingSaleResult?.let { saleResult ->
                            val ttsMessage = PaymentTTSHelper.getSuccessTTSMessage(amount)
                            ttsManager.speak(ttsMessage)
                            onSuccess(saleResult)
                        }
                    }
                } else null,
                onPrint = if (paymentState == PaymentState.SUCCESS && customerSignature != null) {
                    {
                        activityScope.launch {
                            isPrinting = true
                            statusMessage = context.getString(R.string.payment_printing)
                            try {
                                val account = storageService.getAccount()
                                if (account == null) {
                                    ttsManager.speak(context.getString(R.string.payment_no_account))
                                    isPrinting = false
                                    return@launch
                                }

                                if (deviceType == DeviceType.SUNMI_P2 || deviceType == DeviceType.SUNMI_P3) {
                                    if (!printerHelper.waitForReady(timeoutMs = 3000)) {
                                        ttsManager.speak(context.getString(R.string.payment_printer_not_ready))
                                        isPrinting = false
                                        return@launch
                                    }
                                } else {
                                    ttsManager.speak(context.getString(R.string.payment_printer_not_supported))
                                    isPrinting = false
                                    return@launch
                                }

                                pendingSaleResult?.let { saleResult ->
                                    val transaction = saleResult.toTransaction()
                                    receiptPrinter.printReceiptWithSignature(
                                        transaction = transaction,
                                        terminal = account.terminal,
                                        signatureBitmap = customerSignature
                                    )
                                    statusMessage = context.getString(R.string.payment_print_success)
                                    ttsManager.speak(context.getString(R.string.payment_print_success))
                                    onSuccess(saleResult)
                                }
                            } catch (e: Exception) {
                                statusMessage = context.getString(R.string.payment_print_failed)
                                ttsManager.speak(context.getString(R.string.payment_print_failed))
                            } finally {
                                isPrinting = false
                            }
                        }
                    }
                } else null
            )
        }

        if (showErrorDialog) {
            ModernErrorDialog(
                message = errorDialogMessage,
                errorCode = errorCode,
                onRetry = { resetAndRetry() },
                onCancel = {
                    showErrorDialog = false
                    onCancel()
                }
            )
        }

        if (showPinInputDialog) {
            PinInputBottomSheet(
                onCancel = {
                    showPinInputDialog = false
                    onPinCancelledCallback?.invoke()
                    onPinEnteredCallback = null
                    onPinCancelledCallback = null
                },
                onConfirm = { pin ->
                    showPinInputDialog = false
                    onPinEnteredCallback?.invoke(pin)
                    onPinEnteredCallback = null
                    onPinCancelledCallback = null
                },
            )
        }

        if (showSignatureDialog) {
            SignatureBottomSheet(
                onConfirm = { signatureData ->
                    showSignatureDialog = false
                    customerSignature = signatureData
                    paymentState = PaymentState.SUCCESS
                    statusMessage = context.getString(R.string.payment_signature_confirmed)
                }
            )
        }
    }
}

@Composable
private fun InitializingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = Color(0xFF3B82F6),
                strokeWidth = 6.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.payment_initializing),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E3A8A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Đang khởi tạo thiết bị...",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

suspend fun processPayment(apiService: ApiService, requestSale: RequestSale): Result<SaleResultData> {
    val gson = Gson()
    return try {
        val requestBody = mapOf(
            "data" to mapOf(
                "card" to mapOf(
                    "ksn" to requestSale.data.card.ksn,
                    "pin" to requestSale.data.card.pin,
                    "type" to requestSale.data.card.type,
                    "newPin" to requestSale.data.card.newPin,
                    "track1" to requestSale.data.card.track1,
                    "track2" to requestSale.data.card.track2,
                    "track3" to requestSale.data.card.track3,
                    "emvData" to requestSale.data.card.emvData,
                    "clearPan" to requestSale.data.card.clearPan,
                    "expiryDate" to requestSale.data.card.expiryDate,
                    "holderName" to requestSale.data.card.holderName,
                    "issuerName" to requestSale.data.card.issuerName,
                    "mode" to CardHelper.getCardMode(requestSale.data.card.mode),
                ),
                "device" to mapOf(
                    "posEntryMode" to requestSale.data.device.posEntryMode,
                    "posConditionCode" to requestSale.data.device.posConditionCode
                ),
                "payment" to mapOf(
                    "currency" to requestSale.data.payment.currency,
                    "transAmount" to requestSale.data.payment.transAmount
                ),
                "bank" to requestSale.data.bank,
            ),
            "requestData" to mapOf(
                "type" to requestSale.requestData.type,
                "action" to requestSale.requestData.action,
                "merchant_request_data" to gson.toJson(requestSale.requestData.merchantRequestData)
            ),
            "requestId" to requestSale.requestId,
        )

        val resultApi = apiService.post("/api/card/sale", requestBody) as ResultApi<*>
        val saleResultData = gson.fromJson(gson.toJson(resultApi.data), SaleResultData::class.java)

        if (saleResultData != null) {
            if (saleResultData.status?.code == "00") {
                Result.success(saleResultData)
            } else {
                Result.failure(Exception(saleResultData.status?.message))
            }
        } else {
            Result.failure(Exception("API returned null data"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}