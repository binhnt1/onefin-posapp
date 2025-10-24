package com.onefin.posapp.core.managers.helpers

import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.utils.CardHelper
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import timber.log.Timber

class EMVSetupManager(
    private val emvOpt: EMVOptV2,
    private val securityOpt: SecurityOptV2
) {
    private val TAG = "EMVSetup"

    private var isSetupCompleted = false

    fun setupOnce(terminal: Terminal?): Result<Unit> {
        Timber.tag(TAG).d("🔍 EMVOptV2 instance in setup: ${emvOpt.hashCode()}")
        if (isSetupCompleted) {
            Timber.tag(TAG).d("✅ Setup already completed, skipping...")
            return Result.success(Unit)
        }

        return try {
            // Step 1: Clear existing data
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.tag(TAG).d("🧹 STEP 1: CLEARING EXISTING DATA")
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            clearExistingData()

            // Step 2: Inject CAPKs
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.tag(TAG).d("🔑 STEP 2: INJECTING CAPKs")
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            CardHelper.injectCapks(emvOpt)
            verifyCAPKs()

            // Step 3: Terminal-specific setup
            if (terminal != null) {
                Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.tag(TAG).d("🏢 STEP 3: TERMINAL-SPECIFIC SETUP")
                Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Inject security keys
                Timber.tag(TAG).d("🔐 Injecting security keys...")
                CardHelper.injectKeys(securityOpt, terminal)
                Timber.tag(TAG).d("   └─ ✅ Security keys injected")

                // Inject AIDs
                if (!terminal.evmConfigs.isNullOrEmpty()) {
                    Timber.tag(TAG).d("📋 Injecting AIDs from terminal config...")
                    Timber.tag(TAG).d("   ├─ Number of AID configs: ${terminal.evmConfigs.size}")

                    terminal.evmConfigs.forEachIndexed { index, config ->
                        Timber.tag(TAG).d("   ├─ AID[$index]:")
                        Timber.tag(TAG).d("   │  ├─ AID: ${config.aid9F06}")
                        Timber.tag(TAG).d("   │  ├─ App Label: ${config.appName}")
                        Timber.tag(TAG).d("   │  ├─ App Version: ${config.version9F09}")
                        Timber.tag(TAG).d("   │  ├─ Terminal Floor Limit: ${config.floorLimit9F1B}")
                        Timber.tag(TAG).d("   │  ├─ Threshold: ${config.threshold}")
                        Timber.tag(TAG).d("   │  ├─ Target Percentage: ${config.targetPercent}")
                        Timber.tag(TAG).d("   │  ├─ Max Target Percentage: ${config.maxTargetPercent}")
                        Timber.tag(TAG).d("   │  ├─ TAC Default: ${config.tacDefault}")
                        Timber.tag(TAG).d("   │  ├─ TAC Denial: ${config.tacDenial}")
                        Timber.tag(TAG).d("   │  ├─ TAC Online: ${config.tacOnline}")
                        Timber.tag(TAG).d("   │  └─ DDOL: ${config.defaultDDOL}")

                        try {
                            CardHelper.injectAid(emvOpt, config)
                            Timber.tag(TAG).d("   │     └─ ✅ Injected successfully")

                            CardHelper.injectDefaultAIDs(emvOpt)
                        } catch (e: Exception) {
                            Timber.tag(TAG).e("   │     └─ ❌ Failed: ${e.message}")
                        }
                    }
                } else {
                    Timber.tag(TAG).w("   └─ ⚠️ No AID configs in terminal")
                }

                // Set terminal parameters
                Timber.tag(TAG).d("⚙️ Setting terminal parameters...")
                logTerminalParams(terminal)
                CardHelper.setTerminalParam(emvOpt, terminal)
                Timber.tag(TAG).d("   └─ ✅ Terminal parameters set")

            } else {
                Timber.tag(TAG).w("⚠️ No terminal provided - using default configuration")
            }

            // Step 4: Verify setup
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.tag(TAG).d("🔍 STEP 4: VERIFYING SETUP")
            Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            verifyAids()
            verifyTerminalConfig()

            isSetupCompleted = true

            Timber.tag(TAG).d("╔═══════════════════════════════════════════")
            Timber.tag(TAG).d("║ ✅ EMV SETUP COMPLETED SUCCESSFULLY")
            Timber.tag(TAG).d("╚═══════════════════════════════════════════")

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ EMV Setup failed")
            Timber.tag(TAG).e("├─ Exception: ${e.javaClass.simpleName}")
            Timber.tag(TAG).e("├─ Message: ${e.message}")
            Timber.tag(TAG).e("└─ StackTrace: ${e.stackTraceToString().take(1000)}")

            Result.failure(e)
        }
    }

    fun reset() {
        Timber.tag(TAG).d("🔄 Resetting EMV Setup Manager...")
        isSetupCompleted = false
        Timber.tag(TAG).d("   └─ Setup flag reset to: $isSetupCompleted")
    }

    private fun clearExistingData() {
        try {
            Timber.tag(TAG).d("🗑️ Clearing all existing EMV data...")

            // Delete all AIDs
            Timber.tag(TAG).d("   ├─ Deleting all AIDs...")
            val deleteAidResult = emvOpt.deleteAid(null)
            Timber.tag(TAG).d("   │  └─ Result: $deleteAidResult")

            // Delete all CAPKs
            Timber.tag(TAG).d("   ├─ Deleting all CAPKs...")
            val deleteCapkResult = emvOpt.deleteCapk(null, null)
            Timber.tag(TAG).d("   │  └─ Result: $deleteCapkResult")

            // Delete PIN key
            Timber.tag(TAG).d("   └─ Deleting PIN key (index: ${CardConstants.PIN_KEY_INDEX})...")
            val deletePinResult = securityOpt.deleteKey(CardConstants.PIN_KEY_INDEX, 0)
            Timber.tag(TAG).d("      └─ Result: $deletePinResult")

        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "⚠️ Error clearing EMV data (may be normal on first run)")
        }
    }

    private fun verifyAids() {
        try {
            Timber.tag(TAG).d("🔍 Verifying AIDs...")

            val aidList = mutableListOf<String>()
            val result = emvOpt.queryAidCapkList(0, aidList)

            Timber.tag(TAG).d("   ├─ Query result code: $result")

            if (result != 0) {
                Timber.tag(TAG).e("   └─ ❌ Failed to query AIDs (code: $result)")
                return
            }

            if (aidList.isEmpty()) {
                Timber.tag(TAG).e("   └─ ❌ NO AIDs found in terminal!")
                return
            }

            Timber.tag(TAG).d("   ├─ ✅ Total AIDs loaded: ${aidList.size}")

            // 🔥 Extract actual AIDs để check scheme support
            val extractedAids = mutableListOf<String>()

            // Log all AIDs with known card schemes
            aidList.forEachIndexed { index, aidTlv ->
                val scheme = identifyCardScheme(aidTlv)
                Timber.tag(TAG).d("   ├─ [$index] ${aidTlv.take(100)}... - $scheme")

                // 🔥 Extract AID value để check scheme
                try {
                    val afterTag = aidTlv.substring(4) // Skip "9F06"
                    val length = afterTag.substring(0, 2).toInt(16) * 2
                    val aid = afterTag.substring(2, 2 + length)
                    extractedAids.add(aid)
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }

            // 🔥 Check for common card schemes using EXTRACTED AIDs
            val hasVisa = extractedAids.any { it.startsWith("A000000003") }
            val hasMastercard = extractedAids.any { it.startsWith("A000000004") }
            val hasJCB = extractedAids.any { it.startsWith("A000000065") }
            val hasUnionPay = extractedAids.any { it.startsWith("A000000333") }

            Timber.tag(TAG).d("   └─ Card scheme support:")
            Timber.tag(TAG).d("      ├─ Visa: ${if (hasVisa) "✅" else "❌"}")
            Timber.tag(TAG).d("      ├─ Mastercard: ${if (hasMastercard) "✅" else "❌"}")
            Timber.tag(TAG).d("      ├─ JCB: ${if (hasJCB) "✅" else "❌"}")
            Timber.tag(TAG).d("      └─ UnionPay: ${if (hasUnionPay) "✅" else "❌"}")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to verify AIDs")
        }
    }

    private fun verifyCAPKs() {
        try {
            Timber.tag(TAG).d("🔍 Verifying CAPKs...")

            // Query CAPK list (type 1 for CAPKs)
            val capkList = mutableListOf<String>()
            val result = emvOpt.queryAidCapkList(1, capkList)

            Timber.tag(TAG).d("   ├─ Query result code: $result")

            if (result != 0) {
                Timber.tag(TAG).e("   └─ ❌ Failed to query CAPKs (code: $result)")
                return
            }

            if (capkList.isEmpty()) {
                Timber.tag(TAG).e("   └─ ❌ NO CAPKs found!")
                return
            }

            Timber.tag(TAG).d("   └─ ✅ Total CAPKs loaded: ${capkList.size}")

            // Log first few CAPKs
            capkList.take(5).forEachIndexed { index, capk ->
                Timber.tag(TAG).d("      ├─ [$index] $capk")
            }

            if (capkList.size > 5) {
                Timber.tag(TAG).d("      └─ ... and ${capkList.size - 5} more")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).w("⚠️ Could not verify CAPKs: ${e.message}")
        }
    }

    private fun verifyTerminalConfig() {
        try {
            Timber.tag(TAG).d("🔍 Verifying terminal configuration...")

            // Check important terminal TLV values
            val terminalTags = arrayOf(
                "9F16" to "Merchant ID",
                "9F1C" to "Terminal ID",
                "9F33" to "Terminal Capabilities",
                "9F40" to "Additional Terminal Capabilities",
                "9F35" to "Terminal Type",
                "9F1A" to "Terminal Country Code",
                "5F2A" to "Transaction Currency Code",
                "9F53" to "Transaction Category Code"
            )

            terminalTags.forEach { (tag, name) ->
                try {
                    val value = CardHelper.getTlvValue(emvOpt, tag)
                    if (value != null && value.isNotEmpty()) {
                        Timber.tag(TAG).d("   ├─ $tag ($name): $value")
                    } else {
                        Timber.tag(TAG).w("   ├─ ⚠️ $tag ($name): NOT SET")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w("   ├─ ⚠️ $tag ($name): Error reading")
                }
            }

            Timber.tag(TAG).d("   └─ Terminal config verification complete")

        } catch (e: Exception) {
            Timber.tag(TAG).w("⚠️ Could not verify terminal config: ${e.message}")
        }
    }

    private fun logTerminalParams(terminal: Terminal) {
        Timber.tag(TAG).d("   📊 Terminal Parameters:")
        Timber.tag(TAG).d("   ├─ Terminal ID: ${terminal.tid}")
        Timber.tag(TAG).d("   ├─ Merchant ID: ${terminal.mid}")
        Timber.tag(TAG).d("   └─ EMV Configs Count: ${terminal.evmConfigs?.size ?: 0}")
    }

    private fun identifyCardScheme(aidTlv: String): String {
        // 🔥 EXTRACT chỉ phần AID từ TLV string
        // Format: 9F06 [length] [AID value] ...
        // Ví dụ: 9F0607A0000000031010...
        //        → AID = A0000000031010

        if (!aidTlv.startsWith("9F06")) {
            return "INVALID FORMAT"
        }

        try {
            // Bỏ qua "9F06" (4 chars)
            val afterTag = aidTlv.substring(4)

            // Đọc length (2 chars = 1 byte)
            val lengthHex = afterTag.substring(0, 2)
            val length = lengthHex.toInt(16) * 2 // Convert to hex string length

            // Extract AID value
            val aid = afterTag.substring(2, 2 + length)

            Timber.tag(TAG).v("   Extracted AID: $aid from TLV: ${aidTlv.take(40)}...")

            // Now check the actual AID
            return when {
                aid.startsWith("A000000003") -> "VISA"
                aid.startsWith("A000000004") -> "MASTERCARD"
                aid.startsWith("A000000025") -> "AMEX"
                aid.startsWith("A000000065") -> "JCB"
                aid.startsWith("A000000333") -> "UNIONPAY"
                aid.startsWith("A000000152") -> "DISCOVER"
                aid.startsWith("D156000000") -> "BANKCARD (China)"
                aid.startsWith("A000000677") -> "INTERAC/NAPAS"
                aid.startsWith("A000000324") -> "DISCOVER"
                aid.startsWith("A00000002501") -> "AMEX"
                else -> "UNKNOWN ($aid)"
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to parse AID TLV: ${e.message}")
            return "PARSE ERROR"
        }
    }

    fun isSetupComplete(): Boolean = isSetupCompleted
}