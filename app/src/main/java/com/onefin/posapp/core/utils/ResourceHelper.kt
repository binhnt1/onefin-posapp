package com.onefin.posapp.core.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.core.models.data.AidData
import com.onefin.posapp.core.models.data.AidEntry
import com.onefin.posapp.core.models.data.CapkData
import com.onefin.posapp.core.models.data.CvmConfig
import com.onefin.posapp.core.models.data.CvmTlvValues
import com.sunmi.pay.hardware.aidlv2.bean.AidV2
import com.sunmi.pay.hardware.aidlv2.bean.CapkV2
import timber.log.Timber

object ResourceHelper {
    fun loadCvmFromAssets(context: Context): CvmConfig? {
        try {
            val inputStream = context.assets.open("CVM.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val cvmConfig: CvmConfig = Gson().fromJson(jsonString, CvmConfig::class.java)
            return cvmConfig

        } catch (e: Exception) {
            Timber.e(e, "❌ Lỗi khi load CVM từ JSON")
            return null
        }
    }

    fun loadAidsFromAssets(context: Context): List<AidData>? {
        try {
            val inputStream = context.assets.open("AID.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val gson = Gson()
            val listType = object : TypeToken<List<AidData>>() {}.type
            val aidList: List<AidData> = gson.fromJson(jsonString, listType)
            return aidList

        } catch (e: Exception) {
            Timber.e(e, "❌ Lỗi khi load AID từ JSON")
            return null
        }
    }

    fun loadCapksFromAssets(context: Context): List<CapkData>? {
        try {
            val inputStream = context.assets.open("CAPK.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val listType = object : TypeToken<List<CapkData>>() {}.type
            val capkList: List<CapkData> = gson.fromJson(jsonString, listType)
            return capkList

        } catch (e: Exception) {
            Timber.e(e, "❌ Lỗi khi load CAPK từ JSON")
            return null
        }
    }

    fun getDefaultTlvValues(): CvmTlvValues {
        return CvmTlvValues(
            cvmRequiredLimit = "000000000000",
            readerCvmRequiredLimit = "000000000000",
            pinRequiredLimit = "000000000000",
            contactlessTransLimit = "999999999999",
            contactlessCvmLimit = "999999999999",
            isSignatureRequired = true,
            isPinRequired = false
        )
    }
    fun convertToCapkV2(capkData: CapkData): CapkV2 {
        return CapkV2().apply {
            rid = hexStr2Bytes(capkData.rid ?: "")
            index = hexStr2Byte(capkData.index ?: "01")
            hashInd = hexStr2Byte(capkData.hashAlgo ?: "01") // SHA-1
            arithInd = hexStr2Byte(capkData.arithInd ?: "01") // RSA
            modul = hexStr2Bytes(capkData.modul ?: "")
            exponent = hexStr2Bytes(capkData.exponent ?: "03")
            expDate = hexStr2Bytes(capkData.expDate ?: "491231")
            checkSum = hexStr2Bytes(capkData.checkSum ?: "")
        }
    }
    fun convertToAidV2(aidEntry: AidEntry, type: String): AidV2 {
        val baseAid = aidEntry.baseAid

        return AidV2().apply {
            // Base AID fields
            aid = hexStr2Bytes(baseAid.aid)
            version = hexStr2Bytes(baseAid.version ?: "0002")

            // TAC
            TACDefault = hexStr2Bytes(baseAid.tacDefault ?: "DC4000A800")
            TACOnline = hexStr2Bytes(baseAid.tacOnline ?: "DC4004F800")
            TACDenial = hexStr2Bytes(baseAid.tacDenial ?: "0010000000")

            // Floor Limit
            floorLimit = hexStr2Bytes(baseAid.floorLimit ?: "000000000000")

            dDOL = hexStr2Bytes(baseAid.defaultDdol ?: "9F37049F34039F3501")
            tDOL = hexStr2Bytes(baseAid.defaultTdol ?: "")

            // Merchant Info
            merchCateCode = hexStr2Bytes(baseAid.mcc ?: "4112")
            merchId = stringToByteArray(baseAid.merchantId, 15)
            merchName = stringToByteArray(baseAid.merchantName, 128)
            termId = stringToByteArray(baseAid.terminalId, 8)

            // Risk Management
            riskManData = hexStr2Bytes(baseAid.riskManagementData ?: "647A800000000000")
            rMDLen = riskManData?.size?.toByte() ?: 0

            // Terminal Capabilities
            // Sẽ được set qua setTerminalParam và setTlvList

            // Selection Flags
            selFlag = 0x00.toByte() // Partial match
            targetPer = 0x00.toByte()
            maxTargetPer = 0x63.toByte() // 99

            // Feature Flags
            randTransSel = 0x01.toByte()
            velocityCheck = 0x00.toByte()

            // Threshold & Acquirer
            threshold = hexStr2Bytes("00000000")
            AcquierId = hexStr2Bytes(baseAid.acquirerId ?: "000000000000")

            // Currency (VND)
            referCurrCode = hexStr2Bytes(baseAid.transCurrencyCode ?: "0704")
            referCurrExp = 0x02.toByte()
            referCurrCon = hexStr2Bytes("0000")

            // Kernel Settings
            clsStatusCheck = 0x00.toByte()
            zeroCheck = 0x00.toByte()
            kernelType = getKernelType(type)
            // paramType: 0x00=Contact only, 0x01=Both, 0x02=Contactless only
            paramType = when (type) {
                "pure" -> 0x02.toByte()  // NAPAS Pure is contactless only
                else -> 0x01.toByte()     // Others support both
            }

            // TTQ - Lấy từ specific AID nếu có
            ttq = when (type) {
                "paypass" -> hexStr2Bytes(aidEntry.paypassAid?.ttq ?: "3600C080")
                "paywave" -> hexStr2Bytes(aidEntry.paywaveAid?.ttq ?: "3600C080")
                // NAPAS Pure: Use mCTLSAppCapa if available, otherwise use DF8133 value
                "pure" -> hexStr2Bytes(
                    aidEntry.pureAid?.mctlsAppCapa ?: "3200E043F9"
                )
                "qpboc" -> hexStr2Bytes(aidEntry.qpbocAid?.ttq ?: "3600C080")
                else -> hexStr2Bytes("")
            }

            // KernelID must match TLV OpCode for contactless processing
            kernelID = when (type) {
                "paypass" -> hexStr2Bytes("02") // OP_PAYPASS + 1
                "paywave" -> hexStr2Bytes("03") // OP_PAYWAVE + 1
                "pure" -> hexStr2Bytes("06")     // OP_PURE (NAPAS)
                "jcb" -> hexStr2Bytes("05")      // OP_JCB
                "qpboc" -> hexStr2Bytes("03")    // QuickPass uses 03
                else -> hexStr2Bytes("")
            }
            extSelectSupFlg = 0x00.toByte()

            // Contactless Limits - Lấy từ specific AID
            when (type) {
                "paypass" -> {
                    val paypassAid = aidEntry.paypassAid
                    cvmLmt = hexStr2Bytes(paypassAid?.clCvmLimit ?: "000000000000")
                    termClssLmt = hexStr2Bytes(paypassAid?.clTransLimitNoCdcvm ?: "999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes(paypassAid?.clFloorLimit ?: "000000000000")
                }
                "paywave" -> {
                    val paywaveAid = aidEntry.paywaveAid
                    cvmLmt = hexStr2Bytes(paywaveAid?.clCvmLimit ?: "000000000000")
                    termClssLmt = hexStr2Bytes(paywaveAid?.clTransLimit ?: "999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes(paywaveAid?.clFloorLimit ?: "000000000000")
                }
                "pure" -> {
                    val pureAid = aidEntry.pureAid
                    cvmLmt = hexStr2Bytes(pureAid?.clCvmLimit ?: "000000000000")
                    termClssLmt = hexStr2Bytes(pureAid?.clTransLimit ?: "999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes(pureAid?.clFloorLimit ?: "000000000000")
                }
                "jcb" -> {
                    val jcbAid = aidEntry.jcbAid
                    cvmLmt = hexStr2Bytes(jcbAid?.clCvmLimit ?: "000000000000")
                    termClssLmt = hexStr2Bytes(jcbAid?.clTransLimit ?: "999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes(jcbAid?.clFloorLimit ?: "000000000000")
                }
                "qpboc" -> {
                    val qpbocAid = aidEntry.qpbocAid
                    cvmLmt = hexStr2Bytes(qpbocAid?.clCvmLimit ?: "000000000000")
                    termClssLmt = hexStr2Bytes(qpbocAid?.clTransLimit ?: "999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes(qpbocAid?.clFloorLimit ?: "000000000000")
                }
                else -> {
                    cvmLmt = hexStr2Bytes("000000000000")
                    termClssLmt = hexStr2Bytes("999999999999")
                    termClssOfflineFloorLmt = hexStr2Bytes("000000000000")
                }
            }

            termOfflineFloorLmt = hexStr2Bytes("000000000000")
        }
    }
    fun convertToTlv(cvmConfig: CvmConfig, cardBrand: String, entryMode: String): CvmTlvValues {

        // Lấy config cho card brand
        val brandConfig = when (cardBrand.lowercase()) {
            "jcb" -> cvmConfig.sale.jcb
            "visa" -> cvmConfig.sale.visa
            "napas" -> cvmConfig.sale.napas
            "master", "mastercard" -> cvmConfig.sale.master
            "unionpay", "union pay" -> cvmConfig.sale.unionpay
            else -> null
        }

        // Lấy config cho entry mode
        if (brandConfig != null) {
            val modeConfig = when (entryMode.lowercase()) {
                "chip" -> brandConfig.chip
                "magstripe" -> brandConfig.magstripe
                "contactless" -> brandConfig.contactless
                else -> null
            }

            if (modeConfig != null) {
                val signatureRule = modeConfig.signature
                val signatureAmount = signatureRule?.amount ?: 0L
                val needSignature = signatureRule?.isSignatureRequired() ?: false

                // Parse PIN rules
                val pinRule = modeConfig.pin
                val pinAmount = pinRule?.amount ?: 0L
                val needPin = pinRule?.isPinRequired() ?: false

                // Convert amounts to hex strings (12 characters)
                val signatureLimitHex = amountToHex12(signatureAmount)
                val pinLimitHex = amountToHex12(pinAmount)

                // Logic cho contactless
                val contactlessLimit = if (entryMode.lowercase() == "contactless") {
                    if (signatureAmount > 0) {
                        signatureLimitHex
                    } else {
                        "999999999999"
                    }
                } else {
                    "999999999999"
                }
                val contactlessCvmLimit = if (entryMode.lowercase() == "contactless") {
                    if (needSignature || needPin) {
                        minOf(signatureAmount, pinAmount).let {
                            if (it == 0L) signatureLimitHex else amountToHex12(it)
                        }
                    } else {
                        "999999999999"
                    }
                } else {
                    "999999999999"
                }
                return CvmTlvValues(
                    isPinRequired = needPin,
                    pinRequiredLimit = pinLimitHex,
                    isSignatureRequired = needSignature,
                    cvmRequiredLimit = signatureLimitHex,
                    contactlessTransLimit = contactlessLimit,
                    contactlessCvmLimit = contactlessCvmLimit,
                    readerCvmRequiredLimit = signatureLimitHex,
                )
            }
        }
        return getDefaultTlvValues()
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
    private fun getKernelType(type: String): Byte {
        return when (type) {
            "paypass" -> 0x02.toByte() // MasterCard Contactless
            "paywave" -> 0x03.toByte() // Visa Contactless
            "pure" -> 0x06.toByte()    // NAPAS Pure - must match kernelID and OP_PURE
            "jcb" -> 0x04.toByte()     // JCB
            "qpboc" -> 0x07.toByte()   // UnionPay
            else -> 0x00.toByte()      // Standard EMV
        }
    }
    private fun amountToHex12(amount: Long): String {
        return amount.toString().padStart(12, '0')
    }
    private fun hexStr2Bytes(hexStr: String?): ByteArray {
        if (hexStr.isNullOrEmpty()) {
            return ByteArray(0)
        }
        val length = hexStr.length / 2
        val chars = hexStr.toCharArray()
        val b = ByteArray(length)
        for (i in 0 until length) {
            b[i] = (char2Byte(chars[i * 2]) shl 4 or char2Byte(chars[i * 2 + 1])).toByte()
        }
        return b
    }
    private fun stringToByteArray(str: String?, fixedLength: Int): ByteArray {
        val result = ByteArray(fixedLength)
        if (str != null && str.isNotEmpty()) {
            val bytes = str.toByteArray(Charsets.US_ASCII)
            val lengthToCopy = minOf(bytes.size, fixedLength)
            System.arraycopy(bytes, 0, result, 0, lengthToCopy)
        }
        return result
    }
}