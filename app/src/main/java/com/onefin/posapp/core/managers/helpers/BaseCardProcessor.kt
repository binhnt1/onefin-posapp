package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.atg.pos.domain.entities.payment.TLVUtil
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVListenerV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadListenerV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import timber.log.Timber

abstract class BaseCardProcessor(
    protected val context: Context,
    protected val emvOpt: EMVOptV2,
    protected val terminal: Terminal?,
    protected val pinPadOpt: PinPadOptV2,
    protected val readCardOpt: ReadCardOptV2,
    protected val cardType: AidlConstants.CardType,
) {
    // --- Shared State ---
    var isKernelInitialized = false
    protected var isProcessingStarted = false
    protected var cardPanForPin: String? = null
    protected var currentAmount: String = "000000000000"
    protected var currentPaymentAppRequest: PaymentAppRequest? = null
    protected lateinit var processingComplete: ((PaymentResult) -> Unit)

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
            // check payment request
            val request = currentPaymentAppRequest ?: run {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            // check emvTags
            val tagsToRead = arrayOf(
                "5A", // Application PAN
                "56", // Track 1 Equivalent Data 🔥 ADDED
                "57", // Track 2 Equivalent Data
                "5F24", // Application Expiration Date
                "5F34", // Application PAN Sequence Number
                "9F06", // AID
                "9F26", // Application Cryptogram
                "9F27", // Cryptogram Information Data
                "9F10", // Issuer Application Data
                "9F37", // Unpredictable Number
                "9F36", // Application Transaction Counter
                "95",   // Terminal Verification Results
                "9A",   // Transaction Date
                "9C",   // Transaction Type
                "9F02", // Amount, Authorized
                "9F03", // Amount, Other
                "5F2A", // Transaction Currency Code
                "82",   // Application Interchange Profile
                "9F1A", // Terminal Country Code
                "9F33", // Terminal Capabilities
                "9F34", // CVM Results
                "9F35", // Terminal Type
                "9F09", // Application Version Number
                "9F1E", // Interface Device Serial Number
                "84",   // DF Name
                "9F41", // Transaction Sequence Counter
                "50",   // Application Label
                "5F20", // Cardholder Name
                "9F0B", // Cardholder Name Extended
                "5F2D", // Language Preference
            )
            val emvTagsHex = readEmvTags(tagsToRead) ?: run {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID
                    )
                )
                return
            }
            val tagsMap = TLVUtil.buildTLVMap(emvTagsHex)

            // DÙNG TLVUtil để parse
            val track1 = tagsMap["56"]?.value ?: ""
            val track2 = tagsMap["57"]?.value ?: ""
            val cardData = CardHelper.parseEmvData(emvTagsHex, track1, track2) ?: run {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID
                    )
                )
                return
            }

            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    ksn = "",
                    pin = null,
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
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.UNKNOWN_ERROR,
                    "Exception processing success result: ${e.message}"
                )
            )
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
    protected fun logBundle(bundle: Bundle?) {
        val tag = "MagCard"
        if (bundle == null) {
            Timber.tag(tag).d("Bundle is NULL")
            return
        }

        Timber.tag(tag).d("=== BUNDLE CONTENT START ===")
        Timber.tag(tag).d("Bundle size: ${bundle.size()}")

        try {
            for (key in bundle.keySet()) {
                @Suppress("DEPRECATION") val value = bundle.get(key)
                val valueType = value?.javaClass?.simpleName ?: "null"
                val valueString = when (value) {
                    is ByteArray -> "ByteArray(${value.size}) = ${UtilHelper.byteArrayToHexString(value)}"
                    is IntArray -> "IntArray(${value.size}) = ${value.contentToString()}"
                    is ArrayList<*> -> "ArrayList(${value.size}) = $value"
                    is String -> "\"$value\""
                    else -> value.toString()
                }
                Timber.tag(tag).d("  [$key] ($valueType) = $valueString")
            }
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "Error reading bundle keys")
        }

        Timber.tag(tag).d("=== BUNDLE CONTENT END ===")
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
                val tlvHex = outData.copyOf(len).joinToString("") { "%02X".format(it) }
                tlvHex
            } else {
                Timber.e("❌ Failed to read tags (len=$len)")
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
            if (importResult < 0) throw Exception("importOnlineProcStatus failed: Code=$importResult")
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
        Timber.d("📊 onConfirmCardNo")
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
        Timber.d("⌨️ Emv Request Pin Pad")
        val pinPadCallback = object : PinPadListenerV2.Stub() {
            override fun onPinLength(len: Int) {
                Timber.d("⌨️ PIN Length: $len")
            }

            override fun onConfirm(resultCode: Int, pinBlock: ByteArray?) {
                Timber.d("⌨️ PIN Confirm: Code=$resultCode, BlockSize=${pinBlock?.size ?: 0}")
                try {
                    when {
                        resultCode == 0 && pinBlock != null && pinBlock.isNotEmpty() -> emvOpt.importPinInputStatus(pinType, 0) // Success
                        resultCode == -3 -> emvOpt.importPinInputStatus(pinType, 2) // Bypass
                        else -> emvOpt.importPinInputStatus(pinType, if (resultCode == -1) 4 else 3) // Timeout(4) or Fail(3)
                    }
                } catch (e: Exception) {
                    handleError(PaymentResult.Error.from(PaymentErrorHandler.ErrorType.SDK_INIT_FAILED, "Import PIN Confirm failed"))
                }
            }

            override fun onCancel() {
                Timber.d("⌨️ PIN Cancelled")
                try {
                    emvOpt.importPinInputStatus(pinType, 1)
                } catch (e: Exception) {
                    handleError(PaymentResult.Error.from(PaymentErrorHandler.ErrorType.SDK_INIT_FAILED, "Import PIN Cancel failed"))
                }
            }

            override fun onError(errorCode: Int) {
                Timber.e("⌨️ PIN Error: $errorCode")
                try {
                    emvOpt.importPinInputStatus(pinType, 3)
                } catch (e: Exception) {
                    handleError(PaymentResult.Error.from(PaymentErrorHandler.ErrorType.SDK_INIT_FAILED, "Import PIN Error failed"))
                }
            }
        }

        try {
            val pinpadBundle = Bundle().apply {
                putInt("pinPadType", 0)
                putInt("pinType", pinType)
                putBoolean("isOrderNumKey", false)
                putInt("minInput", 4)
                putInt("maxInput", 12)
                putInt("timeout", timeoutSeconds * 1000)
                putBoolean("isSupportbypass", true)
                putInt("pinKeyIndex", CardConstants.PIN_KEY_INDEX)
                putString("pan", cardPanForPin ?: "")
                putInt("keySystem", 0)
                putInt("algorithmType", 0)
                putInt("pinblockFormat", 0)
            }
            Timber.d("   Bắt đầu hiển thị PIN Pad (startInputPin)...")
            val startResult = pinPadOpt.startInputPin(pinpadBundle, pinPadCallback)
            if (startResult != 0) {
                Timber.e("   ❌ startInputPin thất bại: Code=$startResult")
                emvOpt.importPinInputStatus(pinType, 3)
            } else {
                Timber.d("   ✅ startInputPin OK.")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Lỗi nghiêm trọng khi hiển thị PIN Pad")
            try {
                emvOpt.importPinInputStatus(pinType, 3)
            } catch (ignore: Exception) {
                // Ignore
            }
            handleError(PaymentResult.Error.from(PaymentErrorHandler.ErrorType.SDK_INIT_FAILED, "Exception showing PIN Pad: ${e.message}"))
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