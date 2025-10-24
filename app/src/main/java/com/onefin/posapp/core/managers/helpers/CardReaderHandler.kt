package com.onefin.posapp.core.managers.helpers

import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
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
    private val TAG = "CardReader"
    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    interface CardReaderCallback {
        fun onPaymentComplete(result: PaymentResult)
    }

    fun startReading(paymentAppRequest: PaymentAppRequest, amount: String) {
        Timber.tag(TAG).d("═══════════════════════════════════════════")
        Timber.tag(TAG).d("🔵 START CARD READING")
        Timber.tag(TAG).d("├─ Amount: $amount")
        Timber.tag(TAG).d("├─ MerchantId: ${paymentAppRequest.merchantRequestData?.mid}")
        Timber.tag(TAG).d("├─ TransactionId: ${paymentAppRequest.merchantRequestData?.referenceId}")
        Timber.tag(TAG).d("└─ Card Types: ALL (Magnetic, Chip, Contactless)")
        Timber.tag(TAG).d("═══════════════════════════════════════════")

        currentPaymentAppRequest = paymentAppRequest
        currentAmount = amount

        try {
            val timeout = 60
            Timber.tag(TAG).d("📡 Calling checkCard with timeout: ${timeout}s")

            readCardOpt.checkCard(
                CardConstants.CARD_TYPE_ALL,
                createCheckCardCallback(),
                timeout
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to start card reading")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = "startReading exception: ${e.message}"
                )
            )
        }
    }

    fun cancelReading() {
        Timber.tag(TAG).d("🛑 Canceling card reading...")
        try {
            readCardOpt.cancelCheckCard()
            Timber.tag(TAG).d("✅ Card reading canceled successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error canceling card check")
        }
    }

    // ==================== CHIP CARD PROCESSING ====================

    private fun processChipCard(atr: String?) {
        Timber.tag(TAG).d("═══════════════════════════════════════════")
        Timber.tag(TAG).d("💳 CHIP CARD DETECTED")
        Timber.tag(TAG).d("├─ ATR: ${atr ?: "NULL"}")
        Timber.tag(TAG).d("├─ ATR Length: ${atr?.length ?: 0}")
        Timber.tag(TAG).d("└─ ATR Bytes: ${atr?.chunked(2)?.joinToString(" ")}")
        Timber.tag(TAG).d("═══════════════════════════════════════════")

        try {
            // Validate ATR
            if (atr.isNullOrEmpty()) {
                Timber.tag(TAG).e("❌ ATR is null or empty - Invalid card")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "ATR is empty"
                    )
                )
                return
            }

            // Validate EMV Processor
            val processor = emvProcessor
            if (processor == null) {
                Timber.tag(TAG).e("❌ EMV Processor is NULL - System not initialized")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED,
                        technicalMessage = "EMV Processor not available"
                    )
                )
                return
            }
            Timber.tag(TAG).d("✅ EMV Processor available")

            // Validate Payment Request
            val paymentAppRequest = currentPaymentAppRequest
            if (paymentAppRequest == null) {
                Timber.tag(TAG).e("❌ Payment request is NULL")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                        technicalMessage = "Payment request not initialized"
                    )
                )
                return
            }
            Timber.tag(TAG).d("✅ Payment request validated")

            // Parse ATR for debugging
            parseAndLogATR(atr)

            // Process EMV Contact Transaction
            Timber.tag(TAG).d("📤 Passing to EMVTransactionProcessor...")
            Timber.tag(TAG).d("├─ Amount: $currentAmount")
            Timber.tag(TAG).d("└─ Starting EMV Contact flow...")

            processor.setTransactionContext(paymentAppRequest, currentAmount)
            processor.processContact(createEMVTransactionCallback())

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Exception in processChipCard")
            Timber.tag(TAG).e("├─ Exception type: ${e.javaClass.simpleName}")
            Timber.tag(TAG).e("├─ Message: ${e.message}")
            Timber.tag(TAG).e("└─ StackTrace: ${e.stackTraceToString().take(500)}")

            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = "ChipCard exception: ${e.message}"
                )
            )
        }
    }

    private fun parseAndLogATR(atr: String) {
        try {
            Timber.tag(TAG).d("📋 ATR Analysis:")

            // Basic ATR structure parsing
            if (atr.length >= 4) {
                val ts = atr.substring(0, 2)
                val t0 = atr.substring(2, 4)

                Timber.tag(TAG).d("├─ TS (Initial char): $ts")
                Timber.tag(TAG).d("├─ T0 (Format char): $t0")

                // Parse T0 to get number of historical bytes
                val t0Int = t0.toIntOrNull(16) ?: 0
                val historicalBytes = t0Int and 0x0F
                Timber.tag(TAG).d("├─ Historical bytes count: $historicalBytes")

                // Check protocols supported
                val hasTA1 = (t0Int and 0x10) != 0
                val hasTB1 = (t0Int and 0x20) != 0
                val hasTC1 = (t0Int and 0x40) != 0
                val hasTD1 = (t0Int and 0x80) != 0

                Timber.tag(TAG).d("├─ Protocol indicators:")
                Timber.tag(TAG).d("│  ├─ TA1 present: $hasTA1")
                Timber.tag(TAG).d("│  ├─ TB1 present: $hasTB1")
                Timber.tag(TAG).d("│  ├─ TC1 present: $hasTC1")
                Timber.tag(TAG).d("│  └─ TD1 present: $hasTD1")
            }

            // Common ATR patterns
            when {
                atr.startsWith("3B") -> Timber.tag(TAG).d("└─ Card Type: Direct Convention (3B)")
                atr.startsWith("3F") -> Timber.tag(TAG).d("└─ Card Type: Inverse Convention (3F)")
                else -> Timber.tag(TAG).d("└─ Card Type: Unknown prefix")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).w("⚠️ Could not parse ATR: ${e.message}")
        }
    }

    // ==================== CONTACTLESS PROCESSING ====================

    private fun processContactlessCard(ats: String?) {
        Timber.tag(TAG).d("═══════════════════════════════════════════")
        Timber.tag(TAG).d("📶 CONTACTLESS CARD DETECTED")
        Timber.tag(TAG).d("├─ ATS: ${ats ?: "NULL"}")
        Timber.tag(TAG).d("├─ ATS Length: ${ats?.length ?: 0}")
        Timber.tag(TAG).d("└─ ATS Bytes: ${ats?.chunked(2)?.joinToString(" ")}")
        Timber.tag(TAG).d("═══════════════════════════════════════════")

        try {
            if (ats.isNullOrEmpty()) {
                Timber.tag(TAG).e("❌ ATS is null or empty")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "ATS is empty"
                    )
                )
                return
            }

            val processor = emvProcessor ?: run {
                Timber.tag(TAG).e("❌ EMV Processor not initialized")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED,
                        technicalMessage = "EMV Processor not available"
                    )
                )
                return
            }

            val paymentAppRequest = currentPaymentAppRequest ?: run {
                Timber.tag(TAG).e("❌ Payment request not initialized")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                        technicalMessage = "Payment request not initialized"
                    )
                )
                return
            }

            Timber.tag(TAG).d("📤 Starting EMV Contactless flow...")
            processor.setTransactionContext(paymentAppRequest, currentAmount)
            processor.processContactless(createEMVTransactionCallback())

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error processing contactless card")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }
    }

    // ==================== MAGNETIC CARD PROCESSING ====================

    private fun processMagneticCard(info: Bundle?) {
        Timber.tag(TAG).d("═══════════════════════════════════════════")
        Timber.tag(TAG).d("🔖 MAGNETIC CARD DETECTED")

        try {
            val track2ErrorCode = info?.getInt("track2ErrorCode", 0) ?: 0

            Timber.tag(TAG).d("├─ Track2 Error Code: $track2ErrorCode")

            if (track2ErrorCode != 0) {
                val technicalMsg = when (track2ErrorCode) {
                    -1 -> "Parity error"
                    -2 -> "LRC error"
                    -3 -> "No data"
                    -4 -> "Decode error"
                    else -> "Unknown error ($track2ErrorCode)"
                }

                Timber.tag(TAG).e("├─ ❌ Track2 Error: $technicalMsg")

                val errorType = PaymentErrorHandler.mapMagneticStripeError(track2ErrorCode)
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

            Timber.tag(TAG).d("├─ Track1 present: ${track1.isNotEmpty()} (${track1.length} chars)")
            Timber.tag(TAG).d("├─ Track2 present: ${track2.isNotEmpty()} (${track2.length} chars)")
            Timber.tag(TAG).d("├─ Track3 present: ${track3.isNotEmpty()} (${track3.length} chars)")

            if (track2.isNotEmpty()) {
                Timber.tag(TAG).d("├─ Track2 data: ${track2.take(6)}...${track2.takeLast(4)}")
            }

            if (track2.isEmpty()) {
                Timber.tag(TAG).e("└─ ❌ Track2 is empty")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.MAGNETIC_STRIPE_ERROR,
                        technicalMessage = "Track2 is empty"
                    )
                )
                return
            }

            val parsedData = CardHelper.parseMagneticCard(track1, track2)
            if (parsedData == null) {
                Timber.tag(TAG).e("└─ ❌ Failed to parse track2")
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.INVALID_CARD_FORMAT,
                        technicalMessage = "Failed to parse track2: $track2"
                    )
                )
                return
            }

            Timber.tag(TAG).d("├─ PAN: ****${parsedData.pan.takeLast(4)}")
            Timber.tag(TAG).d("├─ Expiry: ${parsedData.expiry}")
            Timber.tag(TAG).d("├─ Service Code: ${parsedData.serviceCode}")
            Timber.tag(TAG).d("└─ Card Brand: ${CardHelper.detectBrand(parsedData.pan)}")

            // Process successful magnetic card...
            val request = currentPaymentAppRequest ?: run {
                callback.onPaymentComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            val emvData = CardHelper.buildMagneticEmvData(
                pan = parsedData.pan,
                expiryDate = parsedData.expiry,
                track2 = track2,
                serviceCode = parsedData.serviceCode
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
                    mode = CardType.MAGNETIC.displayName,
                    type = CardHelper.detectBrand(parsedData.pan),
                )
            )

            Timber.tag(TAG).d("✅ Magnetic card processed successfully")
            callback.onPaymentComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error processing magnetic card")
            callback.onPaymentComplete(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    technicalMessage = e.message
                )
            )
        }

        Timber.tag(TAG).d("═══════════════════════════════════════════")
    }

    // ==================== CALLBACKS ====================

    private fun createCheckCardCallback(): CheckCardCallbackV2.Stub {
        return object : CheckCardCallbackV2.Stub() {

            override fun findMagCard(info: Bundle?) {
                Timber.tag(TAG).d("🔄 CheckCardCallback: findMagCard triggered")
                readCardOpt.cancelCheckCard()
                processMagneticCard(info)
            }

            override fun findICCard(atr: String?) {
                Timber.tag(TAG).d("🔄 CheckCardCallback: findICCard (deprecated) triggered")
                Timber.tag(TAG).d("├─ ATR: $atr")
                Timber.tag(TAG).d("└─ Note: This is deprecated, should use findICCardEx")
            }

            override fun findICCardEx(bundle: Bundle?) {
                Timber.tag(TAG).d("🔄 CheckCardCallback: findICCardEx triggered")

                val atr = bundle?.getString("atr") ?: bundle?.getString("ATR") ?: ""
                if (atr.isNotEmpty()) {
                    Timber.tag(TAG).d("└─ ATR found: $atr")
                    readCardOpt.cancelCheckCard()
                    processChipCard(atr)
                } else {
                    Timber.tag(TAG).e("└─ ❌ No ATR in bundle")
                }
            }

            override fun findRFCard(uuid: String?) {
                Timber.tag(TAG).d("🔄 CheckCardCallback: findRFCard (deprecated) triggered")
                Timber.tag(TAG).d("├─ UUID: $uuid")
                Timber.tag(TAG).d("└─ Note: This is deprecated, should use findRFCardEx")
            }

            override fun findRFCardEx(bundle: Bundle?) {
                Timber.tag(TAG).d("🔄 CheckCardCallback: findRFCardEx triggered")
                val ats = bundle?.getString("ats") ?: bundle?.getString("ATS") ?: ""
                if (ats.isNotEmpty()) {
                    Timber.tag(TAG).d("└─ ATS found: $ats")
                    readCardOpt.cancelCheckCard()
                    processContactlessCard(ats)
                } else {
                    Timber.tag(TAG).e("└─ ❌ No ATS in bundle")
                }
            }

            override fun onError(code: Int, message: String?) {
                Timber.tag(TAG).e("🔄 CheckCardCallback: onError triggered")
                Timber.tag(TAG).e("├─ Code: $code")
                Timber.tag(TAG).e("└─ Message: $message")
            }

            override fun onErrorEx(bundle: Bundle?) {
                Timber.tag(TAG).e("🔄 CheckCardCallback: onErrorEx triggered")

                val code = bundle?.getInt("code") ?: -999
                val msg = bundle?.getString("message") ?: "Unknown"

                Timber.tag(TAG).e("├─ Error Code: $code")
                Timber.tag(TAG).e("├─ Error Message: $msg")

                val errorType = PaymentErrorHandler.mapCardReadError(code)
                Timber.tag(TAG).e("└─ Mapped ErrorType: $errorType")

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
                Timber.tag(TAG).d("═══════════════════════════════════════════")
                Timber.tag(TAG).d("✅ EMV TRANSACTION SUCCESS")
                Timber.tag(TAG).d("├─ Card Mode: ${requestSale.data.card.mode}")
                Timber.tag(TAG).d("├─ Card Type: ${requestSale.data.card.type}")
                Timber.tag(TAG).d("├─ PAN: ****${requestSale.data.card.clearPan.takeLast(4)}")
                Timber.tag(TAG).d("├─ Expiry: ${requestSale.data.card.expiryDate}")
                Timber.tag(TAG).d("═══════════════════════════════════════════")

                callback.onPaymentComplete(PaymentResult.Success(requestSale))
            }

            override fun onError(error: PaymentResult.Error) {
                Timber.tag(TAG).e("═══════════════════════════════════════════")
                Timber.tag(TAG).e("❌ EMV TRANSACTION ERROR")
                Timber.tag(TAG).e("├─ Error Type: ${error.type}")
                Timber.tag(TAG).e("├─ User Message: ${error.vietnameseMessage}")
                Timber.tag(TAG).e("├─ Technical Message: ${error.technicalMessage}")
                Timber.tag(TAG).e("└─ Error Code: ${error.errorCode}")
                Timber.tag(TAG).e("═══════════════════════════════════════════")

                callback.onPaymentComplete(error)
            }
        }
    }
}