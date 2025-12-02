package com.onefin.posapp.core.managers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.onefin.posapp.core.managers.helpers.NfcPhoneProcessor
import com.onefin.posapp.core.managers.helpers.PaymentErrorHandler
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.services.StorageService
import jakarta.inject.Singleton

@Singleton
class NfcPhoneReaderManager(
    private val context: Context,
    private val storageService: StorageService
) {
    private var nfcAdapter: NfcAdapter? = null
    private var currentProcessor: NfcPhoneProcessor? = null

    private val terminal: Terminal? by lazy {
        storageService.getAccount()?.terminal
    }

    private var isProcessing = false
    private var isInitialized = false

    private var pendingAmount: String? = null
    private var pendingRequest: PaymentAppRequest? = null
    private lateinit var processingComplete: ((PaymentResult) -> Unit)

    /**
     * Initialize NFC adapter và check availability
     */
    fun initialize(onComplete: (Boolean, String?) -> Unit) {
        if (isInitialized) {
            onComplete(true, null)
            return
        }

        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)

            if (nfcAdapter == null) {
                val error = "NFC not supported on this device"
                onComplete(false, error)
                return
            }

            if (!nfcAdapter!!.isEnabled) {
                val error = "NFC is disabled. Please enable NFC in settings"
                onComplete(false, error)
                return
            }

            isInitialized = true
            onComplete(true, null)

        } catch (e: Exception) {
            val error = "Failed to initialize NFC: ${e.message}"
            onComplete(false, error)
        }
    }

    /**
     * Bắt đầu payment flow - lưu request và chờ NFC tag
     */
    fun startPayment(
        paymentRequest: PaymentAppRequest,
        onProcessingComplete: (PaymentResult) -> Unit
    ) {
        processingComplete = onProcessingComplete
        if (!isInitialized || nfcAdapter == null) {
            onProcessingComplete(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "NfcPhoneReaderManager not initialized. Call initialize() first."
                )
            )
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            onProcessingComplete(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "NFC is disabled. Please enable NFC in settings"
                )
            )
            return
        }

        if (isProcessing) {
            cancelPayment()
        }

        isProcessing = true
        pendingRequest = paymentRequest
        pendingAmount = paymentRequest.merchantRequestData?.amount?.toString()
    }

    fun enableForegroundDispatch(activity: Activity) {
        if (!isInitialized || nfcAdapter == null) {
            return
        }

        try {
            val pendingIntent = android.app.PendingIntent.getActivity(
                activity,
                0,
                Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_MUTABLE
            )

            val techDiscovered = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val tagDiscovered = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val ndefDiscovered = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            val nfcFilters = arrayOf(techDiscovered, tagDiscovered, ndefDiscovered)

            val techLists = arrayOf(
                arrayOf(IsoDep::class.java.name),
                arrayOf(android.nfc.tech.NfcA::class.java.name),
                arrayOf(android.nfc.tech.NfcB::class.java.name)
            )

            nfcAdapter?.enableForegroundDispatch(
                activity,
                pendingIntent,
                nfcFilters,
                techLists
            )

        } catch (_: Exception) {
        }
    }

    fun handleNfcIntent(tag: Tag?) {
        if (tag == null) {
            return
        }

        if (!isProcessing) {
            return
        }

        val amount = pendingAmount
        val request = pendingRequest

        if (amount == null || request == null) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                    "Payment request not initialized"
                )
            )
            return
        }
        try {
            val techList = tag.techList
            if (!techList.contains(IsoDep::class.java.name)) {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Card does not support ISO-DEP (EMV)"
                    )
                )
                return
            }

            currentProcessor = NfcPhoneProcessor(context, terminal)
            currentProcessor?.startProcessing(tag, amount, request) { result ->
                isProcessing = false
                pendingAmount = null
                pendingRequest = null
                currentProcessor = null
                processingComplete(result)
            }

        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Failed to process NFC tag: ${e.message}"
                )
            )
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        if (nfcAdapter == null) return

        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (_: Exception) {
        }
    }

    fun cancelPayment() {
        try {
            isProcessing = false
            currentProcessor?.cancelProcessing()
            currentProcessor = null
            pendingAmount = null
            pendingRequest = null
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        try {
            cancelPayment()
            nfcAdapter = null
            isInitialized = false
        } catch (_: Exception) {
        }
    }

    fun isNfcAvailable(): Boolean {
        return nfcAdapter != null && nfcAdapter!!.isEnabled
    }

    fun isCurrentlyProcessing(): Boolean {
        return isProcessing
    }

    private fun handleError(error: PaymentResult.Error) {
        isProcessing = false
        pendingAmount = null
        pendingRequest = null
        currentProcessor = null
        processingComplete(error)
    }
}