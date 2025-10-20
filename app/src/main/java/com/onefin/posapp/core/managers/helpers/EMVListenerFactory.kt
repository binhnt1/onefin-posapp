package com.onefin.posapp.core.managers.helpers

import com.onefin.posapp.core.models.CardType
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVListenerV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber

class EMVListenerFactory(
    private val emvOpt: EMVOptV2
) {

    fun createListener(
        cardType: CardType,
        paymentAppRequest: PaymentAppRequest?,
        callback: EMVTransactionProcessor.TransactionCallback
    ): EMVListenerV2 {
        return object : EMVListenerV2.Stub() {

            override fun onWaitAppSelect(
                candidates: MutableList<EMVCandidateV2>?,
                isFirstSelect: Boolean
            ) {
                try {
                    emvOpt.importAppSelect(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "App select error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_NO_APP,
                            technicalMessage = "App select error: ${e.message}"
                        )
                    )
                }
            }

            override fun onAppFinalSelect(tag9F06Value: String?) {
                try {
                    emvOpt.importAppFinalSelectStatus(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "App final select error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_NO_APP,
                            technicalMessage = "App final select error: ${e.message}"
                        )
                    )
                }
            }

            override fun onConfirmCardNo(cardNo: String?) {
                try {
                    emvOpt.importCardNoStatus(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "Confirm card number error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                            technicalMessage = "Confirm card number error: ${e.message}"
                        )
                    )
                }
            }

            override fun onCardDataExchangeComplete() {}

            override fun onRequestDataExchange(data: String?) {
                try {
                    emvOpt.importDataExchangeStatus(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
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
                try {
                    emvOpt.importPinInputStatus(pinType, 2)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "PIN input error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_USER_CANCEL,
                            technicalMessage = "PIN input error: ${e.message}"
                        )
                    )
                }
            }

            override fun onRequestSignature() {
                try {
                    emvOpt.importSignatureStatus(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "Signature error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_TRANS_NOT_ACCEPTED,
                            technicalMessage = "Signature error: ${e.message}"
                        )
                    )
                }
            }

            override fun onCertVerify(certType: Int, certInfo: String?) {
                try {
                    emvOpt.importCertStatus(0)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "Certificate verify error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.SECURITY_VIOLATION,
                            technicalMessage = "Certificate verify error: ${e.message}"
                        )
                    )
                }
            }

            override fun onOnlineProc() {
                try {
                    emvOpt.importOnlineProcStatus(0, null, null, null)
                } catch (e: Exception) {
                    // 🔥 CHANGED: Use ErrorType
                    Timber.e(e, "Online processing error")
                    callback.onError(
                        PaymentResult.Error.from(
                            errorType = PaymentErrorHandler.ErrorType.EMV_TRANS_NOT_ACCEPTED,
                            technicalMessage = "Online processing error: ${e.message}"
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

            // 🔥 MAIN CHANGE: This is where transaction result is determined
            override fun onTransResult(resultCode: Int, msg: String?) {
                if (resultCode == 0) {
                    handleSuccessResult(cardType, paymentAppRequest, callback)
                } else {
                    handleErrorResult(resultCode, msg, callback)
                }
            }
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

            // 🔥 CHANGED: Use ErrorType for read failure
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

            val request = paymentAppRequest ?: run {
                // 🔥 CHANGED: Use ErrorType
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    emvData = tlvHex,
                    track2 = tags["57"] ?: "",
                    type = cardType.toString(),
                    clearPan = tags["5A"] ?: "",
                    expiryDate = tags["5F24"] ?: "",
                )
            )

            callback.onSuccess(requestSale)

        } catch (e: Exception) {
            // 🔥 CHANGED: Use ErrorType for exceptions
            Timber.e(e, "Error reading EMV data")
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                    technicalMessage = "Error reading EMV data: ${e.message}"
                )
            )
        }
    }

    // 🔥 MAJOR CHANGE: Complete rewrite with proper error mapping
    private fun handleErrorResult(
        resultCode: Int,
        msg: String?,
        callback: EMVTransactionProcessor.TransactionCallback
    ) {
        // Map EMV result code to ErrorType
        val errorType = PaymentErrorHandler.mapEmvResultCode(resultCode)

        // Get technical description
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

        // Build technical message
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