package com.onefin.posapp.core.managers

import android.content.Context
import com.onefin.posapp.core.managers.helpers.CardReaderHandler
import com.onefin.posapp.core.managers.helpers.EMVListenerFactory
import com.onefin.posapp.core.managers.helpers.EMVSetupManager
import com.onefin.posapp.core.managers.helpers.EMVTransactionProcessor
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
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

    // 🔥 CHANGED: Single callback instead of separate onCardRead/onError
    private var currentOnResult: ((PaymentResult) -> Unit)? = null

    // 🔥 CHANGED: Callback signature to use PaymentResult.Error
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
                            // 🔥 CHANGED: Use PaymentResult.Error
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
                        // 🔥 CHANGED: Use PaymentResult.Error
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
            // 🔥 CHANGED: Use PaymentResult.Error
            onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    technicalMessage = "Failed to init PaySDK: ${e.message}"
                )
            )
        }
    }

    // 🔥 MAJOR CHANGE: New signature with single callback
    fun startReadCard(
        paymentAppRequest: PaymentAppRequest,
        onResult: (PaymentResult) -> Unit
    ) {
        if (!isConnected || readCardOpt == null) {
            // 🔥 CHANGED: Return PaymentResult.Error
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

        cardReaderHandler = CardReaderHandler(
            readCardOpt = readCardOpt!!,
            callback = createCardReaderCallback()
        )
    }

    // 🔥 CHANGED: Updated callback implementation
    private fun createCardReaderCallback(): CardReaderHandler.CardReaderCallback {
        return object : CardReaderHandler.CardReaderCallback {

            override fun onMagneticCardRead(requestSale: RequestSale) {
                // 🔥 CHANGED: Return Success result
                currentOnResult?.invoke(PaymentResult.Success(requestSale))
            }

            override fun onChipCardDetected() {
                processChipCard()
            }

            override fun onContactlessCardDetected(ats: String) {
                processContactlessCard()
            }

            // 🔥 CHANGED: Signature changed to PaymentResult.Error
            override fun onError(error: PaymentResult.Error) {
                currentOnResult?.invoke(error)
            }
        }
    }

    private fun processChipCard() {
        val processor = emvProcessor ?: run {
            // 🔥 CHANGED: Use PaymentResult.Error
            currentOnResult?.invoke(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED
                )
            )
            return
        }

        val paymentAppRequest = currentPaymentAppRequest ?: run {
            // 🔥 CHANGED: Use PaymentResult.Error
            currentOnResult?.invoke(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                )
            )
            return
        }

        processor.setTransactionContext(paymentAppRequest, currentAmount)
        processor.processContact(createTransactionCallback())
    }

    private fun processContactlessCard() {
        val processor = emvProcessor ?: run {
            // 🔥 CHANGED: Use PaymentResult.Error
            currentOnResult?.invoke(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_PROCESSOR_NOT_INITIALIZED
                )
            )
            return
        }

        val paymentAppRequest = currentPaymentAppRequest ?: run {
            // 🔥 CHANGED: Use PaymentResult.Error
            currentOnResult?.invoke(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                )
            )
            return
        }

        processor.setTransactionContext(paymentAppRequest, currentAmount)
        processor.processContactless(createTransactionCallback())
    }

    // 🔥 CHANGED: Updated callback to use PaymentResult
    private fun createTransactionCallback(): EMVTransactionProcessor.TransactionCallback {
        return object : EMVTransactionProcessor.TransactionCallback {
            override fun onSuccess(requestSale: RequestSale) {
                currentOnResult?.invoke(PaymentResult.Success(requestSale))
            }

            // 🔥 CHANGED: Signature changed to PaymentResult.Error
            override fun onError(error: PaymentResult.Error) {
                currentOnResult?.invoke(error)
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