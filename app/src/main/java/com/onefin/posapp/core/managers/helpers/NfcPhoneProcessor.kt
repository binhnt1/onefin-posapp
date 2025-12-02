package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.parser.IProvider
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.sunmi.pay.hardware.aidl.AidlConstants
import java.io.IOException

class NfcPhoneProcessor(
    private val context: Context,
    private val terminal: Terminal?
) {
    private var isProcessingStarted = false
    private var currentAmount: String = "000000000000"
    private var currentPaymentRequest: PaymentAppRequest? = null
    private lateinit var processingComplete: ((PaymentResult) -> Unit)
    private var isoDep: IsoDep? = null

    fun startProcessing(
        tag: Tag,
        amount: String,
        paymentRequest: PaymentAppRequest,
        onProcessingComplete: (PaymentResult) -> Unit
    ) {
        if (isProcessingStarted) {
            return
        }

        isProcessingStarted = true
        processingComplete = onProcessingComplete
        currentPaymentRequest = paymentRequest
        currentAmount = amount.padStart(12, '0')
        try {
            readCardData(tag)
        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Failed to process NFC card: ${e.message}"
                )
            )
        }
    }

    fun cancelProcessing() {
        try {
            isProcessingStarted = false
            isoDep?.close()
            isoDep = null
        } catch (_: Exception) {
        }
    }

    private fun readCardData(tag: Tag) {
        try {
            // Get IsoDep instance
            isoDep = IsoDep.get(tag)
            if (isoDep == null) {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Card does not support IsoDep"
                    )
                )
                return
            }

            // Connect
            isoDep!!.connect()

            // Create provider for emvnfccard library
            val provider = createProvider(isoDep!!)

            // Configure EMV template
            val config = EmvTemplate.Config()
                .setContactLess(true)
                .setReadAllAids(true)
                .setReadTransactions(false)
                .setReadCplc(false)

            // Create parser
            val parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build()
            val emvCard = parser.readEmvCard()

            if (emvCard == null) {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Failed to read EMV card data"
                    )
                )
                return
            }

            // Parse EMV data
            parseAndBuildResult(emvCard)

        } catch (e: IOException) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Communication error: ${e.message}"
                )
            )
        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Error reading card: ${e.message}"
                )
            )
        } finally {
            try {
                isoDep?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun createProvider(isoDep: IsoDep): IProvider {
        return object : IProvider {
            override fun transceive(command: ByteArray): ByteArray {
                return isoDep.transceive(command)
            }

            override fun getAt(): ByteArray {
                return isoDep.historicalBytes ?: ByteArray(0)
            }
        }
    }

    private fun parseAndBuildResult(emvCard: com.github.devnied.emvnfccard.model.EmvCard) {
        try {
            val request = currentPaymentRequest ?: run {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            // Parse EMV card data
            val track1 = emvCard.track1?.raw?.let {
                UtilHelper.byteArrayToHexString(it)
            } ?: ""
            val track2 = emvCard.track2?.raw?.let {
                UtilHelper.byteArrayToHexString(it)
            } ?: ""
            val emvData = CardHelper.extractEmvDataHex(emvCard)
            val cardData = CardHelper.parseEmvData(emvData, track1, track2) ?: run {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                        "Failed to parse EMV card data"
                    )
                )
                return
            }
            val posEntryMode = CardHelper.parsePosEntryMode(null, AidlConstants.CardType.NFC,
                transactionHasPin = false,
                deviceHasPinPad = false
            )
            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    tc = null,
                    aid = null,
                    ksn = null,
                    pin = null,
                    track1 = track1,
                    track2 = track2,
                    emvData = emvData,
                    clearPan = cardData.pan,
                    expiryDate = cardData.expiry,
                    holderName = cardData.holderName,
                    issuerName = cardData.issuerName,
                    type = CardHelper.detectBrand(cardData.pan),
                    mode = AidlConstants.CardType.NFC.value.toString(),
                ),
                RequestSale.Data.Device(
                    posEntryMode = posEntryMode,
                    posConditionCode = "00"
                )
            )
            processingComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.UNKNOWN_ERROR,
                    "Exception building result: ${e.message}"
                )
            )
        } finally {
            isProcessingStarted = false
        }
    }

    private fun handleError(error: PaymentResult.Error) {
        isProcessingStarted = false
        try {
            isoDep?.close()
        } catch (e: Exception) {
            // Ignore
        }
        processingComplete(error)
    }
}