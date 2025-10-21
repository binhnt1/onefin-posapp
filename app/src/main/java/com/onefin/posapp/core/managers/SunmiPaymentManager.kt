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
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
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

    private var emvOpt: EMVOptV2? = null
    private var pinPadOpt: PinPadOptV2? = null
    private var securityOpt: SecurityOptV2? = null
    private var payKernel: SunmiPayKernel? = null
    private var readCardOpt: ReadCardOptV2? = null
    private var isConnected = false

    private var cardReaderHandler: CardReaderHandler? = null
    private var emvProcessor: EMVTransactionProcessor? = null
    private var listenerFactory: EMVListenerFactory? = null
    private var setupManager: EMVSetupManager? = null

    private var currentAmount: String = "0"
    private var currentOnResult: ((PaymentResult) -> Unit)? = null
    private var currentPaymentAppRequest: PaymentAppRequest? = null

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
                        emvOpt = payKernel?.mEMVOptV2
                        pinPadOpt = payKernel?.mPinPadOptV2
                        securityOpt = payKernel?.mSecurityOptV2
                        readCardOpt = payKernel?.mReadCardOptV2

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
        onResult: (PaymentResult) -> Unit,
        paymentAppRequest: PaymentAppRequest,
    ) {
        if (!isConnected || readCardOpt == null) {
            onResult(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.SERVICE_NOT_CONNECTED
                )
            )
            return
        }

        currentOnResult = onResult
        currentPaymentAppRequest = paymentAppRequest
        currentAmount = paymentAppRequest.merchantRequestData?.amount?.toString() ?: "0"
        cardReaderHandler?.startReading(paymentAppRequest, currentAmount)
    }

    fun cleanup() {
        try {
            handleDisconnection()
            payKernel = null
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    fun cancelReadCard() {
        try {
            cardReaderHandler?.cancelReading()
        } catch (e: Exception) {
            Timber.e(e, "Error canceling card read")
        }
    }

    private fun initializeHelpers() {
        val terminal = storageService.getAccount()?.terminal

        setupManager = EMVSetupManager(emvOpt!!, securityOpt!!)
        setupManager!!.setupOnce(terminal).onFailure { e ->
            Timber.e(e, "EMV setup failed")
        }

        listenerFactory = EMVListenerFactory(emvOpt!!, pinPadOpt!!)
        emvProcessor = EMVTransactionProcessor(
            emvOpt = emvOpt!!,
            terminal = terminal,
            listenerFactory = listenerFactory!!
        )

        // 🔥 CHANGED: Pass emvProcessor to CardReaderHandler
        cardReaderHandler = CardReaderHandler(
            readCardOpt = readCardOpt!!,
            emvProcessor = emvProcessor,
            callback = createCardReaderCallback()
        )
    }

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