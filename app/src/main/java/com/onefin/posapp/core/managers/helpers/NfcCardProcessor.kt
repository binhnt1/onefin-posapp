package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentResult
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import kotlinx.coroutines.*
import timber.log.Timber

class NfcCardProcessor(
    context: Context,
    emvOpt: EMVOptV2,
    terminal: Terminal?,
    pinPadOpt: PinPadOptV2,
    readCardOpt: ReadCardOptV2,
    securityOpt: SecurityOptV2,
) : BaseCardProcessor(context, emvOpt, terminal, pinPadOpt, readCardOpt, securityOpt, AidlConstants.CardType.NFC) {

    private var timeoutJob: Job? = null
    private val TRANSACTION_TIMEOUT_MS = 30000L  // ✅ 30 seconds timeout

    override fun processTransaction(info: Bundle) {
        try {
            Timber.d("🎴 ====== NFC TRANSACTION START ======")

            // ✅ Start timeout timer
            startTimeoutTimer()

            // 1. Init
            Timber.d("📌 Step 1: Aborting previous transaction...")
            emvOpt.abortTransactProcess()
            Timber.d("✅ Previous transaction aborted")

            Timber.d("📌 Step 2: Initializing EMV process...")
            emvOpt.initEmvProcess()
            Timber.d("✅ initEmvProcess OK")

            // 2. Transaction
            val bundle = createBundle()
            Timber.d("📌 Step 3: Creating EMV listener...")
            val listener = createEmvListener()
            Timber.d("✅ EMV listener created")

            Timber.d("📌 Step 4: Starting transactProcessEx...")
            Timber.d("   Bundle contents:")
            Timber.d("   - flowType: ${bundle.getInt("flowType")}")
            Timber.d("   - transType: ${bundle.getString("transType")}")
            Timber.d("   - cardType: ${bundle.getInt("cardType")}")
            Timber.d("   - amount: ${bundle.getString("amount")}")

            emvOpt.transactProcessEx(bundle, listener)
            Timber.d("✅ transactProcessEx called - waiting for callbacks...")
            Timber.d("🎴 ====== WAITING FOR EMV CALLBACKS ======")

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception starting EMV")
            cancelTimeoutTimer()
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Exception starting EMV: ${e.message}"
                )
            )
        }
    }

    /**
     * ✅ Start timeout timer
     */
    private fun startTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutJob = CoroutineScope(Dispatchers.IO).launch {
            delay(TRANSACTION_TIMEOUT_MS)

            Timber.e("⏰ ====== TRANSACTION TIMEOUT ======")
            Timber.e("   No EMV callback received within ${TRANSACTION_TIMEOUT_MS}ms")
            Timber.e("   Possible causes:")
            Timber.e("   1. Card communication failed")
            Timber.e("   2. EMV process stuck")
            Timber.e("   3. Card removed too early")
            Timber.e("   4. AID/CAPK mismatch")

            // Abort transaction
            try {
                emvOpt.abortTransactProcess()
            } catch (e: Exception) {
                Timber.e(e, "Error aborting transaction")
            }

            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Transaction timeout. Please try again or use chip/magnetic."
                )
            )
        }

        Timber.d("⏱️ Timeout timer started (${TRANSACTION_TIMEOUT_MS}ms)")
    }

    /**
     * ✅ Cancel timeout timer
     */
    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * ✅ Override handleError to cancel timeout
     */
    override fun handleError(error: PaymentResult.Error) {
        cancelTimeoutTimer()
        super.handleError(error)
    }

    /**
     * ✅ Override handleSuccessResult to cancel timeout
     */
    override fun handleSuccessResult() {
        cancelTimeoutTimer()
        super.handleSuccessResult()
    }
}