package com.onefin.posapp.ui.payment

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.onefin.posapp.ui.payment.components.ModernErrorDialog
import com.onefin.posapp.ui.payment.components.PinInputBottomSheet
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
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var detectedCardInfo by remember { mutableStateOf<RequestSale?>(null) }
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

    fun resetAndRetry() {
        errorCode = null
        isPrinting = false
        timeRemaining = 60
        isInitializing = true
        showErrorDialog = false
        errorDialogMessage = ""
        pendingSaleResult = null
        customerSignature = null
        isCountdownActive = false
        currentRequestSale = null
        detectedCardInfo = null
        showPinInputDialog = false
        showSignatureDialog = false
        paymentState = PaymentState.INITIALIZING
        statusMessage = context.getString(R.string.payment_initializing)

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
                    currentRequestSale = requestSale
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

    LaunchedEffect(retryTrigger) {
        isInitializing = true
        paymentState = PaymentState.INITIALIZING
        statusMessage = context.getString(R.string.payment_initializing)

        // Give UI a chance to render the first frame with loading indicator
        // This ensures the CircularProgressIndicator is drawn before we do any heavy work
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
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
                        paymentState = paymentState,
                        statusMessage = statusMessage,
                        timeRemaining = if (isCountdownActive) timeRemaining else null,
                        currentRequestSale = currentRequestSale,
                        modifier = Modifier.weight(1f)
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
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
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vui lòng chờ...",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ModernBillCard(
    amount: Long,
    isMemberCard: Boolean,
    billNumber: String?,
    referenceId: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Onefin Logo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = Color(0xFF3B82F6),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .height(24.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${amount.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")} đ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (billNumber != null) {
                        Text(
                            text = "Bill: $billNumber",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (referenceId != null) {
                        Text(
                            text = "• Ref: $referenceId",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveAnimation(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Card icon
        Box(
            modifier = Modifier
                .size(size * 0.5f)
                .background(
                    color = Color(0xFF3B82F6),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💳",
                fontSize = (size.value * 0.25f).sp
            )
        }

        // Wave layers
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = this.size.height / 2

            // Wave 1
            drawPath(
                path = createWavePath(this.size, wave1, centerY + 40.dp.toPx()),
                color = Color(0xFF60A5FA).copy(alpha = 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )

            // Wave 2
            drawPath(
                path = createWavePath(this.size, wave2, centerY + 50.dp.toPx()),
                color = Color(0xFF60A5FA).copy(alpha = 0.2f),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )

            // Wave 3
            drawPath(
                path = createWavePath(this.size, wave3, centerY + 60.dp.toPx()),
                color = Color(0xFF60A5FA).copy(alpha = 0.1f),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
}

private fun createWavePath(size: androidx.compose.ui.geometry.Size, offset: Float, baseY: Float): androidx.compose.ui.graphics.Path {
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, baseY + offset)

        val waveLength = size.width / 3
        for (i in 0..3) {
            val x = i * waveLength
            cubicTo(
                x + waveLength * 0.25f, baseY + offset - 10f,
                x + waveLength * 0.75f, baseY + offset + 10f,
                x + waveLength, baseY + offset
            )
        }

        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
}

@Composable
private fun CardInfoCard(
    requestSale: RequestSale,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Card icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = Color(0xFF10B981),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💳",
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Card type
                val cardType = requestSale.data.card.type?.uppercase() ?: "CARD"
                Text(
                    text = cardType,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Card number
                val cardNumber = requestSale.data.card.clearPan
                Text(
                    text = formatCardNumber(cardNumber),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                val holderName = requestSale.data.card.holderName
                val expiryDate = requestSale.data.card.expiryDate
                if ((holderName != null && holderName.isNotBlank()) || expiryDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Holder name
                        if (holderName != null && holderName.isNotBlank()) {
                            Text(
                                text = holderName,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        // Separator (chỉ hiển thị khi cả 2 đều có)
                        if (holderName != null && holderName.isNotBlank() && expiryDate.isNotBlank()) {
                            Text(
                                text = "-",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Expiry date
                        if (expiryDate.isNotBlank()) {
                            Text(
                                text = "HSD: ${formatExpiryDate(expiryDate)}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoDisplay(
    requestSale: RequestSale,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFF10B981),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = 40.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card type
        val cardType = requestSale.data.card.type?.uppercase() ?: "CARD"
        Text(
            text = cardType,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF60A5FA)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Card number
        val cardNumber = requestSale.data.card.clearPan
        Text(
            text = formatCardNumber(cardNumber),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Holder name
        val holderName = requestSale.data.card.holderName
        if (holderName != null && holderName.isNotBlank()) {
            Text(
                text = holderName,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Expiry date
        val expiryDate = requestSale.data.card.expiryDate
        if (expiryDate.isNotBlank()) {
            Text(
                text = "Hết hạn: ${formatExpiryDate(expiryDate)}",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatCardNumber(cardNumber: String): String {
    return if (cardNumber.length > 4) {
        val masked = "**** **** **** ${cardNumber.takeLast(4)}"
        masked
    } else {
        cardNumber
    }
}

private fun formatExpiryDate(expiryDate: String): String {
    return if (expiryDate.length >= 4) {
        "${expiryDate.substring(0, 2)}/${expiryDate.substring(2)}"
    } else {
        expiryDate
    }
}

@Composable
private fun PulseAnimation(
    icon: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse circles
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(size * (0.6f + index * 0.2f) * scale)
                    .background(
                        color = Color(0xFF3B82F6).copy(alpha = alpha / (index + 1)),
                        shape = CircleShape
                    )
            )
        }

        // Center icon
        Box(
            modifier = Modifier
                .size(size * 0.4f)
                .background(
                    color = Color(0xFF3B82F6),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = (size.value * 0.2f).sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PulseStatusCard(
    paymentState: PaymentState,
    statusMessage: String,
    timeRemaining: Int?,
    currentRequestSale: RequestSale?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (paymentState) {
                PaymentState.WAITING_CARD -> {
                    WaveAnimation(size = 120.dp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                PaymentState.CARD_DETECTED -> {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            fontSize = 40.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        textAlign = TextAlign.Center
                    )
                }
                PaymentState.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFF60A5FA),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                PaymentState.SUCCESS -> {
                    SuccessAnimation(size = 120.dp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    WaveAnimation(size = 120.dp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (timeRemaining != null && timeRemaining > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tự động đóng sau ${timeRemaining}s",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            StepIndicators(paymentState = paymentState)
        }
    }
}

@Composable
private fun RotatingAnimation(
    icon: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Rotating circles
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(size * (0.6f + index * 0.2f))
                    .graphicsLayer(rotationZ = rotation + index * 120f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = Color(0xFF3B82F6).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        // Center icon
        Box(
            modifier = Modifier
                .size(size * 0.4f)
                .background(
                    color = Color(0xFF3B82F6),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = (size.value * 0.2f).sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SuccessAnimation(
    size: Dp,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Success circle
        Box(
            modifier = Modifier
                .size(size * 0.8f * scale.value)
                .background(
                    color = Color(0xFF10B981),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = (size.value * 0.4f).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StepIndicators(
    paymentState: PaymentState,
    modifier: Modifier = Modifier
) {
    val currentStep = when (paymentState) {
        PaymentState.WAITING_CARD, PaymentState.INITIALIZING -> 0
        PaymentState.CARD_DETECTED, PaymentState.PROCESSING -> 1
        PaymentState.SUCCESS, PaymentState.WAITING_SIGNATURE -> 2
        else -> 0
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (index <= currentStep) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )

            if (index < 2) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            color = if (index < currentStep) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
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