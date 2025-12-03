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
                "NAPAS" -> setNapasTlvs(emv, config, cvmConfig)
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

        val ttq = when (config.vendorName.uppercase(Locale.getDefault())) {
            "VISA" -> "26000080"
            "MASTERCARD" -> "3600C080"
            "NAPAS" -> "26000080"  // NAPAS Pure TTQ - Fixed to match AID.json and TLV config
            else -> "3600C080"
        }
        timber.log.Timber.d("🔵 [EMV] Setting Global TLVs - vendorName=${config.vendorName}, TTQ(9F66)=$ttq")

        val globalValues = arrayOf(
            config.countryCode9F1A,
            config.transCurrencyCode5F2A,
            config.transCurrencyExp,
            config.terminalCap9F33,
            config.terminalType9F35,
            config.exTerminalCap9F40,
            ttq,
            config.version9F09,
            terminal?.tid ?: config.terminalId9F1C,
            config.mcc9F15,
            terminal?.mid ?: config.merchantId9F16,
            "00000001"
        )

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
    private fun setNapasTlvs(emv: EMVOptV2, config: EvmConfig, cvmConfig: CvmConfig?) {
        timber.log.Timber.d("🔵 [EMV] Setting NAPAS Pure TLVs")
        val tags = arrayOf(
            "DF7F",   // AID - Application Identifier
            "DF8134", // NAPAS-specific tag
            "DF8133", // NAPAS-specific tag (mCTLSAppCapa)
            "9F66",   // TTQ - Terminal Transaction Qualifiers
            "DF8117", // cardDataInputCap
            "DF8118", // chipCVMCap
            "DF8119", // chipCVMCapNoCVM
            "DF811A", // UDOL - User Data Object List
            "DF811B", // kernelConfig
            "DF811D", // Status check
            "DF811E", // MSDCVMCap
            "DF811F", // securityCap
            "DF8120", // ClTACDefault
            "DF8121", // CLTACDenial
            "DF8122", // CLTACOnline
            "DF8123", // TAC Default
            "DF8124", // CLTransLimitNoCDCVM
            "DF8125", // CLTransLimitCDCVM
            "DF812C"  // MSDCVMCapNoCVM
        )

        val chipCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "napas", "chip")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val contactlessCvm = if (cvmConfig != null) {
            ResourceHelper.convertToTlv(cvmConfig, "napas", "contactless")
        } else {
            ResourceHelper.getDefaultTlvValues()
        }

        val chipValues = arrayOf(
            "A0000007271010",                              // DF7F - Force NAPAS AID
            "D9",                                          // DF8134
            "3200E043F9",                                  // DF8133
            "26000080",                                    // 9F66
            "E0",                                          // DF8117
            "08",                                          // DF8118
            "F0",                                          // DF8119
            "9F6A04",                                      // DF811A
            "30",                                          // DF811B
            "02",                                          // DF811D
            chipCvm.cvmRequiredLimit,                      // DF811E
            "08",                                          // DF811F
            config.tacDefault,                             // DF8120
            config.tacDenial,                              // DF8121
            config.tacOnline,                              // DF8122
            config.tacDefault,                             // DF8123
            chipCvm.contactlessTransLimit,                 // DF8124
            chipCvm.contactlessCvmLimit,                   // DF8125
            chipCvm.readerCvmRequiredLimit                 // DF812C
        )

        val contactlessValues = arrayOf(
            "A0000007271010",                              // DF7F - Force NAPAS AID
            "D9",                                          // DF8134
            "3200E043F9",                                  // DF8133
            "26000080",                                    // 9F66
            "E0",                                          // DF8117
            "08",                                          // DF8118
            "F0",                                          // DF8119
            "9F6A04",                                      // DF811A
            "30",                                          // DF811B
            "02",                                          // DF811D
            contactlessCvm.cvmRequiredLimit,               // DF811E
            "08",                                          // DF811F
            "0000000000",                                  // DF8120 - ClTACDefault (all 0s for approval)
            "0000000000",                                  // DF8121 - CLTACDenial
            "0000000000",                                  // DF8122 - CLTACOnline (all 0s for offline approval)
            config.tacDefault,                             // DF8123
            contactlessCvm.contactlessTransLimit,          // DF8124
            contactlessCvm.contactlessCvmLimit,            // DF8125
            "08"                                           // DF812C
        )

        timber.log.Timber.d("🔵 [EMV] NAPAS Pure OP_PURE TLVs: " +
            "AID=${contactlessValues[0]}, " +
            "TTQ(9F66)=${contactlessValues[3]}, " +
            "napasTag(DF8134)=${contactlessValues[1]}, " +
            "mCTLSAppCapa(DF8133)=${contactlessValues[2]}, " +
            "kernelConfig(DF811B)=${contactlessValues[8]}, " +
            "ClTACDefault(DF8120)=${contactlessValues[12]}, " +
            "CLTACDenial(DF8121)=${contactlessValues[13]}, " +
            "CLTACOnline(DF8122)=${contactlessValues[14]}")

        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_NORMAL, tags, chipValues)
        emv.setTlvList(AidlConstants.EMV.TLVOpCode.OP_PURE, tags, contactlessValues)
        timber.log.Timber.d("🔵 [EMV] NAPAS Pure TLVs set successfully")
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
