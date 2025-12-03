package com.onefin.posapp.core.utils

import android.content.Context
import android.text.TextUtils
import com.atg.pos.domain.entities.payment.TLVUtil
import com.onefin.posapp.core.models.EvmConfig
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.CvmConfig
import com.onefin.posapp.core.models.data.RequestSale.Data.Card
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.bean.AidV2
import com.sunmi.pay.hardware.aidlv2.bean.CapkV2
import com.sunmi.pay.hardware.aidlv2.bean.EmvTermParamV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import java.util.Locale
import kotlin.text.ifEmpty
import kotlin.text.toInt


object EmvUtil {

    fun injectAids(context: Context, emvOptV2: EMVOptV2) {
        try {
            injectAidsFromJson(context, emvOptV2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun injectCapks(context: Context, emvOptV2: EMVOptV2) {
        try {
            injectCapksFromJson(context, emvOptV2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun setTerminalParam(emv: EMVOptV2, terminal: Terminal) {
        try {
            val termParam = EmvTermParamV2().apply {
                // 🔥 COPY Y CHANG TỪ LOG THÀNH CÔNG
                capability = "E0F8C8"           // Hỗ trợ Enciphered PIN online
                addCapability = "0300C00000"     // ⚠️ Khác với code cũ!
                terminalType = "22"
                countryCode = "0704"
                currencyCode = "0704"
                currencyExp = "02"

                // 🔥 KEY SETTINGS
                bypassPin = true        // Bypass offline PIN verification
                getDataPIN = true       // Cho phép lấy PIN data để encrypt online

                IsReadLogInCard = false
                TTQ = "26000080"
                accountType = "00"
                adviceFlag = true
                batchCapture = false
                bypassAllFlg = false
                ectSiFlg = true
                ectSiVal = true
                ectTlFlg = true
                ectTlVal = "100000"
                forceOnline = false
                ifDsn = "3030303030393035"
                isSupportAccountSelect = true
                isSupportExceptFile = true
                isSupportMultiLang = true
                isSupportSM = true
                isSupportTransLog = true
                scriptMode = false
                surportPSESel = true
                termAIP = true
                useTermAIPFlg = true
            }
            emv.setTerminalParam(termParam)

        } catch (_: Exception) {
        }
    }
    fun setEmvTlvs(context: Context, emv: EMVOptV2, terminal: Terminal?) {
        val evmConfigs = terminal?.evmConfigs
        setGlobalTlvs(emv, terminal)

        // 🔥 MỚI: Load CVM config từ JSON
        val cvmConfig = ResourceHelper.loadCvmFromAssets(context)
        evmConfigs?.forEach { config ->
            when (config.vendorName.uppercase(Locale.getDefault())) {
                "JCB" -> setJcbTlvs(emv, config, cvmConfig)
                "NAPAS" -> setNapasTlvs(emv, config)
                "VISA" -> setPayWaveTlvs(emv, config, cvmConfig)
                "MASTERCARD" -> setPayPassTlvs(emv, config, cvmConfig)
                "UNIONPAY", "UNION PAY" -> setQpbocTlvs(emv, config, cvmConfig)
                "AMEX", "AMERICAN EXPRESS" -> setExpressPayTlvs(emv, config, cvmConfig)
            }
        }
    }
    fun injectKeys(securityOpt: SecurityOptV2, terminal: Terminal): Boolean {
        return try {
            // ksn
            val ksn = terminal.ksn ?: "FFFF4357480393800000"
            val ksnBytes = UtilHelper.hexStringToByteArray(ksn)

            // bdk
            val bdk = terminal.bdk ?: "C1D0F8FB4958670DBA40AB1F3752EF0D"
            val bdkBytes = UtilHelper.hexStringToByteArray(bdk)

            val keyType = 7
            val keyIndex = 1
            val result = securityOpt.saveKeyDukpt(
                keyType,
                bdkBytes,
                null,
                ksnBytes,
                1,              // algorithmType DES
                keyIndex
            )
            result == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun setGlobalTlvs(emv: EMVOptV2, terminal: Terminal?) {
        val config: EvmConfig = terminal?.evmConfigs?.firstOrNull() ?: EvmConfig()

        val globalTags = arrayOf(
            "9F1A", "5F2A", "5F36", "9F33", "9F35", "9F40",
            "9F66", "9F09", "9F1C", "9F15", "9F16", "9F1E"
        )

        // ✅ Use fixed universal values that work for ALL card types (NAPAS, VISA, MasterCard)
        // Don't rely on first config which might be wrong vendor
        val terminal9F40 = "0300C00000"  // Terminal Capability - works for all
        val terminal9F33 = "E0F8C8"      // Terminal Capabilities - includes CDA support
        val ttq = "26000080"              // TTQ - works for NAPAS and VISA

        timber.log.Timber.d("🔵 [EMV] Setting Global TLVs - Using universal values for all vendors")

        val globalValues = arrayOf(
            config.countryCode9F1A.ifEmpty { "0704" },
            config.transCurrencyCode5F2A.ifEmpty { "0704" },
            config.transCurrencyExp.ifEmpty { "02" },
            terminal9F33,
            config.terminalType9F35.ifEmpty { "22" },
            terminal9F40,
            ttq,
            config.version9F09.ifEmpty { "0002" },
            terminal?.tid ?: config.terminalId9F1C.ifEmpty { "R1010033" },
            config.mcc9F15.ifEmpty { "9999" },
            terminal?.mid ?: config.merchantId9F16.ifEmpty { "101234230000004" },
            "00000001"
        )

        timber.log.Timber.d("🔵 [EMV] Global TLVs - 9F40=$terminal9F40, 9F33=$terminal9F33, 9F66=$ttq")

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, globalTags, globalValues)
    }
    private fun setJcbTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125", "DF8126"
        )

        val chipCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "jcb", "chip")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val contactlessCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "jcb", "contactless")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val floorLimit = config.floorLimit9F1B.ifEmpty { "000000500000" }

        val chipValues = arrayOf(
            floorLimit,
            floorLimit,
            floorLimit,
            "E8",
            "00",
            floorLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            chipCvm.cvmRequiredLimit
        )

        val contactlessValues = arrayOf(
            floorLimit,
            floorLimit,
            floorLimit,
            contactlessCvm.contactlessTransLimit,
            contactlessCvm.cvmRequiredLimit,
            contactlessCvm.readerCvmRequiredLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            contactlessCvm.cvmRequiredLimit
        )

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_JCB, tags, contactlessValues)
    }
    private fun setQpbocTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        val chipValues = arrayOf("E0", "F8", "F8", "E8", "00")
        val tags = arrayOf("DF69", "DF70", "DF71", "DF72", "DF73")
        val contactlessValues = arrayOf("E0", "F8", "F8", "E8", "00")
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, contactlessValues)
    }
    private fun setNapasTlvs(emv: EMVOptV2, config: EvmConfig) {
        timber.log.Timber.d("🟢 [EMV] Setting NAPAS-specific TLVs")

        // 1. SET TERMINAL CAPABILITIES FOR NAPAS KERNEL 2
        val terminalTags = arrayOf(
            "9F1A", "5F2A", "5F36", "9F33", "9F35", "9F40",
            "9F66", "9F09", "9F1C", "9F15", "9F16", "9F1E"
        )

        val terminalValues = arrayOf(
            "0704",              // Country Code
            "0704",              // Currency Code
            "02",                // Currency Exponent
            "E0F8C8",            // Terminal Capabilities (same as VISA)
            "22",                // Terminal Type
            "0300C00000",        // ✅ FIXED: Match successful log (was "6000F0A001")
            "26000080",          // TTQ
            "0002",              // App Version
            "R1010033",
            "9999",
            "101234230000004",
            "00000001"
        )

        timber.log.Timber.d("🟢 [EMV] NAPAS Terminal 9F40: ${terminalValues[5]}")
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, terminalTags, terminalValues)

