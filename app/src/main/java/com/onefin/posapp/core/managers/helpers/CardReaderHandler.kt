package com.onefin.posapp.core.managers.helpers

import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.CardType
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import timber.log.Timber

class CardReaderHandler(
    private val readCardOpt: ReadCardOptV2,
    private val callback: CardReaderCallback
) {
    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    interface CardReaderCallback {
        fun onChipCardDetected()
        fun onContactlessCardDetected(ats: String)
        fun onMagneticCardRead(requestSale: RequestSale)
        fun onError(error: PaymentResult.Error)  // 🔥 CHANGED: String -> PaymentResult.Error
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

    private data class MagneticCardData(
        val pan: String,
        val expiry: String
    )

    private fun processMagneticCard(info: Bundle?) {
        try {
            val track2ErrorCode = info?.getInt("track2ErrorCode", 0) ?: 0

            // 🔥 CHANGED: Map error code to ErrorType
            if (track2ErrorCode != 0) {
                val errorType = PaymentErrorHandler.mapMagneticStripeError(track2ErrorCode)
                val technicalMsg = when (track2ErrorCode) {
                    -1 -> "Parity error"
                    -2 -> "LRC error"
                    -3 -> "No data"
                    -4 -> "Decode error"
                    else -> "Unknown error ($track2ErrorCode)"
                }

                callback.onError(
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

            // 🔥 CHANGED: Use ErrorType for empty track2
            if (track2.isEmpty()) {
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.MAGNETIC_STRIPE_ERROR,
                        technicalMessage = "Track2 is empty"
                    )
                )
                return
            }

            val parsedData = parseMagneticTrack2(track2)

            // 🔥 CHANGED: Use ErrorType for invalid format
            if (parsedData == null) {
                callback.onError(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "Failed to parse track2: $track2"
                    )
                )
                return
            }

            val request = currentPaymentAppRequest ?: run {
                // 🔥 CHANGED: Use ErrorType for not initialized
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
                    type = CardType.MAGNETIC.toString(),
                    track1 = track1,
                    track2 = track2,
                    track3 = track3,
                    clearPan = parsedData.pan,
                    expiryDate = parsedData.expiry,
                    emvData = "",
                    mode = "MAGNETIC"
                )
            )

            callback.onMagneticCardRead(requestSale)

        } catch (e: Exception) {
            // 🔥 CHANGED: Catch generic exceptions
            Timber.e(e, "Error processing magnetic card")
            callback.onError(
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
                callback.onChipCardDetected()
            }

            override fun findICCardEx(bundle: Bundle?) {
                readCardOpt.cancelCheckCard()
                callback.onChipCardDetected()
            }

            override fun findRFCard(uuid: String?) {}

            override fun findRFCardEx(bundle: Bundle?) {
                val ats = bundle?.getString("ats") ?: bundle?.getString("ATS") ?: ""
                if (ats.isNotEmpty()) {
                    readCardOpt.cancelCheckCard()
                    callback.onContactlessCardDetected(ats)
                }
            }

            // 🔥 CHANGED: Map error codes to ErrorType
            override fun onError(code: Int, message: String?) {
                val errorType = PaymentErrorHandler.mapCardReadError(code)
                val technicalMsg = message ?: when (code) {
                    -1 -> "Card read timeout"
                    -2 -> "Operation cancelled"
                    -3 -> "Card removed"
                    -4 -> "Card read failed"
                    else -> "Unknown error"
                }

                callback.onError(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = technicalMsg,
                        errorCode = code.toString()
                    )
                )
            }

            // 🔥 CHANGED: Map bundle error to ErrorType
            override fun onErrorEx(bundle: Bundle?) {
                val code = bundle?.getInt("code") ?: -999
                val msg = bundle?.getString("message") ?: "Unknown"

                val errorType = PaymentErrorHandler.mapCardReadError(code)

                callback.onError(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = msg,
                        errorCode = code.toString()
                    )
                )
            }
        }
    }

    private fun parseMagneticTrack2(track2: String): MagneticCardData? {
        try {
            val parts = track2.split("=", "D", "d")
            if (parts.isEmpty()) return null

            val pan = parts[0].trim()

            if (pan.length < 13 || pan.length > 19 || !pan.all { it.isDigit() }) {
                return null
            }

            val expiry = if (parts.size > 1 && parts[1].length >= 4) {
                parts[1].substring(0, 4)
            } else ""

            return MagneticCardData(pan, expiry)

        } catch (e: Exception) {
            return null
        }
    }
}