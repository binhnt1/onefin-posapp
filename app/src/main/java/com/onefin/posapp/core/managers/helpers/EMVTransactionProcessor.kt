package com.onefin.posapp.core.managers.helpers

import android.annotation.SuppressLint
import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber
import java.util.Calendar

class EMVTransactionProcessor(
    private val emvOpt: EMVOptV2,
    private val terminal: Terminal?,
    private val listenerFactory: EMVListenerFactory
) {
    private val TAG = "EMVProcessor"

    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    interface TransactionCallback {
        fun onSuccess(requestSale: RequestSale)
        fun onError(error: PaymentResult.Error)
    }

    fun setTransactionContext(paymentAppRequest: PaymentAppRequest, amount: String) {
        Timber.tag(TAG).d("📝 Setting transaction context:")
        Timber.tag(TAG).d("├─ Amount: $amount")
        Timber.tag(TAG).d("├─ MerchantId: ${paymentAppRequest.merchantRequestData?.mid}")
        Timber.tag(TAG).d("└─ TransactionId: ${paymentAppRequest.merchantRequestData?.referenceId}")

        currentPaymentAppRequest = paymentAppRequest
        currentAmount = amount
    }

    fun processContact(callback: TransactionCallback) {
        try {
            // Log instance để verify
            Timber.tag(TAG).d("🔍 EMVOptV2 instance: ${emvOpt.hashCode()}")
            Timber.tag(TAG).d("🔍 EMVOptV2 class: ${emvOpt.javaClass.name}")

            // Step 1: Abort
            emvOpt.abortTransactProcess()
            emvOpt.initEmvProcess()

            // Step 2: Set TLVs AGAIN (fresh)
            setEmvTlvs()

            // Step 3: Set PDOL AGAIN
            setPdolDataForContact()

            // Step 4: Create bundle
            val bundle = createContactBundle()

            // Step 5: Create listener
            val listener = listenerFactory.createListener(
                CardType.CHIP,
                currentPaymentAppRequest,
                callback
            )

            // Step 6: Execute
            emvOpt.transactProcessEx(bundle, listener)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Exception")
        }
    }

    fun processContactless(callback: TransactionCallback) {
        Timber.tag(TAG).d("╔═══════════════════════════════════════════")
        Timber.tag(TAG).d("║ 📶 EMV CONTACTLESS TRANSACTION START")
        Timber.tag(TAG).d("╠═══════════════════════════════════════════")
        Timber.tag(TAG).d("║ Amount: $currentAmount")
        Timber.tag(TAG).d("║ Terminal: ${terminal?.tid ?: "NOT SET"}")
        Timber.tag(TAG).d("╚═══════════════════════════════════════════")

        try {
            // Similar steps as contact but with contactless bundle
            Timber.tag(TAG).d("🔄 Step 1: Abort previous transaction...")
            emvOpt.abortTransactProcess()

            Timber.tag(TAG).d("🔄 Step 2: Initialize EMV process...")
            emvOpt.initEmvProcess()

            Timber.tag(TAG).d("🔄 Step 3: Setting EMV TLVs...")
            setEmvTlvs()

            Timber.tag(TAG).d("🔄 Step 4: Setting PDOL data...")
            setPdolData()

            Timber.tag(TAG).d("🔄 Step 5: Creating contactless bundle...")
            val bundle = createContactlessBundle()
            logBundle("Contactless Bundle", bundle)

            Timber.tag(TAG).d("🔄 Step 6: Creating EMV listener...")
            val listener = listenerFactory.createListener(
                CardType.CONTACTLESS,
                currentPaymentAppRequest,
                callback
            )

            Timber.tag(TAG).d("🔄 Step 7: Executing contactless transaction...")
            emvOpt.transactProcessEx(bundle, listener)

            Timber.tag(TAG).d("✅ Contactless transaction processing started")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ EMV contactless error")
            callback.onError(
                PaymentResult.Error.from(
                    errorType = PaymentErrorHandler.ErrorType.EMV_DATA_INVALID,
                    technicalMessage = "EMV contactless error: ${e.message}"
                )
            )
        }
    }

    private fun createContactBundle(): Bundle {
        val bundle = Bundle().apply {
            putString("amount", currentAmount.padStart(12, '0'))
            putString("transType", "00")  // 00 = Purchase
            putInt("flowType", AidlConstants.EMV.FlowType.TYPE_EMV_STANDARD)
            putInt("cardType", AidlConstants.CardType.IC.value)

        }

        Timber.tag(TAG).d("📦 Contact Bundle created:")
        Timber.tag(TAG).d("   ├─ amount: ${bundle.getString("amount")}")
        Timber.tag(TAG).d("   ├─ transType: ${bundle.getString("transType")} (Purchase)")
        Timber.tag(TAG).d("   ├─ flowType: ${bundle.getInt("flowType")} (Contact)")
        Timber.tag(TAG).d("   └─ cardType: ${bundle.getInt("cardType")} (IC Card)")

        return bundle
    }

    private fun createContactlessBundle(): Bundle {
        val bundle = Bundle().apply {
            putString("amount", currentAmount.padStart(12, '0'))
            putString("transType", "00")  // 00 = Purchase
            putInt("flowType", AidlConstants.EMV.FlowType.TYPE_EMV_BRIEF)          // 2 = Contactless
            putInt("cardType", AidlConstants.CardType.NFC.value)          // 4 = RF Card
            putInt("timeout", 60000)       // 60 seconds
        }

        Timber.tag(TAG).d("📦 Contactless Bundle created:")
        Timber.tag(TAG).d("   ├─ amount: ${bundle.getString("amount")}")
        Timber.tag(TAG).d("   ├─ transType: ${bundle.getString("transType")} (Purchase)")
        Timber.tag(TAG).d("   ├─ flowType: ${bundle.getInt("flowType")} (Contactless)")
        Timber.tag(TAG).d("   ├─ cardType: ${bundle.getInt("cardType")} (RF Card)")
        Timber.tag(TAG).d("   └─ timeout: ${bundle.getInt("timeout")}ms")

        return bundle
    }

    private fun setEmvTlvs() {
        try {
            Timber.tag(TAG).d("📝 Setting Terminal EMV TLVs...")

            // Log terminal configuration
            if (terminal != null) {
                Timber.tag(TAG).d("   ├─ Terminal ID: ${terminal.tid}")
                Timber.tag(TAG).d("   ├─ Merchant ID: ${terminal.mid}")
            } else {
                Timber.tag(TAG).w("   └─ ⚠️ Terminal is NULL - using defaults")
            }

            CardHelper.setEmvTlvs(emvOpt, terminal)
            Timber.tag(TAG).d("   └─ ✅ EMV TLVs set successfully")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "   └─ ❌ Error setting EMV TLVs")
        }
    }

    private fun setPdolData() {
        try {
            Timber.tag(TAG).d("📝 Setting PDOL data (standard)...")

            val pdolTags = arrayOf(
                "9F66",  // TTQ - Terminal Transaction Qualifiers
                "9F02",  // Amount, Authorized
                "9F03",  // Amount, Other
                "9F1A",  // Terminal Country Code
                "95",    // Terminal Verification Results
                "5F2A",  // Transaction Currency Code
                "9A",    // Transaction Date
                "9C",    // Transaction Type
                "9F37"   // Unpredictable Number
            )

            val date = getCurrentDate()
            val unpredictableNumber = generateUnpredictableNumber()

            val pdolValues = arrayOf(
                "26000080",                      // TTQ
                currentAmount.padStart(12, '0'), // Amount
                "000000000000",                  // Amount Other
                "0704",                          // Vietnam country code
                "0000000000",                    // TVR
                "0704",                          // VND currency code
                date,                            // Transaction date
                "00",                            // Purchase
                unpredictableNumber              // Random number
            )

            // Log PDOL values
            Timber.tag(TAG).d("   PDOL Tags and Values:")
            pdolTags.forEachIndexed { index, tag ->
                val tagName = getTagName(tag)
                Timber.tag(TAG).d("   ├─ $tag ($tagName): ${pdolValues[index]}")
            }

            val result = emvOpt.setTlvList(CardConstants.OP_NORMAL, pdolTags, pdolValues)
            Timber.tag(TAG).d("   └─ PDOL set result: $result")

        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "   └─ ⚠️ Failed to set PDOL data")
        }
    }

    private fun setPdolDataForContact() {
        try {
            Timber.tag(TAG).d("📝 Setting PDOL for contact WITHOUT PSE...")

            val date = getCurrentDate()
            val unpredictableNumber = generateUnpredictableNumber()

            // ❌ BỎ PSE - Dùng AID list trực tiếp
            val tags = arrayOf(
                "9F66",  // TTQ
                "9F02",  // Amount
                "9F03",  // Amount Other
                "9F1A",  // Country Code
                "5F2A",  // Currency Code
                "9A",    // Transaction Date
                "9C",    // Transaction Type
                "9F37"   // Unpredictable Number
                // ❌ BỎ "DF60" - PSE Selection
                // ❌ BỎ "9F4E" - Merchant Name
            )

            val values = arrayOf(
                "26000080",                      // TTQ
                currentAmount.padStart(12, '0'), // Amount
                "000000000000",                  // Amount Other
                "0704",                          // Vietnam
                "0704",                          // VND
                date,                            // Date
                "00",                            // Purchase
                unpredictableNumber              // Random
            )

            Timber.tag(TAG).d("   PDOL WITHOUT PSE Tags and Values:")
            tags.forEachIndexed { index, tag ->
                Timber.tag(TAG).d("   ├─ $tag (${getTagName(tag)}): ${values[index]}")
            }

            val result = emvOpt.setTlvList(CardConstants.OP_NORMAL, tags, values)
            Timber.tag(TAG).d("   └─ PDOL set result: $result")

        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "   └─ ⚠️ Failed to set PDOL")
        }
    }

    private fun verifyPSEConfiguration() {
        try {
            Timber.tag(TAG).d("🔍 Verifying PSE configuration...")

            // Check if DF60 is set
            val df60 = CardHelper.getTlvValue(emvOpt, "DF60")
            if (df60 != null) {
                Timber.tag(TAG).d("   ├─ DF60 (PSE indicator): $df60")
                when(df60) {
                    "00" -> Timber.tag(TAG).d("   │  └─ Mode: AID list only")
                    "01" -> Timber.tag(TAG).d("   │  └─ Mode: PSE only")
                    "02" -> Timber.tag(TAG).d("   │  └─ Mode: PSE first, then AID list ✅")
                    else -> Timber.tag(TAG).d("   │  └─ Mode: Unknown")
                }
            } else {
                Timber.tag(TAG).w("   ├─ ⚠️ DF60 not found - PSE may not work")
            }

            // Check merchant name
            val merchantName = CardHelper.getTlvValue(emvOpt, "9F4E")
            if (merchantName != null) {
                Timber.tag(TAG).d("   └─ 9F4E (Merchant): $merchantName (${hexToAscii(merchantName)})")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).w("   └─ ⚠️ Could not verify PSE: ${e.message}")
        }
    }

    private fun logCurrentEMVState() {
        try {
            Timber.tag(TAG).d("📊 Current EMV State:")

            // Try to read some important tags
            val importantTags = arrayOf(
                "9F02", // Amount
                "9F1A", // Country Code
                "5F2A", // Currency Code
                "9A",   // Transaction Date
                "9C",   // Transaction Type
                "9F66", // TTQ
                "DF60"  // PSE indicator
            )

            importantTags.forEach { tag ->
                try {
                    val value = CardHelper.getTlvValue(emvOpt, tag)
                    if (value != null) {
                        Timber.tag(TAG).d("   ├─ $tag (${getTagName(tag)}): $value")
                    }
                } catch (e: Exception) {
                    // Ignore individual tag read errors
                }
            }

            // Check AIDs
            val aidList = mutableListOf<String>()
            val aidResult = emvOpt.queryAidCapkList(0, aidList)
            if (aidResult == 0 && aidList.isNotEmpty()) {
                Timber.tag(TAG).d("   └─ AIDs loaded: ${aidList.size}")
                aidList.take(3).forEach { aid ->
                    Timber.tag(TAG).d("      ├─ $aid")
                }
                if (aidList.size > 3) {
                    Timber.tag(TAG).d("      └─ ... and ${aidList.size - 3} more")
                }
            } else {
                Timber.tag(TAG).w("   └─ ⚠️ No AIDs found or error reading AIDs")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).w("Could not log EMV state: ${e.message}")
        }
    }

    private fun logBundle(name: String, bundle: Bundle) {
        Timber.tag(TAG).d("📦 $name contents:")
        bundle.keySet().forEach { key ->
            val value = when (@Suppress("DEPRECATION") val v = bundle.get(key)) {
                is String -> v
                is Int -> v.toString()
                is Long -> v.toString()
                is Boolean -> v.toString()
                else -> v?.toString() ?: "null"
            }
            Timber.tag(TAG).d("   ├─ $key: $value")
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) % 100
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dateStr = String.format("%02d%02d%02d", year, month, day)

        Timber.tag(TAG).d("   ├─ Generated date: $dateStr (YY/MM/DD: $year/$month/$day)")
        return dateStr
    }

    private fun generateUnpredictableNumber(): String {
        val unpredictable = (0..3).joinToString("") {
            (0..255).random().toString(16).padStart(2, '0')
        }.uppercase()

        Timber.tag(TAG).d("   ├─ Generated unpredictable number: $unpredictable")
        return unpredictable
    }

    private fun getTagName(tag: String): String {
        return when (tag) {
            "9F66" -> "TTQ"
            "9F02" -> "Amount"
            "9F03" -> "Amount Other"
            "9F1A" -> "Country Code"
            "95" -> "TVR"
            "5F2A" -> "Currency Code"
            "9A" -> "Transaction Date"
            "9C" -> "Transaction Type"
            "9F37" -> "Unpredictable Number"
            "DF60" -> "PSE Selection"
            "9F4E" -> "Merchant Name"
            "5A" -> "PAN"
            "5F24" -> "Expiry Date"
            "5F20" -> "Cardholder Name"
            "57" -> "Track 2"
            "9F27" -> "Cryptogram Info"
            "9F26" -> "Application Cryptogram"
            "84" -> "DF Name"
            "4F" -> "AID"
            else -> "Unknown"
        }
    }

    private fun hexToAscii(hex: String): String {
        return try {
            hex.chunked(2)
                .map { it.toInt(16).toChar() }
                .joinToString("")
        } catch (e: Exception) {
            "Invalid"
        }
    }
}