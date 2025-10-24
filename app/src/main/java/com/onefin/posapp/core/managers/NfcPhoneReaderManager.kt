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
import timber.log.Timber

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
            Timber.tag("NfcPhoneInit").d("🔌 Initializing Android NFC...")

            nfcAdapter = NfcAdapter.getDefaultAdapter(context)

            if (nfcAdapter == null) {
                val error = "NFC not supported on this device"
                Timber.tag("NfcPhoneInit").e("❌ $error")
                onComplete(false, error)
                return
            }

            if (!nfcAdapter!!.isEnabled) {
                val error = "NFC is disabled. Please enable NFC in settings"
                Timber.tag("NfcPhoneInit").w("⚠️ $error")
                onComplete(false, error)
                return
            }

            isInitialized = true
            Timber.tag("NfcPhoneInit").d("✅ NFC initialized successfully")
            onComplete(true, null)

        } catch (e: Exception) {
            val error = "Failed to initialize NFC: ${e.message}"
            Timber.tag("NfcPhoneInit").e(e, "❌ $error")
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
            Timber.tag("NfcPhonePayment").w("⚠️ Already processing, cancelling previous payment")
            cancelPayment()
        }

        isProcessing = true
        pendingRequest = paymentRequest
        pendingAmount = paymentRequest.merchantRequestData?.amount?.toString()

        Timber.tag("NfcPhonePayment").d("✅ Ready to read NFC card. Waiting for tap...")
    }

    fun enableForegroundDispatch(activity: Activity) {
        Timber.tag("NfcForeground").d("🔥 enableForegroundDispatch called")

        if (!isInitialized || nfcAdapter == null) {
            Timber.tag("NfcForeground").w("⚠️ Cannot enable - not initialized")
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

            Timber.tag("NfcForeground").d("✅ Foreground dispatch enabled")

        } catch (e: Exception) {
            Timber.tag("NfcForeground").e(e, "❌ Failed to enable foreground dispatch")
        }
    }

    fun handleNfcIntent(tag: Tag?) {
        Timber.tag("NfcIntent").d("🔥 handleNfcIntent called")
        Timber.tag("NfcIntent").d("   Tag: $tag")
        Timber.tag("NfcIntent").d("   isProcessing: $isProcessing")

        if (tag == null) {
            Timber.tag("NfcIntent").w("⚠️ Null tag received")
            return
        }

        Timber.tag("NfcIntent").d("   Tag ID: ${tag.id.contentToString()}")
        Timber.tag("NfcIntent").d("   Tech list: ${tag.techList.joinToString()}")

        if (!isProcessing) {
            Timber.tag("NfcIntent").w("⚠️ Not in processing state, ignoring tag")
            return
        }

        val amount = pendingAmount
        val request = pendingRequest

        if (amount == null || request == null) {
            Timber.tag("NfcIntent").e("❌ Pending amount or request is null")
            Timber.tag("NfcIntent").e("   amount: $amount")
            Timber.tag("NfcIntent").e("   request: $request")
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED,
                    "Payment request not initialized"
                )
            )
            return
        }

        Timber.tag("NfcIntent").d("📱 NFC tag detected, starting processing...")

        try {
            val techList = tag.techList
            Timber.tag("NfcIntent").d("   Checking for IsoDep support...")

            if (!techList.contains(IsoDep::class.java.name)) {
                Timber.tag("NfcIntent").e("❌ Card does not support ISO-DEP")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Card does not support ISO-DEP (EMV)"
                    )
                )
                return
            }

            Timber.tag("NfcIntent").d("✅ IsoDep supported, creating processor...")

            currentProcessor = NfcPhoneProcessor(context, terminal)
            currentProcessor?.startProcessing(tag, amount, request) { result ->
                Timber.tag("NfcIntent").d("📦 Processing complete: $result")
                isProcessing = false
                pendingAmount = null
                pendingRequest = null
                currentProcessor = null
                processingComplete(result)
            }

        } catch (e: Exception) {
            Timber.tag("NfcIntent").e(e, "❌ Exception processing tag")
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
            Timber.tag("NfcPhoneForeground").d("✅ Foreground dispatch disabled")
        } catch (e: Exception) {
            Timber.tag("NfcPhoneForeground").e(e, "❌ Failed to disable foreground dispatch")
        }
    }

    fun cancelPayment() {
        try {
            isProcessing = false
            currentProcessor?.cancelProcessing()
            currentProcessor = null
            pendingAmount = null
            pendingRequest = null
            Timber.tag("NfcPhoneCancel").d("✅ Payment cancelled")
        } catch (e: Exception) {
            Timber.tag("NfcPhoneCancel").e(e, "❌ Error during cancellation")
        }
    }

    fun destroy() {
        try {
            cancelPayment()
            nfcAdapter = null
            isInitialized = false
            Timber.tag("NfcPhoneDestroy").d("✅ Destroyed successfully")
        } catch (e: Exception) {
            Timber.tag("NfcPhoneDestroy").e(e, "❌ Error during destroy")
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