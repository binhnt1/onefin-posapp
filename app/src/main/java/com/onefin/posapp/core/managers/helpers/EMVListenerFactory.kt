package com.onefin.posapp.core.managers.helpers

import android.os.Bundle
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVListenerV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadListenerV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import timber.log.Timber

class EMVListenerFactory(
    private val emvOpt: EMVOptV2,
    private val pinPadOpt: PinPadOptV2
) {
    fun createListener(
        cardType: CardType,
        paymentAppRequest: PaymentAppRequest?,
        callback: EMVTransactionProcessor.TransactionCallback
    ): EMVListenerV2 {

        var cardPan: String? = null

        return object : EMVListenerV2.Stub() {

            override fun onWaitAppSelect(
                candidates: MutableList<EMVCandidateV2>?,
                isFirstSelect: Boolean
            ) {
                Timber.d("🔹 onWaitAppSelect called")
                Timber.d("   ├─ First select: $isFirstSelect")
                Timber.d("   └─ Candidates from CARD: ${candidates?.size ?: 0}")

                if (candidates.isNullOrEmpty()) {
                    Timber.e("   ❌ CARD RETURNED NO APPLICATIONS!")
                    Timber.e("   This means:")
                    Timber.e("   1. Card AIDs don't match any terminal AIDs, OR")
                    Timber.e("   2. Card is not responding properly, OR")
                    Timber.e("   3. Card is damaged/blocked")

                    // Try to continue anyway
                    try {
                        emvOpt.importAppSelect(0)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to import app select")
                    }
                    return
                }

                // 🔥 LOG CHI TIẾT AIDs từ thẻ
                candidates.forEachIndexed { index, candidate ->
                    Timber.d("   [$index] CARD AID:")
                    Timber.d("      ├─ AID: ${candidate.aid}")
                    Timber.d("      ├─ App Name: ${candidate.appPreName}")
                    Timber.d("      ├─ App Label: ${candidate.appLabel}")
                    Timber.d("      └─ Priority: ${candidate.priority}")
                }

                try {
                    emvOpt.importAppSelect(0)
                    Timber.d("✅ importAppSelect SUCCESS (selected index 0)")
                } catch (e: Exception) {
                    Timber.e(e, "❌ App select error")
                }
            }

            override fun onAppFinalSelect(tag9F06Value: String?) {
                Timber.d("🔹 onAppFinalSelect called")
                Timber.d("   AID (9F06): $tag9F06Value")

                try {
                    emvOpt.importAppFinalSelectStatus(0)
                    Timber.d("✅ importAppFinalSelectStatus SUCCESS")
                } catch (e: Exception) {
                    Timber.e(e, "❌ App final select error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                            technicalMessage = "onAppFinalSelect error: ${e.message}"
                        )
                    )
                }
            }

            override fun onConfirmCardNo(cardNo: String?) {
                try {
                    cardPan = cardNo
                    Timber.d("💳 Card PAN: ${cardNo?.takeLast(4)}")
                    emvOpt.importCardNoStatus(0)
                } catch (e: Exception) {
                    Timber.e(e, "Confirm card number error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                            technicalMessage = "Confirm card number error: ${e.message}"
                        )
                    )
                }
            }

            override fun onCardDataExchangeComplete() {
                Timber.d("🔹 onCardDataExchangeComplete called")

                // 🔥 ĐỌC TRỰC TIẾP AIDs từ thẻ
                try {
                    val aidListOnCard = mutableListOf<String>()
                    val result = emvOpt.queryAidCapkList(0, aidListOnCard)

                    Timber.d("   ├─ AIDs on card (query result: $result):")
                    aidListOnCard.forEach { aid ->
                        Timber.d("   │  ├─ $aid")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "   └─ ❌ Cannot read AIDs from card")
                }
            }

            override fun onRequestDataExchange(data: String?) {
                try {
                    emvOpt.importDataExchangeStatus(0)
                } catch (e: Exception) {
                    Timber.e(e, "Data exchange error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                            technicalMessage = "Data exchange error: ${e.message}"
                        )
                    )
                }
            }

            override fun onRequestShowPinPad(pinType: Int, remainTime: Int) {
                Timber.d("🔹 onRequestShowPinPad called")
                Timber.d("   pinType: $pinType, remainTime: $remainTime")
                Timber.d("   cardPan: ****${cardPan?.takeLast(4)}")

                try {
                    val bundle = Bundle().apply {
                        putInt("keyIndex", 1)           // Key index đã inject
                        putInt("keyType", 2)            // 2 = DATA key (BDK)
                        putInt("pinType", 0)            // 0 = ISO 9564 Format 0
                        putString("pan", cardPan ?: "") // Card PAN
                        putInt("timeout", remainTime)   // Timeout in seconds
                        putInt("isOnline", 1)           // 1 = Online PIN (encrypted)
                    }
                    pinPadOpt.startInputPin(
                        bundle,
                        object : PinPadListenerV2.Stub() {

                            override fun onPinLength(len: Int) {
                                // User đang nhập PIN (mỗi lần nhấn số)
                                Timber.d("📝 PIN length: $len")
                            }

                            override fun onConfirm(resultCode: Int, pinBlock: ByteArray?) {
                                Timber.d("🔹 onConfirm called")
                                Timber.d("   resultCode: $resultCode")
                                Timber.d("   pinBlock: ${pinBlock?.size ?: 0} bytes")

                                // Check result code
                                if (resultCode != 0) {
                                    Timber.e("❌ PIN confirmation failed with code: $resultCode")
                                    val errorMsg = when (resultCode) {
                                        -1 -> "Timeout"
                                        -2 -> "User cancelled or no PIN entered"
                                        -3 -> "PIN bypass"
                                        else -> "Unknown error: $resultCode"
                                    }
                                    Timber.e("   → $errorMsg")
                                    emvOpt.importPinInputStatus(pinType, 1) // 1 = Failed/Bypass
                                    return
                                }

                                if (pinBlock == null || pinBlock.isEmpty()) {
                                    Timber.e("❌ Empty PIN block received")
                                    emvOpt.importPinInputStatus(pinType, 1)
                                    return
                                }

                                // ✅ Nhận được encrypted PIN block
                                val pinBlockHex = pinBlock.joinToString("") { "%02X".format(it) }
                                Timber.d("✅ PIN entered successfully")
                                Timber.d("   Encrypted PIN Block: $pinBlockHex")
                                Timber.d("   PIN Block length: ${pinBlock.size} bytes")

                                // ✅ Import PIN vào EMV transaction
                                try {
                                    emvOpt.importPinInputStatus(pinType, 0) // 0 = Success
                                    Timber.d("✅ PIN imported to EMV successfully")
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Failed to import PIN status")
                                    callback.onError(
                                        PaymentResult.Error.from(
                                            errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                                            technicalMessage = "Failed to import PIN: ${e.message}"
                                        )
                                    )
                                }
                            }

                            override fun onCancel() {
                                Timber.d("❌ User cancelled PIN input")
                                try {
                                    emvOpt.importPinInputStatus(pinType, 2) // 2 = Cancelled
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to import cancel status")
                                }
                                callback.onError(
                                    PaymentResult.Error.from(
                                        errorType = PaymentErrorHandler.ErrorType.EMV_USER_CANCEL,
                                        technicalMessage = "User cancelled PIN input"
                                    )
                                )
                            }

                            override fun onError(errorCode: Int) {
                                Timber.e("❌ PIN input error: $errorCode")
                                val errorMsg = when (errorCode) {
                                    -1 -> "Timeout waiting for PIN"
                                    -2 -> "Invalid parameter"
                                    -3 -> "PinPad not initialized"
                                    -4 -> "Key not found (keyIndex=1, keyType=2)"
                                    -5 -> "Card data invalid"
                                    -10001 -> "User cancelled"
                                    else -> "Unknown error code: $errorCode"
                                }
                                Timber.e("   → $errorMsg")

                                try {
                                    emvOpt.importPinInputStatus(pinType, 1) // 1 = Failed
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to import error status")
                                }

                                callback.onError(
                                    PaymentResult.Error.from(
                                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                                        technicalMessage = "PIN input error: $errorMsg"
                                    )
                                )
                            }
                        }
                    )

                    Timber.d("✅ PIN input started, waiting for user...")

                } catch (e: Exception) {
                    Timber.e(e, "💥 Failed to start PIN input")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_USER_CANCEL,
                            technicalMessage = "Cannot start PIN input: ${e.message}"
                        )
                    )
                }
            }

            override fun onRequestSignature() {
                Timber.d("📝 Signature required by card, auto-approving...")
                emvOpt.importSignatureStatus(0)  // Auto approve
            }

            override fun onCertVerify(certType: Int, certInfo: String?) {
                Timber.d("🔹 onCertVerify called")
                Timber.d("   certType: $certType")
                Timber.d("   certInfo: ${certInfo?.take(100)}...") // First 100 chars

                try {
                    emvOpt.importCertStatus(0)
                    Timber.d("✅ Certificate verified")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Certificate verify error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_TRANS_NOT_ACCEPTED,
                            technicalMessage = "Certificate error: ${e.message}"
                        )
                    )
                }
            }

            override fun onOnlineProc() {
                Timber.d("🔹 onOnlineProc called")
                Timber.d("   Card is requesting ONLINE authorization")

                try {
                    emvOpt.importOnlineProcStatus(0, null, null, null)
                    Timber.d("✅ Online proc approved (simulated)")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Online processing error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_TRANS_NOT_ACCEPTED,
                            technicalMessage = "OnlineProc error: ${e.message}"
                        )
                    )
                }
            }

            override fun onPreFirstGenAC() {
                try {
                    emvOpt.importPreFirstGenACStatus(0)
                } catch (e: Exception) {
                    Timber.w(e, "PreFirstGenAC error (may not be supported)")
                }
            }

            override fun onTermRiskManagement() {
                try {
                    emvOpt.importTermRiskManagementStatus(0)
                } catch (e: Exception) {
                    Timber.w(e, "TermRiskManagement error (may not be supported)")
                }
            }

            override fun onConfirmationCodeVerified() {}

            override fun onDataStorageProc(
                tags: Array<out String?>?,
                values: Array<out String?>?
            ) {}

            override fun onTransResult(resultCode: Int, msg: String?) {
                Timber.d("🔹 onTransResult called")
                Timber.d("   resultCode: $resultCode")
                Timber.d("   message: $msg")

                if (resultCode == 0) {
                    Timber.d("✅ Transaction APPROVED by card")
                    handleSuccessResult(cardType, paymentAppRequest, callback)
                } else {
                    Timber.e("❌ Transaction DECLINED by card")
                    Timber.e("   Checking what happened before this...")
                    handleErrorResult(resultCode, msg, callback)
                }
            }
        }
    }

    private fun handlePinEntered(pinType: Int, cardPan: String?) {
        try {
            Timber.d("🔐 Getting encrypted PIN block...")

            // Prepare Bundle with parameters
            val bundle = Bundle().apply {
                putInt("keyIndex", 1)              // keyIndex from injectKeys
                putInt("keyType", 2)               // 2 = DATA key (same as injected)
                putInt("pinType", 0)               // 0 = ISO 9564 Format 0
                putInt("timeout", 60)              // 60 seconds timeout
                putString("pan", cardPan ?: "")    // Card PAN for PIN block formatting
                putInt("isOnline", 1)              // 1 = Online PIN (encrypted)
            }

            // Output buffer for encrypted PIN block
            val pinBlockOutput = ByteArray(512) // Larger buffer for response

            val result = pinPadOpt.getPinBlock(bundle, pinBlockOutput)

            Timber.d("📤 getPinBlock() result: $result")

            if (result == 0) {
                // Parse response from output buffer
                // Typically first 8 bytes = PIN block, next 10 bytes = KSN
                val pinBlock = pinBlockOutput.copyOfRange(0, 8)
                val ksn = if (pinBlockOutput.size >= 18) {
                    pinBlockOutput.copyOfRange(8, 18)
                } else {
                    ByteArray(10)
                }

                val pinBlockHex = pinBlock.joinToString("") { "%02X".format(it) }
                val ksnHex = ksn.joinToString("") { "%02X".format(it) }

                Timber.d("✅ PIN block obtained")
                Timber.d("   PIN Block: $pinBlockHex")
                Timber.d("   KSN: $ksnHex")

                // Import PIN into EMV transaction
                emvOpt.importPinInputStatus(pinType, 0) // 0 = Success

            } else {
                Timber.e("❌ getPinBlock failed: $result")
                when (result) {
                    -1 -> Timber.e("   Reason: Timeout or user cancelled")
                    -2 -> Timber.e("   Reason: Invalid parameter")
                    -3 -> Timber.e("   Reason: PIN pad not initialized")
                    -4 -> Timber.e("   Reason: Key not found (check keyIndex=1)")
                    -10001 -> Timber.e("   Reason: User cancelled PIN entry")
                    else -> Timber.e("   Reason: Unknown error")
                }

                // Notify EMV that PIN failed
                emvOpt.importPinInputStatus(pinType, 1) // 1 = Failed/Bypass
            }

        } catch (e: Exception) {
            Timber.e(e, "💥 Exception getting PIN block")
            try {
                emvOpt.importPinInputStatus(pinType, 2) // 2 = Error
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to import PIN error status")
            }
        }
    }

    private fun handlePinCancelled(
        pinType: Int,
        callback: EMVTransactionProcessor.TransactionCallback
    ) {
        Timber.d("❌ User cancelled PIN entry")
        try {
            emvOpt.importPinInputStatus(pinType, 2) // 2 = Cancelled
        } catch (e: Exception) {
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_USER_CANCEL,
                    technicalMessage = "PIN cancelled: ${e.message}"
                )
            )
        }
    }

    private fun handleSuccessResult(
        cardType: CardType,
        paymentAppRequest: PaymentAppRequest?,
        callback: EMVTransactionProcessor.TransactionCallback
    ) {
        try {
            val tagsToRead = arrayOf(
                "5A", "57", "5F24", "5F34", "9F06", "9F26", "9F27",
                "9F10", "9F37", "9F36", "95", "9A", "9C", "9F02",
                "5F2A", "82", "9F1A", "9F33", "9F34", "9F35", "9F09"
            )

            val outBuf = ByteArray(8192)
            val ret = emvOpt.getTlvList(0, tagsToRead, outBuf)
            if (ret <= 0) {
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                        technicalMessage = "Cannot read card data (getTlvList returned $ret)"
                    )
                )
                return
            }

            val tlvData = outBuf.copyOf(ret)
            val tlvHex = tlvData.joinToString("") { "%02X".format(it) }
            val tags = CardHelper.parseEmvTlv(tlvHex)

            // 🔥 LOG CHI TIẾT
            Timber.d("📋 EMV Tags parsed:")
            tags.forEach { (tag, value) ->
                Timber.d("   $tag: $value")
            }

            // 🔥 CHECK: CVM Results (nếu có)
            val cvmResults = tags["9F34"]
            if (!cvmResults.isNullOrEmpty() && cvmResults.length >= 4) {  // ✅ CHECK LENGTH
                val cvmPerformed = cvmResults.substring(0, 2)
                val cvmCondition = cvmResults.substring(2, 4)
                Timber.d("🔐 CVM Results (9F34): $cvmResults")
                Timber.d("   CVM Performed: $cvmPerformed")
                Timber.d("   CVM Condition: $cvmCondition")

                when (cvmPerformed) {
                    "00" -> Timber.d("   → Failed")
                    "01" -> Timber.d("   → Plaintext PIN verified by ICC")
                    "02" -> Timber.d("   → Online PIN verified")
                    "1E" -> Timber.d("   → Signature")
                    "1F" -> Timber.d("   → No CVM required")
                    "3F" -> Timber.d("   → No CVM performed")
                    else -> Timber.d("   → Unknown CVM: $cvmPerformed")
                }
            } else {
                Timber.d("🔐 CVM Results (9F34): empty or invalid")
            }

            val request = paymentAppRequest ?: run {
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            val cardData = CardHelper.parseEmvData(tlvHex)
            if (cardData == null) {
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                        technicalMessage = "Cannot extract card data from EMV"
                    )
                )
                return
            }

            // 🔥 FIX: Get PAN safely
            val pan = tags["5A"]?.takeIf { it.isNotEmpty() }
                ?: tags["57"]?.takeIf { it.isNotEmpty() }?.let { track2 ->
                    // Extract PAN from Track 2 before 'D' or '='
                    track2.split('D', '=', 'd', ignoreCase = true)
                        .firstOrNull()
                        ?.filter { it.isDigit() }
                }
                ?: ""

            if (pan.isEmpty()) {
                Timber.e("❌ Cannot extract PAN from tags")
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                        technicalMessage = "Cannot extract PAN from EMV tags"
                    )
                )
                return
            }

            Timber.d("✅ Extracted PAN: ****${pan.takeLast(4)}")

            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    emvData = tlvHex,
                    clearPan = cardData.pan,
                    track2 = tags["57"] ?: "",
                    mode = cardType.displayName,
                    expiryDate = cardData.expiry,
                    type = CardHelper.detectBrand(pan),
                )
            )

            callback.onSuccess(requestSale)

        } catch (e: Exception) {
            Timber.e(e, "Error reading EMV data")
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                    technicalMessage = "Error reading EMV data: ${e.message}"
                )
            )
        }
    }

    private fun handleErrorResult(
        resultCode: Int,
        msg: String?,
        callback: EMVTransactionProcessor.TransactionCallback
    ) {
        val errorType = PaymentErrorHandler.mapEmvResultCode(resultCode)

        val technicalDesc = when (resultCode) {
            -1 -> "EMV_TIMEOUT"
            -2 -> "EMV_DATA_INVALID"
            -3 -> "EMV_APP_BLOCKED"
            -4 -> "EMV_NO_APP"
            -5 -> "EMV_USER_CANCEL"
            -6 -> "EMV_EXPIRED_CARD"
            -7 -> "EMV_TRANS_NOT_ACCEPTED"
            -4002 -> "TRANSACTION_TERMINATED"
            -4100 -> "COMMAND_TIMEOUT"
            else -> "UNKNOWN_ERROR"
        }

        val technicalMessage = buildString {
            append("EMV failed ($technicalDesc)")
            msg?.let {
                if (it.isNotBlank()) {
                    append(": $it")
                }
            }
        }

        Timber.e("EMV Transaction Failed - Code: $resultCode, Type: $errorType, Message: $technicalMessage")

        callback.onError(
            PaymentResult.Error.from(
                errorType = errorType,
                technicalMessage = technicalMessage,
                errorCode = resultCode.toString()
            )
        )
    }
}