package com.onefin.posapp.core.managers

import android.content.Context
import com.onefin.posapp.core.managers.helpers.CardReaderHandler
import com.onefin.posapp.core.managers.helpers.EMVListenerFactory
import com.onefin.posapp.core.managers.helpers.EMVSetupManager
import com.onefin.posapp.core.managers.helpers.EMVTransactionProcessor
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.services.StorageService
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Singleton
import sunmi.paylib.SunmiPayKernel
import timber.log.Timber

@Singleton
class SunmiPaymentManager(
    @param:ApplicationContext private val context: Context,
    private val storageService: StorageService,
) {
    private val TAG = "SunmiPayment"

    private var payKernel: SunmiPayKernel? = null
    private var readCardOpt: ReadCardOptV2? = null
    private var emvOpt: EMVOptV2? = null
    private var isConnected = false

    private var cardReaderHandler: CardReaderHandler? = null
    private var emvProcessor: EMVTransactionProcessor? = null
    private var listenerFactory: EMVListenerFactory? = null
    private var setupManager: EMVSetupManager? = null

    private var currentPaymentAppRequest: PaymentAppRequest? = null
    private var currentAmount: String = "0"
    private var currentOnResult: ((PaymentResult) -> Unit)? = null

    fun initialize(onReady: () -> Unit, onError: (PaymentResult.Error) -> Unit) {
        try {
            if (isConnected && readCardOpt != null) {
                onReady()
                return
            }

            payKernel = SunmiPayKernel.getInstance()
            payKernel?.initPaySDK(context, object : SunmiPayKernel.ConnectCallback {
                override fun onConnectPaySDK() {
                    try {
                        readCardOpt = payKernel?.mReadCardOptV2
                        emvOpt = payKernel?.mEMVOptV2

                        if (readCardOpt == null) {
                            onError(
                                PaymentResult.Error.from(
                                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                                    technicalMessage = "ReadCardOptV2 not available"
                                )
                            )
                            return
                        }

                        if (emvOpt != null) {
                            initializeHelpers()
                        }

                        isConnected = true
                        onReady()
                    } catch (e: Exception) {
                        Timber.e(e, "Error in onConnectPaySDK")
                        onError(
                            PaymentResult.Error.from(
                                errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                                technicalMessage = "Error onConnectPaySDK: ${e.message}"
                            )
                        )
                    }
                }

                override fun onDisconnectPaySDK() {
                    Timber.w("PaySDK disconnected")
                    handleDisconnection()
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to init PaySDK")
            onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    technicalMessage = "Failed to init PaySDK: ${e.message}"
                )
            )
        }
    }

    fun startReadCard(
        paymentAppRequest: PaymentAppRequest,
        onResult: (PaymentResult) -> Unit
    ) {
        if (!isConnected || readCardOpt == null) {
            onResult(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.SERVICE_NOT_CONNECTED
                )
            )
            return
        }

        currentPaymentAppRequest = paymentAppRequest
        currentAmount = paymentAppRequest.merchantRequestData?.amount?.toString() ?: "0"
        currentOnResult = onResult

        cardReaderHandler?.startReading(paymentAppRequest, currentAmount)
    }

    fun cancelReadCard() {
        try {
            cardReaderHandler?.cancelReading()
        } catch (e: Exception) {
            Timber.e(e, "Error canceling card read")
        }
    }

    fun cleanup() {
        try {
            handleDisconnection()
            payKernel = null
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    private fun initializeHelpers() {
        val terminal = storageService.getAccount()?.terminal

        setupManager = EMVSetupManager(emvOpt!!)
        setupManager!!.setupOnce(terminal).onFailure { e ->
            Timber.e(e, "EMV setup failed")
        }

        listenerFactory = EMVListenerFactory(emvOpt!!)

        emvProcessor = EMVTransactionProcessor(
            emvOpt = emvOpt!!,
            terminal = terminal,
            listenerFactory = listenerFactory!!
        )

        // 🔥 CHANGED: Pass emvProcessor to CardReaderHandler
        cardReaderHandler = CardReaderHandler(
            readCardOpt = readCardOpt!!,
            emvProcessor = emvProcessor,  // ← Now CardReaderHandler can handle EMV
            callback = createCardReaderCallback()
        )
    }

    // 🔥 SIMPLIFIED: Only ONE callback method!
    private fun createCardReaderCallback(): CardReaderHandler.CardReaderCallback {
        return object : CardReaderHandler.CardReaderCallback {
            override fun onPaymentComplete(result: PaymentResult) {
                currentOnResult?.invoke(result)
            }
        }
    }

    private fun handleDisconnection() {
        isConnected = false
        readCardOpt = null
        emvOpt = null
        cardReaderHandler = null
        emvProcessor = null
        listenerFactory = null
        setupManager?.reset()
        setupManager = null
    }
}