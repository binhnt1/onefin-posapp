package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.utils.EmvUtil
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

    override fun processTransaction(info: Bundle) {
        try {
            // 1. Init
            emvOpt.abortTransactProcess()
            emvOpt.initEmvProcess()
            Timber.d("✅ initEmvProcess OK.")

            // 2. Re-apply EMV TLVs after initEmvProcess (fixes NAPAS error -4125)
            EmvUtil.setEmvTlvs(context, emvOpt, terminal)
            Timber.d("✅ setEmvTlvs re-applied (NAPAS TTQ: 26000000)")

            // 3. Wait for NFC card activation (contactless cards need 300-500ms to be ready)
            Timber.d("⏳ Waiting 400ms for NFC card activation...")
            Thread.sleep(400)
            Timber.d("✅ NFC card should be ready now")

            // 4. Transaction
            val bundle = createBundle()
            val listener = createEmvListener()
            Timber.d("🚀 Gọi transactProcessEx...")
            emvOpt.transactProcessEx(bundle, listener)

        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Exception starting EMV: ${e.message}"
                )
            )
        }
    }
}