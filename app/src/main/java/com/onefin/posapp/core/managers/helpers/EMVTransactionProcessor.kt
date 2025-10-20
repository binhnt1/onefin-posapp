package com.onefin.posapp.core.managers.helpers

import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber

class EMVTransactionProcessor(
    private val emvOpt: EMVOptV2,
    private val terminal: Terminal?,
    private val listenerFactory: EMVListenerFactory
) {

    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    // 🔥 CHANGED: Interface callback signature
    interface TransactionCallback {
        fun onSuccess(requestSale: RequestSale)
        fun onError(error: PaymentResult.Error)  // 🔥 CHANGED: String -> PaymentResult.Error
    }

    fun setTransactionContext(paymentAppRequest: PaymentAppRequest, amount: String) {
        currentPaymentAppRequest = paymentAppRequest
        currentAmount = amount
    }

    fun processContact(callback: TransactionCallback) {
        try {
            emvOpt.abortTransactProcess()
            emvOpt.initEmvProcess()
            setEmvTlvs()

            val bundle = createContactBundle()
            val listener = listenerFactory.createListener(
                CardType.CHIP,
                currentPaymentAppRequest,
                callback
            )

            emvOpt.transactProcessEx(bundle, listener)

        } catch (e: Exception) {
            // 🔥 CHANGED: Use ErrorType instead of String
            Timber.e(e, "EMV contact error")
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                    technicalMessage = "EMV contact error: ${e.message}"
                )
            )
        }
    }

    fun processContactless(callback: TransactionCallback) {
        try {
            emvOpt.abortTransactProcess()
            emvOpt.initEmvProcess()
            setEmvTlvs()
            setPdolData()

            val bundle = createContactlessBundle()
            val listener = listenerFactory.createListener(
                CardType.CONTACTLESS,
                currentPaymentAppRequest,
                callback
            )

            emvOpt.transactProcessEx(bundle, listener)

        } catch (e: Exception) {
            // 🔥 CHANGED: Use ErrorType instead of String
            Timber.e(e, "EMV contactless error")
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                    technicalMessage = "EMV contactless error: ${e.message}"
                )
            )
        }
    }

    private fun createContactBundle(): Bundle {
        return Bundle().apply {
            putString("amount", currentAmount.padStart(12, '0'))
            putString("transType", "00")
            putInt("flowType", 1)
            putInt("cardType", 2)
        }
    }

    private fun createContactlessBundle(): Bundle {
        return Bundle().apply {
            putString("amount", currentAmount.padStart(12, '0'))
            putString("transType", "00")
            putInt("flowType", 2)
            putInt("cardType", 4)
            putInt("timeout", 60000)
        }
    }

    private fun setEmvTlvs() {
        try {
            CardHelper.setEmvTlvs(emvOpt, terminal)
        } catch (e: Exception) {
            Timber.e(e, "Error setting EMV TLVs")
        }
    }

    private fun setPdolData() {
        try {
            val pdolTags = arrayOf(
                "9F66", "9F02", "9F03", "9F1A", "95",
                "5F2A", "9A", "9C", "9F37"
            )
            val pdolValues = arrayOf(
                "26000080",
                currentAmount.padStart(12, '0'),
                "000000000000",
                "0704",
                "0000000000",
                "0704",
                "251020",
                "00",
                "12345678"
            )
            emvOpt.setTlvList(CardConstants.OP_NORMAL, pdolTags, pdolValues)
        } catch (e: Exception) {
            Timber.w(e, "Failed to set PDOL data")
        }
    }
}