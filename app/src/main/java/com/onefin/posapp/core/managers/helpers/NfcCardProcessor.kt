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

            // 2. Transaction
            val bundle = createBundle()
            val listener = createEmvListener()
            emvOpt.transactProcessEx(bundle, listener)
        } catch (e: Exception) {
            Timber.e(e, "🔴 [NFC] Exception starting EMV transaction")
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED,
                    "Exception starting EMV: ${e.message}"
                )
            )
        }
    }
}