package com.onefin.posapp.core.managers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.managers.helpers.BaseCardProcessor
import com.onefin.posapp.core.managers.helpers.ChipCardProcessor
import com.onefin.posapp.core.managers.helpers.MagCardProcessor
import com.onefin.posapp.core.managers.helpers.MifareCardProcessor
import com.onefin.posapp.core.managers.helpers.NfcCardProcessor
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.NfcConfigResponse
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.EmvUtil
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sunmi.paylib.SunmiPayKernel
import com.onefin.posapp.core.managers.helpers.PinInputCallback

@Singleton
class CardProcessorManager(
    private val context: Context,
    private val apiService: ApiService,
    private val storageService: StorageService
) {
    private var emvOpt: EMVOptV2? = null
    private var pinPadOpt: PinPadOptV2? = null
    private var securityOpt: SecurityOptV2? = null
    private var readCardOpt: ReadCardOptV2? = null
    private var sunmiPayKernel: SunmiPayKernel? = null
    private var pinInputCallback: PinInputCallback? = null

    private val terminal: Terminal? by lazy {
        storageService.getAccount()?.terminal
    }

    private var isProcessing = false
    private var isSdkConnected = false
    private var isKernelInitialized = false
    private var currentProcessor: BaseCardProcessor? = null

    private var pendingAmount: String? = null
    private var pendingRequest: PaymentAppRequest? = null
    private var allowedCardTypes: Set<AidlConstants.CardType>? = null
    private lateinit var processingComplete: ((PaymentResult) -> Unit)

    fun initialize(onComplete: (Boolean, String?) -> Unit) {
        if (isKernelInitialized) {
            onComplete(true, null)
            return
        }

        // Run initialization on IO thread to prevent blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                connectSdk(onComplete)
            } catch (e: Exception) {
                val error = "Initialize failed: ${e.message}"
                onComplete(false, error)
            }
        }
    }

    fun startPayment(
        paymentRequest: PaymentAppRequest,
        onProcessingComplete: (PaymentResult) -> Unit,
        cardTypes: List<AidlConstants.CardType>? = null,
    ) {
        processingComplete = onProcessingComplete
        if (!isKernelInitialized || !isSdkConnected) {
            onProcessingComplete(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "CardProcessorManager not initialized. Call initialize() first."
                )
            )
            return
        }
        if (emvOpt == null || readCardOpt == null || pinPadOpt == null) {
            onProcessingComplete(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "SDK dependencies not available"
                )
            )
            return
        }

        if (isProcessing) {
            cancelPayment()
        }

        isProcessing = true
        pendingRequest = paymentRequest
        pendingAmount = paymentRequest.merchantRequestData?.amount?.toString()

        // Determine allowed card types based on payment type
        allowedCardTypes = if (cardTypes.isNullOrEmpty()) {
            when (paymentRequest.type.lowercase()) {
                "card" -> setOf(
                    AidlConstants.CardType.IC,
                    AidlConstants.CardType.NFC,
                    AidlConstants.CardType.MAGNETIC
                )
                "member" -> setOf(
                    AidlConstants.CardType.MIFARE,
                    AidlConstants.CardType.MAGNETIC
                )
                else -> setOf(
                    AidlConstants.CardType.IC,
                    AidlConstants.CardType.NFC,
                    AidlConstants.CardType.MAGNETIC
                )
            }
        } else {
            cardTypes.toSet()
        }

        // Check if MIFARE card type is allowed
        CoroutineScope(Dispatchers.IO).launch {
            if (allowedCardTypes?.contains(AidlConstants.CardType.MIFARE) == true) {
                val nfcConfig = loadNfcConfig()
                if (nfcConfig == null) {
                    isProcessing = false
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.SDK_INIT_FAILED_MIFARE,
                            "Failed to load NFC configuration"
                        )
                    )
                    return@launch
                }

                // Check transaction limit
                val transactionAmount = paymentRequest.merchantRequestData?.amount ?: 0L
                if (nfcConfig.exceedsLimit(transactionAmount)) {
                    val limitAmount = nfcConfig.getTransactionLimitAmount()
                    isProcessing = false
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.AMOUNT_EXCEEDS_LIMIT,
                            "Transaction amount exceeds NFC card limit (${limitAmount} VND)"
                        )
                    )
                    return@launch
                }
            }
        }

        // Start card detection
        try {
            val cardTypeMask = calculateCardTypeMask(allowedCardTypes!!)
            readCardOpt!!.checkCard(cardTypeMask, createAutoDetectCallback(), 60)
        } catch (e: Exception) {
            isProcessing = false
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Failed to start card detection: ${e.message}"
                )
            )
        }
    }

    fun destroy() {
        try {
            cancelPayment()
            emvOpt = null
            pinPadOpt = null
            readCardOpt = null
            sunmiPayKernel?.destroyPaySDK()
            sunmiPayKernel = null
            isSdkConnected = false
            isKernelInitialized = false
        } catch (e: Exception) {
        }
    }

    fun cancelPayment() {
        try {
            isProcessing = false
            currentProcessor?.cancelProcessing()
            currentProcessor = null
            readCardOpt?.cancelCheckCard()
            emvOpt?.abortTransactProcess()

            pendingAmount = null
            pendingRequest = null
            allowedCardTypes = null
        } catch (e: Exception) {
        }
    }

    fun setPinInputCallback(callback: PinInputCallback?) {
        this.pinInputCallback = callback
    }
    private fun initializeKernelData(): Boolean {
        if (emvOpt == null) {
            return false
        }
        if (terminal == null) {
            return false
        }
        if (securityOpt == null) {
            return false
        }
        return try {
            // Step 1: Inject AIDs
            EmvUtil.injectAids(context, emvOpt!!)

            // Step 2: Inject CAPKs
            EmvUtil.injectCapks(context, emvOpt!!)

            // Step 3: Set EMV TLVs
            EmvUtil.setEmvTlvs(context, emvOpt!!, terminal!!)

            // Step 4: Set Terminal Parameters
            EmvUtil.setTerminalParam(emvOpt!!, terminal!!)

            // Step 5: Inject Keys (CRITICAL)
            if (!EmvUtil.injectKeys(securityOpt!!, terminal!!)) {
                return false
            }

            // Step 6: Set additional TLV (optional)
            try {
                emvOpt!!.setTlv(
                    AidlConstants.EMV.TLVOpCode.OP_NORMAL,
                    "9F40",
                    "6000F0A001"
                )
            } catch (_: Exception) {
            }
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    private fun handleError(error: PaymentResult.Error) {
        isProcessing = false
        pendingAmount = null
        pendingRequest = null
        allowedCardTypes = null
        currentProcessor = null
        processingComplete(error)
    }
    private suspend fun loadNfcConfig(): NfcConfigResponse? {
        return try {
            // Step 1: Try get from cache
            val cachedConfig = storageService.getNfcConfig()
            if (cachedConfig != null) {
                return cachedConfig
            }

            // Step 2: Cache miss or expired, fetch from API
            val endpoint = "/api/card/nfcConfig"
            val driver = pendingRequest?.merchantRequestData?.mid ?: ""
            val employee = pendingRequest?.merchantRequestData?.tid ?: ""
            val requestBody = mapOf(
                "mid" to driver,
                "tid" to employee,
            )
            val resultApi = apiService.post(endpoint, requestBody) as ResultApi<*>
            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(resultApi.data)
            val config = gson.fromJson(jsonString, NfcConfigResponse::class.java)

            // Step 3: Save to cache
            if (config != null && config.nfckey.isNotEmpty()) {
                storageService.saveNfcConfig(config)
                config
            } else null
        } catch (_: Exception) {
            null
        }
    }
    private fun createAutoDetectCallback(): CheckCardCallbackV2 {
        return object : CheckCardCallbackV2.Stub() {
            override fun findICCard(atr: String?) {}
            override fun findICCardEx(info: Bundle?) {
                if (info != null && allowedCardTypes?.contains(AidlConstants.CardType.IC) == true) {
                    onCardDetected(AidlConstants.CardType.IC, info)
                }
            }

            override fun findRFCard(uuid: String?) {}
            override fun findRFCardEx(info: Bundle?) {
                if (info != null && allowedCardTypes?.contains(AidlConstants.CardType.NFC) == true) {
                    onCardDetected(AidlConstants.CardType.NFC, info)
                }
                if (info != null && allowedCardTypes?.contains(AidlConstants.CardType.MIFARE) == true) {
                    onCardDetected(AidlConstants.CardType.MIFARE, info)
                }
            }

            override fun findMagCard(info: Bundle?) {
                if (info != null && allowedCardTypes?.contains(AidlConstants.CardType.MAGNETIC) == true) {
                    onCardDetected(AidlConstants.CardType.MAGNETIC, info)
                }
            }

            override fun onError(code: Int, message: String?) {}
            override fun onErrorEx(info: Bundle?) {
                val code = info?.getInt("code") ?: -999
                val message = info?.getString("message") ?: "Unknown error"
                isProcessing = false
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Card detection error: $message",
                        code.toString()
                    )
                )
            }
        }
    }
    private fun connectSdk(onComplete: (Boolean, String?) -> Unit) {
        try {
            if (isSdkConnected && readCardOpt != null) {
                onComplete(true, null)
                return
            }

            sunmiPayKernel = SunmiPayKernel.getInstance()
            sunmiPayKernel?.initPaySDK(context, object : SunmiPayKernel.ConnectCallback {
                override fun onConnectPaySDK() {
                    // Run initialization on IO thread to avoid blocking UI
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            emvOpt = sunmiPayKernel?.mEMVOptV2
                            pinPadOpt = sunmiPayKernel?.mPinPadOptV2
                            securityOpt = sunmiPayKernel?.mSecurityOptV2
                            readCardOpt = sunmiPayKernel?.mReadCardOptV2

                            if (readCardOpt == null) {
                                val error = "ReadCardOptV2 not available"
                                handleError(
                                    PaymentResult.Error.from(
                                        errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                                        technicalMessage = error
                                    )
                                )
                                onComplete(false, error)
                                return@launch
                            }

                            isSdkConnected = true
                            if (!initializeKernelData()) {
                                val error = "Failed to initialize kernel data"
                                handleError(
                                    PaymentResult.Error.from(
                                        errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                                        technicalMessage = error
                                    )
                                )
                                onComplete(false, error)
                                return@launch
                            }

                            isKernelInitialized = true
                            onComplete(true, null)

                        } catch (e: Exception) {
                            val error = "Error in onConnectPaySDK: ${e.message}"
                            e.printStackTrace()
                            handleError(
                                PaymentResult.Error.from(
                                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                                    technicalMessage = error
                                )
                            )
                            onComplete(false, error)
                        }
                    }
                }

                override fun onDisconnectPaySDK() {
                    isSdkConnected = false
                    isKernelInitialized = false
                }
            })

        } catch (e: Exception) {
            val error = "Failed to init PaySDK: ${e.message}"
            e.printStackTrace()
            handleError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    technicalMessage = error
                )
            )
            onComplete(false, error)
        }
    }
    private fun onCardDetected(cardType: AidlConstants.CardType, info: Bundle) {
        if (currentProcessor != null) {
            return
        }

        if (emvOpt == null || pinPadOpt == null || readCardOpt == null) {
            isProcessing = false
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "SDK dependencies not available"
                )
            )
            return
        }

        val amount = pendingAmount
        val request = pendingRequest
        if (amount == null || request == null) {
            isProcessing = false
            return
        }

        currentProcessor = when (cardType) {
            AidlConstants.CardType.IC -> {
                ChipCardProcessor(context, emvOpt!!, terminal, pinPadOpt!!, readCardOpt!!, securityOpt!!)
            }

            AidlConstants.CardType.NFC -> {
                NfcCardProcessor(context, emvOpt!!, terminal, pinPadOpt!!, readCardOpt!!, securityOpt!!)
            }

            AidlConstants.CardType.MAGNETIC -> {
                MagCardProcessor(context, emvOpt!!, terminal, pinPadOpt!!, readCardOpt!!, securityOpt!!)
            }

            AidlConstants.CardType.MIFARE -> {
                MifareCardProcessor(context, emvOpt!!, terminal, pinPadOpt!!, readCardOpt!!, securityOpt!!, pinInputCallback)
            }

            else -> {
                isProcessing = false
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Unknown card type detected"
                    )
                )
                return
            }
        }

        currentProcessor?.apply {
            isKernelInitialized = true
            startProcessing(info, amount, request) { result ->
                isProcessing = false
                pendingAmount = null
                pendingRequest = null
                allowedCardTypes = null
                currentProcessor = null
                processingComplete(result)
            }
        }
    }
    private fun calculateCardTypeMask(cardTypes: Set<AidlConstants.CardType>): Int {
        var mask = 0
        cardTypes.forEach { type ->
            mask = mask or type.value
        }
        return mask
    }
}