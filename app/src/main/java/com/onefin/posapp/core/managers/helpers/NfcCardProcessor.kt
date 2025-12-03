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

            // 2. Re-apply EMV TLVs after initEmvProcess (fixes NAPAS error -4125)
            Timber.d("🔵 [NFC] Re-applying EMV TLVs (NAPAS error -4125 workaround)")
            EmvUtil.setEmvTlvs(context, emvOpt, terminal)
            Thread.sleep(400)

            // 2.1. Update NAPAS Pure TLV dynamically based on transaction amount
            // Following ATG.POS architecture for dynamic DF8133 configuration
            val bundle = createBundle()
            val amountStr = bundle.getString("amount") ?: "0"
            val amount = amountStr.toLongOrNull() ?: 0L
            Timber.d("🔵 [NFC] Updating NAPAS Pure TLV for transaction amount: $amount")
            EmvUtil.updateNapasPureTlvForTransaction(emvOpt, terminal, amount, flagRc85 = false)

            Timber.d("🔵 [NFC] Initializing EMV process")
            emvOpt.initEmvProcess()

            // 3. Transaction
            Timber.d("🔵 [NFC] Starting transaction with amount: ${bundle.getString("amount")}, cardType: NFC")
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