package com.onefin.posapp.core.managers.helpers

import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.data.MagneticCardData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import timber.log.Timber

class CardReaderHandler(
    private val readCardOpt: ReadCardOptV2,
    private val emvProcessor: EMVTransactionProcessor?,
    private val callback: CardReaderCallback
) {
    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    // 🔥 NEW: Single callback interface
    interface CardReaderCallback {
        fun onPaymentComplete(result: PaymentResult)
    }

    fun startReading(paymentAppRequest: PaymentAppRequest, amount: String) {
        currentPaymentAppRequest = paymentAppRequest
        currentAmount = amount

        readCardOpt.checkCard(
            CardConstants.CARD_TYPE_ALL,
            createCheckCardCallback(),
            60
        )
    }

    fun cancelReading() {
        try {
            readCardOpt.cancelCheckCard()
        } catch (e: Exception) {
            Timber.e(e, "Error canceling card check")
        }
    }

    // ==================== CARD PROCESSING ====================

    private fun processMagneticCard(info: Bundle?) {
        try {
            val track2ErrorCode = info?.getInt("track2ErrorCode", 0) ?: 0

            if (track2ErrorCode != 0) {
                val errorType = PaymentErrorHandler.mapMagneticStripeError(track2ErrorCode)
                val technicalMsg = when (track2ErrorCode) {
                    -1 -> "Parity error"
                    -2 -> "LRC error"
                    -3 -> "No data"
                    -4 -> "Decode error"
                    else -> "Unknown error ($track2ErrorCode)"
                }

                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = technicalMsg,
                        errorCode = track2ErrorCode.toString()
                    )
                )
                return
            }

            val track1 = info?.getString("TRACK1") ?: info?.getString("track1") ?: ""
            val track2 = info?.getString("TRACK2") ?: info?.getString("track2") ?: ""
            val track3 = info?.getString("TRACK3") ?: info?.getString("track3") ?: ""

            if (track2.isEmpty()) {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.MAGNETIC_STRIPE_ERROR,
                        technicalMessage = "Track2 is empty"
                    )
                )
                return
            }

            val parsedData = CardHelper.parseMagneticTrack2(track2)
            if (parsedData == null) {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "Failed to parse track2: $track2"
                    )
                )
                return
            }

            // 🔍 Validate expiry format
            if (parsedData.expiry.length != 4) {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "Invalid expiry format: ${parsedData.expiry}"
                    )
                )
                return
            }

            val request = currentPaymentAppRequest ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            // 🔥 Log để debug
            Timber.d("📤 Magnetic Card: PAN=${parsedData.pan.takeLast(4)}, Expiry=${parsedData.expiry}")
            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    emvData = "",
                    track1 = track1,
                    track2 = track2,
                    track3 = track3,
                    clearPan = parsedData.pan,
                    expiryDate = parsedData.expiry,
                    mode = CardType.MAGNETIC.displayName,
                    type = CardHelper.detectBrand(parsedData.pan),
                )
            )

            callback.onPaymentComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
            Timber.e(e, "Error processing magnetic card")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }
    }

    // 🔥 NEW: Process chip card completely
    private fun processChipCard(atr: String?) {
        try {
            if (atr.isNullOrEmpty()) {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "ATR is empty"
                    )
                )
                return
            }

            val processor = emvProcessor ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED,
                        technicalMessage = "EMV Processor not available"
                    )
                )
                return
            }

            val paymentAppRequest = currentPaymentAppRequest ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                        technicalMessage = "Payment request not initialized"
                    )
                )
                return
            }

            Timber.d("Chip Card detected - ATR: $atr")

            processor.setTransactionContext(paymentAppRequest, currentAmount)
            processor.processContact(createEMVTransactionCallback())

        } catch (e: Exception) {
            Timber.e(e, "Error processing chip card")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }
    }

    // 🔥 NEW: Process contactless card completely
    private fun processContactlessCard(ats: String?) {
        try {
            if (ats.isNullOrEmpty()) {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "ATS is empty"
                    )
                )
                return
            }

            val processor = emvProcessor ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED,
                        technicalMessage = "EMV Processor not available"
                    )
                )
                return
            }

            val paymentAppRequest = currentPaymentAppRequest ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                        technicalMessage = "Payment request not initialized"
                    )
                )
                return
            }

            Timber.d("Contactless Card detected - ATS: $ats")

            processor.setTransactionContext(paymentAppRequest, currentAmount)
            processor.processContactless(createEMVTransactionCallback())

        } catch (e: Exception) {
            Timber.e(e, "Error processing contactless card")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }
    }

    private fun createCheckCardCallback(): CheckCardCallbackV2.Stub {
        return object : CheckCardCallbackV2.Stub() {

            override fun findMagCard(info: Bundle?) {
                readCardOpt.cancelCheckCard()
                processMagneticCard(info)
            }

            override fun findICCard(atr: String?) {
                readCardOpt.cancelCheckCard()
                processChipCard(atr)  // 🔥 CHANGED: Process here
            }

            override fun findICCardEx(bundle: Bundle?) {
                readCardOpt.cancelCheckCard()
                val atr = bundle?.getString("atr") ?: bundle?.getString("ATR") ?: ""
                processChipCard(atr)  // 🔥 CHANGED: Process here
            }

            override fun findRFCard(uuid: String?) {}

            override fun findRFCardEx(bundle: Bundle?) {
                val ats = bundle?.getString("ats") ?: bundle?.getString("ATS") ?: ""
                if (ats.isNotEmpty()) {
                    readCardOpt.cancelCheckCard()
                    processContactlessCard(ats)  // 🔥 CHANGED: Process here
                }
            }

            override fun onError(code: Int, message: String?) {
                val errorType = PaymentErrorHandler.mapCardReadError(code)
                val technicalMsg = message ?: when (code) {
                    -1 -> "Card read timeout"
                    -2 -> "Operation cancelled"
                    -3 -> "Card removed"
                    -4 -> "Card read failed"
                    else -> "Unknown error"
                }

                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = technicalMsg,
                        errorCode = code.toString()
                    )
                )
            }

            override fun onErrorEx(bundle: Bundle?) {
                val code = bundle?.getInt("code") ?: -999
                val msg = bundle?.getString("message") ?: "Unknown"
                val errorType = PaymentErrorHandler.mapCardReadError(code)

                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = msg,
                        errorCode = code.toString()
                    )
                )
            }
        }
    }
    private fun createEMVTransactionCallback(): EMVTransactionProcessor.TransactionCallback {
        return object : EMVTransactionProcessor.TransactionCallback {
            override fun onSuccess(requestSale: RequestSale) {
                callback.onPaymentComplete(PaymentResult.Success(requestSale))
            }

            override fun onError(error: PaymentResult.Error) {
                callback.onPaymentComplete(error)
            }
        }
    }
}