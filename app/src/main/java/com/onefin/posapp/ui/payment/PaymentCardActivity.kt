package com.onefin.posapp.ui.payment

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.managers.helpers.PaymentTTSHelper
import com.onefin.posapp.core.models.ResultApi
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
import com.onefin.posapp.ui.payment.components.SignatureBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class PaymentCardActivity : BaseActivity() {

    private val gson = Gson()

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var printerHelper: PrinterHelper

    @Inject
    lateinit var receiptPrinter: ReceiptPrinter

    @Inject
    lateinit var cardProcessorManager: CardProcessorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                PaymentCardScreen(
                    apiService = apiService,
                    printerHelper = printerHelper,
                    receiptPrinter = receiptPrinter,
                    paymentAppRequest = requestData,
                    onCancel = { cancelTransaction() },
                    onSuccess = { saleResult -> onSuccess(saleResult, requestData) },
                )
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelTransaction()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cardProcessorManager.cancelPayment()
    }

    private fun cancelTransaction(errorMessage: String? = null) {
        cardProcessorManager.cancelPayment()
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

    private fun onSuccess(saleResult: SaleResultData, originalRequest: PaymentAppRequest) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest() ?: originalRequest
            storageService.clearExternalPaymentContext()

            val response = CardHelper.returnSaleResponse(saleResult, pendingRequest)
            val resultIntent = Intent().apply {
                putExtra(
                    ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                    gson.toJson(response)
                )
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            val response = CardHelper.returnSaleResponse(saleResult, originalRequest)
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
fun PaymentCardScreen(
    onCancel: () -> Unit,
    apiService: ApiService,
    onSuccess: (SaleResultData) -> Unit,
    paymentAppRequest: PaymentAppRequest,
    printerHelper: PrinterHelper,
    receiptPrinter: ReceiptPrinter,
) {
    var isPrinting by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Đang khởi tạo...") }
    var currentRequestSale by remember { mutableStateOf<RequestSale?>(null) }
    var paymentState by remember { mutableStateOf(PaymentState.INITIALIZING) }

    // 🔥 Lưu kết quả API và signature
    val activity = LocalContext.current as PaymentCardActivity
    val activityScope = CoroutineScope(Dispatchers.Main)
    var customerSignature by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSaleResult by remember { mutableStateOf<SaleResultData?>(null) }

    val ttsManager = remember { activity.ttsManager }
    val storageService = remember { activity.storageService }
    var retryTrigger by remember { mutableIntStateOf(0) }
    val cardProcessorManager = remember { activity.cardProcessorManager }

    val amount = paymentAppRequest.merchantRequestData?.amount ?: 0

    fun resetAndRetry() {
        Timber.tag("PaymentCard").d("🔄 Reset and retry")

        errorCode = null
        isPrinting = false
        showErrorDialog = false
        errorDialogMessage = ""
        pendingSaleResult = null
        customerSignature = null
        currentRequestSale = null
        showSignatureDialog = false
        statusMessage = "Đang khởi tạo..."
        paymentState = PaymentState.INITIALIZING
        cardProcessorManager.cancelPayment()
        retryTrigger++
    }

    fun startCardReading() {
        paymentState = PaymentState.WAITING_CARD
        statusMessage = "Vui lòng quẹt/chạm/cắm thẻ"

        cardProcessorManager.startPayment(
            paymentRequest = paymentAppRequest,
            onProcessingComplete = { result ->
                activityScope.launch {
                    when (result) {
                        is PaymentResult.Success -> {
                            val requestSale = result.requestSale

                            // init
                            paymentState = PaymentState.CARD_DETECTED
                            statusMessage = "Đã phát hiện thẻ"
                            currentRequestSale = requestSale
                            delay(1000)

                            // processing
                            paymentState = PaymentState.PROCESSING
                            statusMessage = "Đang xử lý giao dịch..."

                            // call api
                            activityScope.launch {
                                val result = processPayment(apiService, requestSale)
                                result.onSuccess { saleResultData ->
                                    if (saleResultData.status?.code == "00") {
                                        pendingSaleResult = saleResultData
                                        paymentState = PaymentState.WAITING_SIGNATURE
                                        statusMessage = "Vui lòng ký xác nhận"
                                        val ttsMessage = "Giao dịch thành công. Vui lòng ký xác nhận"
                                        ttsManager.speak(ttsMessage)
                                        showSignatureDialog = true
                                    } else {
                                        val apiErrorMsg = saleResultData.status?.message
                                            ?: "Giao dịch thất bại"
                                        errorCode = saleResultData.status?.code ?: "API_ERROR"
                                        errorDialogMessage = apiErrorMsg
                                        showErrorDialog = true
                                        ttsManager.speak("Giao dịch thất bại. $apiErrorMsg")
                                    }
                                }

                                result.onFailure { error ->
                                    val errorMessage = error.message ?: "Giao dịch thất bại. Vui lòng thử lại"
                                    showErrorDialog = true
                                    errorCode = "API_ERROR"
                                    errorDialogMessage = errorMessage
                                    ttsManager.speak("Giao dịch thất bại. $errorMessage")
                                }
                            }
                        }
                        is PaymentResult.Error -> {
                            showErrorDialog = true
                            errorCode = result.errorCode
                            errorDialogMessage = result.vietnameseMessage
                            ttsManager.speak(PaymentTTSHelper.getTTSMessage(result.type))
                        }
                    }
                }
            }
        )
    }

    LaunchedEffect(retryTrigger) {
        paymentState == PaymentState.PROCESSING
        cardProcessorManager.initialize { success, error ->
            if (success) {
                Timber.tag("Initialize").d("✅ System initialized successfully")
                startCardReading()
            } else {
                val error = PaymentResult.Error.from(
                    technicalMessage = error,
                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED
                )
                showErrorDialog = true
                errorCode = error.errorCode
                paymentState = PaymentState.ERROR
                statusMessage = error.vietnameseMessage
                errorDialogMessage = error.vietnameseMessage
                ttsManager.speak(PaymentTTSHelper.getTTSMessage(error.type))
            }
        }
    }

    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF3B82F6)
                    )
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
            ModernHeader(
                billNumber = paymentAppRequest.merchantRequestData?.billNumber,
                referenceId = paymentAppRequest.merchantRequestData?.referenceId
            )

            Spacer(modifier = Modifier.height(16.dp))

            AmountCard(amount = amount)

            Spacer(modifier = Modifier.height(24.dp))

            PaymentStatusCard(
                paymentState = paymentState,
                statusMessage = statusMessage,
                currentRequestSale = currentRequestSale,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            ActionButtons(
                paymentState = paymentState,
                isPrinting = isPrinting,
                onCancel = onCancel,
                onClose = if (paymentState == PaymentState.SUCCESS) {
                    {
                        // 🔥 Đóng mà không in
                        Timber.tag("PaymentCard").d("✅ Close without printing")

                        pendingSaleResult?.let { saleResult ->
                            val ttsMessage = PaymentTTSHelper.getSuccessTTSMessage(amount)
                            ttsManager.speak(ttsMessage)
                            onSuccess(saleResult)
                        }
                    }
                } else null,
                onPrint = if (paymentState == PaymentState.SUCCESS && customerSignature != null) {
                    {
                        // 🔥 In hóa đơn có chữ ký
                        activityScope.launch {
                            isPrinting = true
                            statusMessage = "Đang in hóa đơn..."
                            try {
                                val account = storageService.getAccount()
                                if (account == null) {
                                    Timber.tag("PaymentCard").e("❌ No account info")
                                    ttsManager.speak("Không có thông tin tài khoản")
                                    isPrinting = false
                                    return@launch
                                }

                                if (!printerHelper.waitForReady(timeoutMs = 3000)) {
                                    Timber.tag("PaymentCard").e("❌ Printer not ready")
                                    ttsManager.speak("Máy in chưa sẵn sàng")
                                    isPrinting = false
                                    return@launch
                                }

                                pendingSaleResult?.let { saleResult ->
                                    val transaction = saleResult.toTransaction()

                                    // 🔥 In hóa đơn
                                    receiptPrinter.printReceiptWithSignature(
                                        transaction = transaction,
                                        terminal = account.terminal,
                                        signatureBitmap = customerSignature
                                    )
                                    Timber.tag("PaymentCard").d("✅ Print completed")
                                    statusMessage = "In hóa đơn thành công"
                                    ttsManager.speak("In hóa đơn thành công")
                                    onSuccess(saleResult)
                                }

                            } catch (e: Exception) {
                                Timber.tag("PaymentCard").e(e, "❌ Print failed")
                                statusMessage = "In hóa đơn thất bại"
                                ttsManager.speak("In hóa đơn thất bại")
                            } finally {
                                isPrinting = false
                            }
                        }
                    }
                } else null
            )
        }

        // Error Dialog
        if (showErrorDialog) {
            ModernErrorDialog(
                message = errorDialogMessage,
                errorCode = errorCode,
                onRetry = {
                    resetAndRetry()
                },
                onCancel = {
                    showErrorDialog = false
                    onCancel()
                }
            )
        }

        // 🔥 Signature Dialog (BẮT BUỘC phải ký)
        if (showSignatureDialog) {
            SignatureBottomSheet(
                onConfirm = { signatureData ->
                    Timber.tag("PaymentCard").d("✅ Signature confirmed")
                    Timber.tag("PaymentCard").d("   Signature size: ${signatureData?.size ?: 0} bytes")

                    showSignatureDialog = false

                    // 🔥 Lưu signature
                    customerSignature = signatureData

                    paymentState = PaymentState.SUCCESS
                    statusMessage = "Đã ký xác nhận"

                    ttsManager.speak("Đã ký xác nhận thành công")

                    // 🔥 KHÔNG gọi onSuccess ở đây
                    // Chờ user bấm "In" hoặc "Đóng"
                }
            )
        }
    }
}

suspend fun processPayment(
    apiService: ApiService,
    requestSale: RequestSale
): Result<SaleResultData> {
    val gson = Gson()
    Timber.tag("Payment").d("Processing payment:")
    return try {
        val requestBody = mapOf(
            "data" to mapOf(
                "card" to mapOf(
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
                    "issuerName" to requestSale.data.card.issuerName
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
        val saleResultData = gson.fromJson(
            gson.toJson(resultApi.data),
            SaleResultData::class.java
        )

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