        // 2. SET NAPAS KERNEL PARAMETERS (opCode 0 and 1)
        val napasTags = arrayOf(
            "DF7F", "DF8134", "DF8133", "9F66", "DF8117", "DF8118", "DF8119",
            "DF811A", "DF811B", "DF811D", "DF811E", "DF811F",
            "DF8120", "DF8121", "DF8122", "DF8123", "DF8124", "DF8125", "DF812C"
        )

        val napasChipValues = arrayOf(
            "A0000007271010", "D9", "3200E043F9", "26000080",
            "E0", "08", "F0", "9F6A04", "30", "02", "000000000000", "08",
            "BCF8049800", "0000000000", "BCF8049800", "BCF8049800",  // ✅ FIXED TAC: Force online
            "999999999999", "999999999999", "000000000000"
        )

        val napasContactlessValues = arrayOf(
            "A0000007271010", "D9", "3200E043F9", "26000080",
            "E0", "08", "F0", "9F6A04", "30", "02", "000000000000", "08",
            "BCF8049800", "0000000000", "BCF8049800", "BCF8049800",  // ✅ FIXED TAC: Force online
            "000000000000", "000000000000", "08"
        )

        timber.log.Timber.d("🔵 [EMV] NAPAS Chip: ${napasChipValues.contentToString()}")
        timber.log.Timber.d("🔵 [EMV] NAPAS Contactless: ${napasContactlessValues.contentToString()}")

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, napasTags, napasChipValues)
        // ⭐ Use OP_PURE (6) for NAPAS Pure contactless, not OP_PAYPASS
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_PURE, napasTags, napasContactlessValues)
    }

    /**
     * Update NAPAS Pure TLV dynamically based on transaction amount.
     * Following ATG.POS architecture: initEmvTlvNapas()
     *
     * @param emv EMVOptV2 instance
     * @param terminal Terminal configuration
     * @param amount Transaction amount in smallest currency unit (e.g., cents for VND)
     * @param flagRc85 RC85 flag (reserved for future use)
     */
    fun updateNapasPureTlvForTransaction(
        emv: EMVOptV2,
        terminal: Terminal?,
        amount: Long,
        flagRc85: Boolean = false
    ) {
        try {
            // Tags specific to NAPAS Pure (following ATG.POS pattern)
            val tags = arrayOf("DF7F", "DF8134", "DF8133")

            // DF8133 value depends on amount and RC85 flag
            // ⭐ Following ATG.POS logic:
            // - If amount <= 1,000,000 VNĐ AND !flagRc85: use "3200E043F9"
            // - Otherwise: use "3600E043F9"
            val df8133 = if (!flagRc85 && amount <= 1_000_000) {
                "3200E043F9"
            } else {
                "3600E043F9"
            }

            val values = arrayOf(
                "A0000007271010",  // DF7F: NAPAS AID
                "D9",                                           // DF8134: NAPAS specific tag
                df8133                                          // DF8133: Dynamic based on amount
            )

            timber.log.Timber.d("🔵 [EMV] NAPAS Pure TLV Update - amount=$amount, flagRc85=$flagRc85, DF8133=$df8133")

            // Set TLV with OP_PURE (OpCode 6) for NAPAS Pure contactless
            emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_PURE, tags, values)

            timber.log.Timber.d("🔵 [EMV] NAPAS Pure TLV updated successfully")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "🔴 [EMV] Failed to update NAPAS Pure TLV")
        }
    }

    private fun setPayWaveTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811B", "DF811D", "DF811E",
            "DF811F", "DF8120", "DF8121", "DF8122", "DF8123", "DF8124",
            "DF8125", "DF812C"
        )

        val chipCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "visa", "chip")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val contactlessCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "visa", "contactless")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val floorLimit = config.floorLimit9F1B.ifEmpty { "000005000000" }
        val chipValues = arrayOf(
            floorLimit,
            "000000000000",
            "000000999999",
            "30",
            "02",
            chipCvm.cvmRequiredLimit,
            chipCvm.contactlessTransLimit,
            "000000999999",
            chipCvm.contactlessCvmLimit,
            "0000000000",
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            chipCvm.readerCvmRequiredLimit
        )

        val contactlessValues = arrayOf(
            floorLimit,
            "000000000000",
            "000000999999",
            "30",
            "02",
            contactlessCvm.cvmRequiredLimit,
            contactlessCvm.contactlessTransLimit,
            "000000999999",
            contactlessCvm.contactlessCvmLimit,
            "0000000000",
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            contactlessCvm.readerCvmRequiredLimit
        )

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_PAYWAVE, tags, contactlessValues)
    }
    private fun setPayPassTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125", "DF8126", "DF811B", "DF811D",
            "DF8122", "DF8120", "DF8121"
        )

        val chipCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "master", "chip")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val contactlessCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "master", "contactless")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val floorLimit = config.floorLimit9F1B.ifEmpty { "000000500000" }

        val chipValues = arrayOf(
            floorLimit,
            floorLimit,
            floorLimit,
            "E8",
            "00",
            chipCvm.readerCvmRequiredLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            chipCvm.cvmRequiredLimit,
            "30",
            "02",
            "0000000000",
            "000000000000",
            "000000000000"
        )

        val contactlessValues = arrayOf(
            floorLimit,
            floorLimit,
            floorLimit,
            contactlessCvm.contactlessTransLimit,
            contactlessCvm.cvmRequiredLimit,
            contactlessCvm.readerCvmRequiredLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            contactlessCvm.cvmRequiredLimit,
            "30",
            "02",
            "0000000000",
            "000000000000",
            contactlessCvm.contactlessCvmLimit
        )

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_PAYPASS, tags, contactlessValues)
    }
    private fun setExpressPayTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125"
        )

        val chipCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "amex", "chip")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val contactlessCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "amex", "contactless")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val chipValues = arrayOf(
            "E0",
            "F8",
            "F8",
            "E8",
            chipCvm.cvmRequiredLimit,
            chipCvm.readerCvmRequiredLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline
        )

        val contactlessValues = arrayOf(
            "E0",
            "F8",
            "F8",
            contactlessCvm.contactlessTransLimit,
            contactlessCvm.cvmRequiredLimit,
            contactlessCvm.readerCvmRequiredLimit,
            config.tacDefault,
            config.tacDenial,
            config.tacOnline
        )

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_AE, tags, contactlessValues)
    }

    private fun char2Byte(c: Char): Int {
        if (c >= 'a') {
            return (c.code - 'a'.code + 10) and 0x0f
        }
        if (c >= 'A') {
            return (c.code - 'A'.code + 10) and 0x0f
        }
        return (c.code - '0'.code) and 0x0f
    }
    private fun hexStr2Byte(hexStr: String): Byte {
        return hexStr.toInt(16).toByte()
    }
    private  fun hexStr2Aid(hexStr: String?): AidV2 {
        val aidV2 = AidV2()
        val map = TLVUtil.buildTLVMap(hexStr)
        var tlv = map["DF21"]
        if (tlv != null) {
            aidV2.cvmLmt = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF20"]
        if (tlv != null) {
            aidV2.termClssLmt = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF19"]
        if (tlv != null) {
            aidV2.termClssOfflineFloorLmt = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F7B"]
        if (tlv != null) {
            aidV2.termOfflineFloorLmt = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F06"]
        if (tlv != null) {
            aidV2.aid = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF01"]
        if (tlv != null) {
            aidV2.selFlag = hexStr2Byte(tlv.value)
        }
        tlv = map["DF17"]
        if (tlv != null) {
            aidV2.targetPer = hexStr2Byte(tlv.value)
        }
        tlv = map["DF16"]
        if (tlv != null) {
            aidV2.maxTargetPer = hexStr2Byte(tlv.value)
        }
        tlv = map["9F1B"]
        if (tlv != null) {
            aidV2.floorLimit = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF15"]
        if (tlv != null) {
            aidV2.threshold = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF13"]
        if (tlv != null) {
            aidV2.TACDenial = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF12"]
        if (tlv != null) {
            aidV2.TACOnline = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF11"]
        if (tlv != null) {
            aidV2.TACDefault = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F01"]
        if (tlv != null) {
            aidV2.AcquierId = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF14"]
        if (tlv != null) {
            aidV2.dDOL = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F09"]
        if (tlv != null) {
            aidV2.version = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F4E"]
        if (tlv != null) {
            aidV2.merchName = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F15"]
        if (tlv != null) {
            aidV2.merchCateCode = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F16"]
        if (tlv != null) {
            aidV2.merchId = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F3C"]
        if (tlv != null) {
            aidV2.referCurrCode = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F3D"]
        if (tlv != null) {
            aidV2.referCurrExp = hexStr2Byte(tlv.value)
        }
        tlv = map["DFC108"]
        if (tlv != null) {
            aidV2.clsStatusCheck = hexStr2Byte(tlv.value)
        }
        tlv = map["DFC109"]
        if (tlv != null) {
            aidV2.zeroCheck = hexStr2Byte(tlv.value)
        }
        tlv = map["DFC10A"]
        if (tlv != null) {
            aidV2.kernelType = hexStr2Byte(tlv.value)
        }
        tlv = map["DFC10B"]
        if (tlv != null) {
            aidV2.paramType = hexStr2Byte(tlv.value)
        }
        tlv = map["9F66"]
        if (tlv != null) {
            aidV2.ttq = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F1C"]
        if (tlv != null) {
            aidV2.termId = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F1D"]
        if (tlv != null) {
            aidV2.riskManData = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF8101"]
        if (tlv != null) {
            aidV2.referCurrCon = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF8102"]
        if (tlv != null) {
            aidV2.tDOL = hexStr2Bytes(tlv.value)
        }
        tlv = map["DFC10C"]
        if (tlv != null) {
            aidV2.kernelID = hexStr2Bytes(tlv.value)
        }
        return aidV2
    }
    private fun hexStr2Rid(hexStr: String?): CapkV2 {
        val capkV2 = CapkV2()
        val map = TLVUtil.buildTLVMap(hexStr)
        var tlv = map["9F06"]
        if (tlv != null) {
            capkV2.rid = hexStr2Bytes(tlv.value)
        }
        tlv = map["9F22"]
        if (tlv != null) {
            capkV2.index = hexStr2Byte(tlv.value)
        }
        tlv = map["DF06"]
        if (tlv != null) {
            capkV2.hashInd = hexStr2Byte(tlv.value)
        }
        tlv = map["DF07"]
        if (tlv != null) {
            capkV2.arithInd = hexStr2Byte(tlv.value)
        }
        tlv = map["DF02"]
        if (tlv != null) {
            capkV2.modul = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF04"]
        if (tlv != null) {
            capkV2.exponent = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF05"]
        if (tlv != null) {
            capkV2.expDate = hexStr2Bytes(tlv.value)
        }
        tlv = map["DF03"]
        if (tlv != null) {
            capkV2.checkSum = hexStr2Bytes(tlv.value)
        }
        return capkV2
    }
    private fun hexStr2Bytes(hexStr: String?): ByteArray {
        if (TextUtils.isEmpty(hexStr)) {
            return ByteArray(0)
        }
        val length = hexStr!!.length / 2
        val chars = hexStr.toCharArray()
        val b = ByteArray(length)
        for (i in 0..<length) {
            b[i] = (char2Byte(chars[i * 2]) shl 4 or char2Byte(chars[i * 2 + 1])).toByte()
        }
        return b
    }
    private fun injectAidsFromJson(context: Context, emvOptV2: EMVOptV2): Int {
        try {
            val aidList = ResourceHelper.loadAidsFromAssets(context) ?: run {
                return 0
            }

            var successCount = 0
            for ((index, aidData) in aidList.withIndex()) {
                val entry = aidData.getEntry() ?: continue
                val (type, aidEntry) = entry

                try {
                    val aidV2 = ResourceHelper.convertToAidV2(aidEntry, type)
                    val result = emvOptV2.addAid(aidV2)
                    if (result == 0) {
                        successCount++
                    }
                } catch (_: Exception) {
                }
            }
            return successCount
        } catch (e: Exception) {
            return 0
        }
    }
    private fun injectCapksFromJson(context: Context, emvOptV2: EMVOptV2): Int {
        try {
            val capkList = ResourceHelper.loadCapksFromAssets(context) ?: run {
                return 0
            }

            var successCount = 0
            for (capkData in capkList) {
                try {
                    val capkV2 = ResourceHelper.convertToCapkV2(capkData)
                    val result = emvOptV2.addCapk(capkV2)

                    if (result == 0) {
                        successCount++
                    }
                } catch (_: Exception) {
                }
            }
            return successCount

        } catch (e: Exception) {
            return 0
        }
    }

    fun removePaddingCard(card: Card): Card {
        val pan = card.clearPan
        val track2 = card.track2
        if (pan.endsWith("F", ignoreCase = true)) {
            card.clearPan = pan.dropLast(1)
            if (card.emvData != null)
                card.emvData = card.emvData!!.replace(pan, card.clearPan)
        }
        if (track2 != null && track2.endsWith("F", ignoreCase = true)) {
            card.track2 = track2.dropLast(1)
            if (card.emvData != null)
                card.emvData = card.emvData!!.replace(track2, card.track2.toString())
        }
        return card
    }
}
