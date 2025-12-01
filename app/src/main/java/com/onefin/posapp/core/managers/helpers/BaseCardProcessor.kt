package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.atg.pos.domain.entities.payment.ByteUtil
import com.atg.pos.domain.entities.payment.TLVUtil
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardProviderType
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.EMVTag
import com.onefin.posapp.core.utils.F55Manager
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.bean.PinPadConfigV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVListenerV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadListenerV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import timber.log.Timber


abstract class BaseCardProcessor(
    protected val context: Context,
    protected val emvOpt: EMVOptV2,
    protected val terminal: Terminal?,
    protected val pinPadOpt: PinPadOptV2,
    protected val readCardOpt: ReadCardOptV2,
    protected val securityOpt: SecurityOptV2,
    protected val cardType: AidlConstants.CardType,
) {
    // --- Shared State ---
    var isKernelInitialized = false
    protected var isProcessingStarted = false
    protected var cardPanForPin: String? = null
    protected var currentAmount: String = "000000000000"
    protected var detectedCardType: CardProviderType? = null
    protected var currentPaymentAppRequest: PaymentAppRequest? = null
    protected lateinit var processingComplete: ((PaymentResult) -> Unit)

    // 🔥 Add these for PIN block storage
    protected var currentKsn: String? = null
    protected var currentPinBlock: String? = null

    protected abstract fun processTransaction(info: Bundle)

    fun startProcessing(
        info: Bundle,
        amount: String,
        paymentRequest: PaymentAppRequest,
        onProcessingComplete: (PaymentResult) -> Unit,
    ) {
        // Validate kernel initialized
        if (!isKernelInitialized) {
            Timber.e("❌ Kernel chưa được khởi tạo!")
            onProcessingComplete(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Kernel not initialized."
                )
            )
            return
        }

        if (isProcessingStarted) {
            Timber.w("⚠️ Already processing, ignoring duplicate call")
            return
        }

        // Reset state
        cardPanForPin = null
        isProcessingStarted = true
        processingComplete = onProcessingComplete
        currentPaymentAppRequest = paymentRequest
        currentAmount = amount.padStart(12, '0')
        try {
            processTransaction(info)
        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Failed to start transaction: ${e.message}"
                )
            )
        }
    }

    fun cancelProcessing() {
        try {
            isProcessingStarted = false
            emvOpt.abortTransactProcess()
        } catch (_: Exception) {
        }
    }

    protected fun handleSuccessResult() {
        try {
            val request = currentPaymentAppRequest ?: run {
                processingComplete(PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                ))
                return
            }

            val outData = ByteArray(2048)
            val f55Tags = F55Manager.getF55TagsRequired(detectedCardType, cardType)
            val tvlOpCode = F55Manager.getTLVOpCode(detectedCardType, cardType)
            val length = emvOpt.getTlvList(tvlOpCode, f55Tags, outData)

            val f55Hex = if (length > 0) {
                val copy = outData.copyOf(length)
                ByteUtil.bytes2HexStr(copy) + "9F0306000000000000"
            } else {
                ""
            }

            // 2. Get Additional Tags (for card details) ⭐ QUAN TRỌNG
            val additionalTags = arrayOf(
                EMVTag.CARD_NO,   // PAN
                EMVTag.NAME_HOLDER, // Cardholder Name
                EMVTag.CHIP_EXPIRY_DATE, // Expiry Date
                EMVTag.TRACK_1_DATA,   // Track 1
                EMVTag.TRACK_2_DATA,   // Track 2
                EMVTag.TRACK_3_DATA,   // Track 2
                EMVTag.CHIP_APP_NAME,   // App Label
                EMVTag.CHIP_APP_ID_84,   // AID
                EMVTag.CHIP_APP_ID,   // AID fallback
                EMVTag.CHIP_TC, // TC
                EMVTag.POS_ENTRY_MODE  // POS Entry Mode
            )
            val additionalData = ByteArray(2048)
            val additionalLength = emvOpt.getTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, additionalTags, additionalData)
            val cardDetailsHex = if (additionalLength > 0) {
                ByteUtil.bytes2HexStr(additionalData.copyOf(additionalLength))
            } else {
                ""
            }

            // 3. Parse Card Details từ cardDetailsHex (KHÔNG phải f55Hex)
            val tagsMap = TLVUtil.buildTLVMap(cardDetailsHex)

            val track1 = tagsMap[EMVTag.TRACK_1_DATA]?.value ?: ""
            val track3 = tagsMap[EMVTag.TRACK_3_DATA]?.value ?: ""
            var track2 = tagsMap[EMVTag.TRACK_2_DATA]?.value ?: ""
            if (track2.isNotEmpty()) {
                track2 = track2
                    .replace("D", "=", ignoreCase = true)
                    .replace("d", "=")
                    .removeSuffix("F")
                    .removeSuffix("f")
            }

            // Parse card data
            val cardData = CardHelper.parseEmvData(
                cardDetailsHex,
                track1,
                track2
            ) ?: run {
                processingComplete(PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID
                ))
                return
            }

            // 4. Extract other fields
            val tc = tagsMap[EMVTag.CHIP_TC]?.value ?: ""
            val transactionHasPin = !currentPinBlock.isNullOrEmpty()
            val aid = tagsMap[EMVTag.CHIP_APP_ID]?.value ?: tagsMap[EMVTag.CHIP_APP_ID_84]?.value ?: ""
            val posEntryMode = CardHelper.parsePosEntryMode(tagsMap[EMVTag.POS_ENTRY_MODE]?.value ?: "", cardType, transactionHasPin)

            // 5. Build request với F55 Data
            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    tc = tc,
                    aid = aid,
                    track1 = track1,
                    track2 = track2,
                    track3 = track3,
                    emvData = f55Hex,
                    pin = currentPinBlock,
                    ksn = currentKsn ?: "",
                    clearPan = cardData.pan,
                    expiryDate = cardData.expiry,
                    mode = cardType.value.toString(),
                    holderName = cardData.holderName,
                    issuerName = cardData.issuerName,
                    type = CardHelper.detectBrand(cardData.pan),
                ),
                RequestSale.Data.Device(
                    posEntryMode = posEntryMode,
                    posConditionCode = "00"
                )
            )
            processingComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
            Timber.e(e, "Exception in handleSuccessResult")
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.UNKNOWN_ERROR,
                "Exception: ${e.message}"
            ))
        }
    }
    protected fun handleError(error: PaymentResult.Error) {
        isProcessingStarted = false
        processingComplete.invoke(error)
    }

    protected fun createBundle(): Bundle {
        var flowType: Int = AidlConstants.EMV.FlowType.TYPE_EMV_STANDARD
        if (cardType == AidlConstants.CardType.NFC)
            flowType = AidlConstants.EMV.FlowType.TYPE_EMV_BRIEF

        return Bundle().apply {
            putInt("flowType", flowType)
            putString("transType", "00")
            putInt("cardType", cardType.value)
            putString("amount", currentAmount)
        }
    }
    // Trong createEmvListener()
    protected fun createEmvListener(): EMVListenerV2 {
        return object : EMVListenerV2.Stub() {
            override fun onWaitAppSelect(candidates: MutableList<EMVCandidateV2>?, isFirstSelect: Boolean) {
                onEmvWaitAppSelect(candidates, isFirstSelect)
            }

            override fun onAppFinalSelect(tag9F06Value: String?) {
                onEmvAppFinalSelect(tag9F06Value)
            }

            override fun onConfirmCardNo(cardNo: String?) {
                onEmvConfirmCardNo(cardNo)
            }

            override fun onRequestShowPinPad(pinType: Int, remainTime: Int) {
                onEmvRequestShowPinPad(pinType, remainTime)
            }

            override fun onCardDataExchangeComplete() {
                onEmvCardDataExchangeComplete()
            }

            override fun onOnlineProc() {
                onEmvOnlineProc()
            }

            override fun onTransResult(resultCode: Int, msg: String?) {
                onEmvTransResult(resultCode, msg)
            }

            override fun onConfirmationCodeVerified() {
                onEmvConfirmationCodeVerified()
            }

            override fun onRequestDataExchange(data: String?) {
                onEmvRequestDataExchange(data)
            }

            override fun onTermRiskManagement() {
                onEmvTermRiskManagement()
            }

            override fun onPreFirstGenAC() {
                onEmvPreFirstGenAC()
            }

            override fun onDataStorageProc(tags: Array<out String?>?, values: Array<out String?>?) {
                onEmvDataStorageProc(tags, values)
            }

            override fun onCertVerify(certType: Int, certInfo: String?) {
                onEmvCertVerify(certType, certInfo)
            }

            override fun onRequestSignature() {
                onEmvRequestSignature()
            }
        }
    }

    private fun onEmvOnlineProc() {
        try {
            val onlineResultStatus = AidlConstants.EMV.OnlineResult.ONLINE_APPROVAL
            val importResult = emvOpt.importOnlineProcStatus(onlineResultStatus, null, null, ByteArray(1))
            if (importResult < 0) {
                handleError(PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Exception in onOnlineProc: Code=$importResult"))
            }
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "Exception in onOnlineProc: ${e.message}"))
        }
    }
    private fun onEmvPreFirstGenAC() {
        try {
            emvOpt.importPreFirstGenACStatus(0)
        } catch (_: Exception) {
        }
    }
    private fun onEmvRequestSignature() {
        try {
            emvOpt.importSignatureStatus(0)
        } catch (_: Exception) {
        }
    }
    private fun onEmvTermRiskManagement() {
        try {
            emvOpt.importTermRiskManagementStatus(0)
        } catch (_: Exception) {
        }
    }
    private fun onEmvCardDataExchangeComplete() {
    }
    private fun onEmvConfirmationCodeVerified() {
    }
    private fun onEmvConfirmCardNo(cardNo: String?) {
        if (!cardNo.isNullOrEmpty()) {
            cardPanForPin = cardNo
        }
        try {
            emvOpt.importCardNoStatus(0)
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "importCardNoStatus failed: ${e.message}"))
        }
    }
    private fun onEmvRequestDataExchange(data: String?) {
        try {
            emvOpt.importDataExchangeStatus(0)
        } catch (_: Exception) {
        }
    }
    private fun onEmvAppFinalSelect(tag9F06Value: String?) {
        try {
            detectedCardType = CardProviderType.fromAid(tag9F06Value)
            emvOpt.importAppFinalSelectStatus(0)
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "importAppFinalSelectStatus failed: ${e.message}"))
        }
    }
    private fun onEmvTransResult(resultCode: Int, msg: String?) {
        when (resultCode) {
            AidlConstants.EMV.TransResult.SUCCESS,
            AidlConstants.EMV.TransResult.OFFLINE_APPROVAL,
            AidlConstants.EMV.TransResult.ONLINE_APPROVAL
                -> handleSuccessResult()
            else -> handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.mapEmvResultCode(resultCode),
                    "EMV Final Result: ${msg ?: "Unknown"}",
                    resultCode.toString()
                )
            )
        }
    }
    private fun onEmvCertVerify(certType: Int, certInfo: String?) {
        try {
            emvOpt.importCertStatus(0)
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "importCertStatus failed: ${e.message}"))
        }
    }
    private fun onEmvRequestShowPinPad(pinType: Int, timeoutSeconds: Int) {
        val pinPadCallback = object : PinPadListenerV2.Stub() {
            override fun onCancel() {
                emvOpt.importPinInputStatus(pinType, 1)
            }

            override fun onPinLength(len: Int) {
            }

            override fun onError(errorCode: Int) {
                emvOpt.importPinInputStatus(pinType, 3)
            }

            override fun onConfirm(resultCode: Int, pinBlock: ByteArray?) {
                if (resultCode == 0 && pinBlock != null && pinBlock.isNotEmpty()) {
                    val blockHex = pinBlock.joinToString("") { "%02X".format(it) }

                    // 🔥 LẤY KSN SAU KHI CONFIRM
                    val ksnBytes = ByteArray(10)
                    securityOpt.dukptCurrentKSN(1, ksnBytes)
                    val ksnHex = ksnBytes.joinToString("") { "%02X".format(it) }

                    currentKsn = ksnHex
                    currentPinBlock = blockHex
                    emvOpt.importPinInputStatus(pinType, 0)
                } else {
                    emvOpt.importPinInputStatus(pinType, 3)
                }
            }
        }
        try {
            val config = PinPadConfigV2().apply {
                minInput = 4
                maxInput = 6
                keySystem = 1
                timeout = 60000
                pinKeyIndex = 1
                algorithmType = 0
                pinblockFormat = 0
                pinPadType = pinType
                isOrderNumKey = false
                val panSubstring = cardPanForPin?.substring(
                    (cardPanForPin?.length ?: 0) - 13,
                    (cardPanForPin?.length ?: 0) - 1
                ) ?: ""
                pan = panSubstring.toByteArray(Charsets.US_ASCII)
            }
            pinPadOpt.initPinPad(config, pinPadCallback)  // ← initPinPad!

        } catch (e: Exception) {
            emvOpt.importPinInputStatus(pinType, 3)
        }
    }
    private fun onEmvDataStorageProc(tags: Array<out String?>?, values: Array<out String?>?) {
    }
    private fun onEmvWaitAppSelect(candidates: MutableList<EMVCandidateV2>?, isFirstSelect: Boolean) {
        try {
            emvOpt.importAppSelect(0)
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "importAppSelect failed: ${e.message}"))
        }
    }
}