package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.atg.pos.domain.entities.payment.TLVUtil
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
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
        Timber.d("🚀 Starting transaction processing (card already detected)...")
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
            Timber.d("   ✅ Cancelled successfully")
        } catch (e: Exception) {
            Timber.e(e, "⚠️ Error during cancellation")
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

            val tagsToRead = arrayOf(
                "5A", "56", "57", "5F24", "5F34", "9F06", "9F26", "9F27",
                "9F10", "9F37", "9F36", "95", "9A", "9C", "9F02", "9F03",
                "5F2A", "82", "9F1A", "9F33", "9F34", "9F35", "9F09",
                "9F1E", "84", "9F41", "50", "5F20", "9F0B", "5F2D"
            )

            val emvTagsHex = readEmvTags(tagsToRead) ?: run {
                processingComplete(PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID
                ))
                return
            }

            val tagsMap = TLVUtil.buildTLVMap(emvTagsHex)
            val cvmResults = tagsMap["9F34"]?.value ?: ""

            Timber.d("📊 CVM Results: $cvmResults")
            Timber.d("🔐 PIN Block: $currentPinBlock")
            Timber.d("🔑 KSN: $currentKsn")

            val track1 = tagsMap["56"]?.value ?: ""
            val track2 = tagsMap["57"]?.value ?: ""
            val cardData = CardHelper.parseEmvData(emvTagsHex, track1, track2) ?: run {
                processingComplete(PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID
                ))
                return
            }

            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    ksn = currentKsn ?: "",                // ✅ KSN from DUKPT
                    pin = currentPinBlock,                  // ✅ Encrypted PIN block
                    track2 = track2,
                    track1 = track1,
                    emvData = emvTagsHex,
                    clearPan = cardData.pan,
                    expiryDate = cardData.expiry,
                    mode = CardType.CHIP.displayName,
                    holderName = cardData.holderName,
                    issuerName = cardData.issuerName,
                    type = CardHelper.detectBrand(cardData.pan),
                )
            )

            processingComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
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
    protected fun createEmvListener(): EMVListenerV2 {
        return object : EMVListenerV2.Stub() {
            override fun onOnlineProc() { onEmvOnlineProc() }
            override fun onPreFirstGenAC() { onEmvPreFirstGenAC() }
            override fun onRequestSignature() { onEmvRequestSignature() }
            override fun onTermRiskManagement() { onEmvTermRiskManagement() }
            override fun onConfirmCardNo(cardNo: String?) { onEmvConfirmCardNo(cardNo) }
            override fun onConfirmationCodeVerified() { onEmvConfirmationCodeVerified() }
            override fun onCardDataExchangeComplete() { onEmvCardDataExchangeComplete() }
            override fun onRequestDataExchange(data: String?) { onEmvRequestDataExchange(data) }
            override fun onAppFinalSelect(tag9F06Value: String?) { onEmvAppFinalSelect(tag9F06Value) }
            override fun onTransResult(resultCode: Int, msg: String?) { onEmvTransResult(resultCode, msg) }
            override fun onCertVerify(certType: Int, certInfo: String?) { onEmvCertVerify(certType, certInfo) }
            override fun onDataStorageProc(tags: Array<out String?>?, values: Array<out String?>?) { onEmvDataStorageProc(tags, values) }
            override fun onRequestShowPinPad(pinType: Int, remainTime: Int) { onEmvRequestShowPinPad(pinType, remainTime) }
            override fun onWaitAppSelect(candidates: MutableList<EMVCandidateV2>?, isFirstSelect: Boolean) { onEmvWaitAppSelect(candidates, isFirstSelect) }
        }
    }
    protected fun readEmvTags(tags: Array<String>): String? {
        return try {
            val outData = ByteArray(4096)
            val len = emvOpt.getTlvList(
                AidlConstants.EMV.TLVOpCode.OP_NORMAL,
                tags,
                outData
            )
            if (len > 0) {
                outData.copyOf(len).joinToString("") { "%02X".format(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception reading tags")
            null
        }
    }

    private fun onEmvOnlineProc() {
        Timber.d("⌨️ Emv Online Proc")
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
        Timber.d("⏳ onPreFirstGenAC")
        try {
            emvOpt.importPreFirstGenACStatus(0)
        } catch (e: Exception) {
            Timber.e(e, "Lỗi importPreFirstGenACStatus")
        }
    }
    private fun onEmvRequestSignature() {
        Timber.d("⌨️ Emv Request Signature")
        try {
            emvOpt.importSignatureStatus(0)
        } catch (e: Exception) {
            Timber.w(e, "Lỗi importSignatureStatus (thường bỏ qua)")
        }
    }
    private fun onEmvTermRiskManagement() {
        Timber.d("🛡️ onTermRiskManagement")
        try {
            emvOpt.importTermRiskManagementStatus(0)
        } catch (e: Exception) {
            Timber.e(e, "Lỗi importTermRiskManagementStatus")
        }
    }
    private fun onEmvCardDataExchangeComplete() {
        Timber.d("🔄 onCardDataExchangeComplete")
    }
    private fun onEmvConfirmationCodeVerified() {
        Timber.d("✔️ onConfirmationCodeVerified")
    }
    private fun onEmvConfirmCardNo(cardNo: String?) {
        Timber.d("📊 onConfirmCardNo: $cardNo")
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
        Timber.d("📊 onRequestDataExchange")
        try {
            emvOpt.importDataExchangeStatus(0)
        } catch (e: Exception) {
            Timber.e(e, "Lỗi importDataExchangeStatus")
        }
    }
    private fun onEmvAppFinalSelect(tag9F06Value: String?) {
        Timber.d("📊 onAppFinalSelect")
        try {
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
        Timber.d("📊 onCertVerify")
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
                    Timber.d("✅ PIN Block: $blockHex")

                    // 🔥 LẤY KSN SAU KHI CONFIRM
                    val ksnBytes = ByteArray(10)
                    securityOpt.dukptCurrentKSN(1, ksnBytes)
                    val ksnHex = ksnBytes.joinToString("") { "%02X".format(it) }
                    Timber.d("✅ KSN: $ksnHex")

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
        Timber.d("💾 onDataStorageProc")
    }
    private fun onEmvWaitAppSelect(candidates: MutableList<EMVCandidateV2>?, isFirstSelect: Boolean) {
        Timber.d("💾 onWaitAppSelect")
        try {
            emvOpt.importAppSelect(0)
        } catch (e: Exception) {
            handleError(PaymentResult.Error.from(
                PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                "importAppSelect failed: ${e.message}"))
        }
    }
}