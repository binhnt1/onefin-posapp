package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import timber.log.Timber

class MagCardProcessor(
    context: Context,
    emvOpt: EMVOptV2,
    terminal: Terminal?,
    pinPadOpt: PinPadOptV2,
    readCardOpt: ReadCardOptV2
) : BaseCardProcessor(context, emvOpt, terminal, pinPadOpt, readCardOpt, AidlConstants.CardType.MAGNETIC) {
    override fun processTransaction(info: Bundle) {
        try {
            logBundle(info)

            // check payment request
            val request = currentPaymentAppRequest ?: run {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            // check error code
            val track2ErrorCode = info.getInt("track2ErrorCode", 0)
            if (track2ErrorCode != 0) {
                val technicalMsg = when (track2ErrorCode) {
                    -1 -> "Parity error"
                    -2 -> "LRC error"
                    -3 -> "No data"
                    -4 -> "Decode error"
                    else -> "Unknown error ($track2ErrorCode)"
                }

                val errorType = PaymentErrorHandler.mapMagneticStripeError(track2ErrorCode)
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = errorType,
                        technicalMessage = technicalMsg,
                        errorCode = track2ErrorCode.toString()
                    )
                )
                return
            }

            // check track2
            val track1 = info.getString("TRACK1") ?: info.getString("track1") ?: ""
            val track2 = info.getString("TRACK2") ?: info.getString("track2") ?: ""
            val track3 = info.getString("TRACK3") ?: info.getString("track3") ?: ""
            if (track2.isEmpty()) {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.MAGNETIC_STRIPE_ERROR,
                        technicalMessage = "Track2 is empty"
                    )
                )
                return
            }

            // check track2 format
            val parsedData = CardHelper.parseMagneticCard(track1, track2)
            if (parsedData == null) {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "Failed to parse track2: $track2"
                    )
                )
                return
            }

            val emvData = CardHelper.buildMagneticEmvData(
                track2 = track2,
                pan = parsedData.pan,
                expiryDate = parsedData.expiry,
                serviceCode = parsedData.serviceCode,
            )
            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    track1 = track1,
                    track2 = track2,
                    track3 = track3,
                    emvData = emvData,
                    clearPan = parsedData.pan,
                    expiryDate = parsedData.expiry,
                    holderName = parsedData.holderName,
                    issuerName = parsedData.issuerName,
                    mode = CardType.MAGNETIC.displayName,
                    type = CardHelper.detectBrand(parsedData.pan),
                )
            )
            processingComplete(PaymentResult.Success(requestSale))
        } catch (e: Exception) {
            processingComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }
    }
}