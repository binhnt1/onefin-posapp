package com.onefin.posapp.ui.payment

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.onefin.posapp.core.utils.EmvUtil
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.payment.components.ActionButtons
import com.onefin.posapp.ui.payment.components.CardInfoCard
import com.onefin.posapp.ui.payment.components.ModernBillCard
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
import com.onefin.posapp.ui.payment.components.PulseStatusCard
import com.onefin.posapp.ui.payment.components.SignatureBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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
                    onSuccess = { saleResult -> returnSuccess(saleResult, requestData) },
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

    private fun returnSuccess(saleResult: SaleResultData, originalRequest: PaymentAppRequest) {
        val pendingRequest = storageService.getPendingPaymentRequest() ?: originalRequest
        val response = CardHelper.returnSaleResponse(saleResult, pendingRequest)
        val resultIntent = paymentHelper.buildResultIntentSuccess(response)
        setResult(RESULT_OK, resultIntent)
        storageService.clearExternalPaymentContext()
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
    val activityScope = rememberCoroutineScope()
    val activity = context as PaymentCardActivity

    // 🔥 KHÔNG ACCESS MANAGERS NGAY!
    val ttsManager = remember { activity.ttsManager }
    val storageService = remember { activity.storageService }

    // 🔥 LAZY - chỉ access khi cần
    var cardProcessorManager by remember { mutableStateOf<CardProcessorManager?>(null) }
    var nfcPhoneReaderManager by remember { mutableStateOf<NfcPhoneReaderManager?>(null) }

    var isPrinting by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(true) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var showPinInputDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.payment_initializing)) }
    var detectedCardInfo by remember { mutableStateOf<RequestSale?>(null) }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }
    var timeRemaining by remember { mutableIntStateOf(60) }
    var isCountdownActive by remember { mutableStateOf(false) }
    var successCountdown by remember { mutableIntStateOf(10) }
    var isSuccessCountdownActive by remember { mutableStateOf(false) }
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

    fun resetAndRetry() {
        errorCode = null
        isPrinting = false
        timeRemaining = 60
        isInitializing = true
        successCountdown = 10
        showErrorDialog = false
        errorDialogMessage = ""
        detectedCardInfo = null
        pendingSaleResult = null
        customerSignature = null
        isCountdownActive = false
        showPinInputDialog = false
        showSignatureDialog = false
        isSuccessCountdownActive = false
        statusMessage = context.getString(R.string.payment_initializing)
        paymentState = PaymentState.INITIALIZING

        when (deviceType) {
            DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> cardProcessorManager?.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager?.cancelPayment()
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
                    detectedCardInfo = requestSale // Save card info persistently
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
                cardProcessorManager?.startPayment(
                    paymentRequest = paymentAppRequest,
                    onProcessingComplete = { result -> handlePaymentResult(result) }
                )
            }
            DeviceType.ANDROID_PHONE -> {
                nfcPhoneReaderManager?.startPayment(
                    paymentRequest = paymentAppRequest,
                    onProcessingComplete = { result -> handlePaymentResult(result) }
                )
            }
        }
    }

    LaunchedEffect(isCountdownActive) {
        if (isCountdownActive) {
            while (timeRemaining > 0 && isCountdownActive) {
                delay(1000)
                if (isCountdownActive) {
                    timeRemaining--
                }
            }
            if (timeRemaining <= 0 && isCountdownActive) {
                isCountdownActive = false
                // Timeout error
                showErrorDialog = true
                errorCode = "TIMEOUT"
                errorDialogMessage = "Hết thời gian giao dịch. Vui lòng thử lại."
                paymentState = PaymentState.ERROR
                statusMessage = "Hết thời gian"
            }
        }
    }

    LaunchedEffect(isSuccessCountdownActive) {
        if (isSuccessCountdownActive) {
            successCountdown = 10
            while (successCountdown > 0 && isSuccessCountdownActive) {
                delay(1000)
                if (isSuccessCountdownActive) {
                    successCountdown--
                }
            }
            if (successCountdown <= 0 && isSuccessCountdownActive) {
                // Tự động đóng và trả kết quả
                pendingSaleResult?.let { saleResult ->
                    onSuccess(saleResult)
                }
            }
        }
    }

    LaunchedEffect(retryTrigger) {
        isInitializing = true
        paymentState = PaymentState.INITIALIZING
        statusMessage = context.getString(R.string.payment_initializing)

        yield() // Let compose render the current state
        delay(50) // Additional small delay to ensure frame is committed

        // Now access managers and initialize on IO thread
        withContext(Dispatchers.IO) {
            when (deviceType) {
                DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
                    cardProcessorManager = activity.cardProcessorManager
                }
                DeviceType.ANDROID_PHONE -> {
                    nfcPhoneReaderManager = activity.nfcPhoneReaderManager
                }
            }
        }

        // Initialize SDK asynchronously
        try {
            val minDisplayTime = 800L // Minimum time to show loading animation
            val startTime = System.currentTimeMillis()

            val initResult = suspendCancellableCoroutine { continuation ->
                val pinCallback = object : PinInputCallback {
                    override fun requestPinInput(
                        onPinEntered: (String) -> Unit,
                        onCancelled: () -> Unit
                    ) {
                        activityScope.launch(Dispatchers.Main) {
                            onPinCancelledCallback = onCancelled
                            onPinEnteredCallback = onPinEntered
                            showPinInputDialog = true
                        }
                    }
                }

                when (deviceType) {
                    DeviceType.SUNMI_P2, DeviceType.SUNMI_P3 -> {
                        cardProcessorManager?.setPinInputCallback(pinCallback)
                        cardProcessorManager?.initialize { success, error ->
                            if (continuation.isActive) {
                                continuation.resume(Pair(success, error)) {}
                            }
                        }
                    }
                    DeviceType.ANDROID_PHONE -> {
                        nfcPhoneReaderManager?.initialize { success, error ->
                            if (continuation.isActive) {
                                continuation.resume(Pair(success, error)) {}
                            }
                        }
                    }
                }
            }

            // Ensure minimum display time
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < minDisplayTime) {
                delay(minDisplayTime - elapsed)
            }

            // Handle result
            val (success, error) = initResult
            isInitializing = false

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

        } catch (e: Exception) {
            isInitializing = false
            val paymentError = PaymentResult.Error.from(
                technicalMessage = e.message,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ModernBillCard(
                amount = amount,
                isMemberCard = isMemberCard,
                billNumber = paymentAppRequest.merchantRequestData?.billNumber,
                referenceId = paymentAppRequest.merchantRequestData?.referenceId
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isInitializing) {
                InitializingCard(modifier = Modifier.weight(1f))
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    PulseStatusCard(
                        isMemberCard = isMemberCard,
                        paymentState = paymentState,
                        statusMessage = statusMessage,
                        modifier = Modifier.weight(1f),
                        timeRemaining = when {
                            isSuccessCountdownActive -> successCountdown
                            isCountdownActive -> timeRemaining
                            else -> null
                        }
                    )
                    if (detectedCardInfo != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CardInfoCard(requestSale = detectedCardInfo!!)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                onCancel = onCancel,
                isPrinting = isPrinting,
                paymentState = paymentState,
                onClose = if (paymentState == PaymentState.SUCCESS) {
                    {
                        pendingSaleResult?.let { saleResult ->
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
                    isSuccessCountdownActive = true
                }
            )
        }
    }
}

@Composable
private fun InitializingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = Color(0xFF60A5FA),
                strokeWidth = 6.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Đang khởi tạo thiết bị",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vui lòng chờ...",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

suspend fun processPayment(apiService: ApiService, requestSale: RequestSale): Result<SaleResultData> {
    val gson = Gson()
    requestSale.data.card = EmvUtil.removePaddingCard(requestSale.data.card)
    return try {
        val requestBody = mapOf(
            "data" to mapOf(
                "card" to mapOf(
                    "tc" to requestSale.data.card.tc,
                    "aid" to requestSale.data.card.aid,
                    "ksn" to requestSale.data.card.ksn,
                    "pin" to requestSale.data.card.pin,
                    "type" to requestSale.data.card.type,
                    "mode" to requestSale.data.card.mode,
                    "newPin" to requestSale.data.card.newPin,
                    "track1" to requestSale.data.card.track1,
                    "track2" to requestSale.data.card.track2,
                    "track3" to requestSale.data.card.track3,
                    "emvData" to requestSale.data.card.emvData,
                    "clearPan" to requestSale.data.card.clearPan,
                    "expiryDate" to requestSale.data.card.expiryDate,
                    "holderName" to requestSale.data.card.holderName,
                    "issuerName" to requestSale.data.card.issuerName,
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
        if (resultApi.isSuccess()) {
            val saleResultData = gson.fromJson(gson.toJson(resultApi.data), SaleResultData::class.java)
            if (saleResultData != null) {
                if (saleResultData.status?.code == "00") {
                    Result.success(saleResultData)
                } else {
                    Result.failure(Exception(saleResultData.status?.message))
                }
            } else Result.failure(Exception("Dữ liệu trả về không hợp lệ"))
        } else Result.failure(Exception(resultApi.description))
    } catch (e: Exception) {
        Result.failure(e)
    }
}