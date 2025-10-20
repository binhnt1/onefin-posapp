package com.onefin.posapp.core.utils

import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.EvmConfig
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.EmvCardData
import com.onefin.posapp.core.models.data.MagneticCardData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.models.enums.CardBrand
import com.sunmi.pay.hardware.aidlv2.bean.CapkV2
import com.sunmi.pay.hardware.aidlv2.bean.EmvTermParamV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber
import java.util.Locale
import java.util.UUID

object CardHelper {

    fun injectCapks(emv: EMVOptV2) {
        val capks = listOf(
            // Mastercard CAPK FA
            Triple("A000000004", "FA", mapOf(
                "modulus" to "A90FCD55AA2D5D9963E35ED0F440177699832F49C6BAB15CDAE5794BE93F934D4462D5D12762E48C38BA83D8445DEAA74195A301A102B2F114EADA0D180EE5E7A5C73E0C4E11F67A43DDAB5D55683B1474CC0627F44B8D3088A492FFAADAD4F42422D0E7013536C3C49AD3D0FAE96459B0F6B1B6056538A3D6D44640F94467B108867DEC40FAAECD740C00E2B7A8852DDF",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "5BED4068D96EA16D2D77E03D6036FC7A160EA99C"
            )),

            // Visa CAPK 08
            Triple("A000000003", "08", mapOf(
                "modulus" to "D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24EC00AD880B3CE50D088111217C1C2A3C32F2F20969507EC45A3DF52A405D3A0FF41FBFFD869CEF90775D35E5B0FA1C91AD5A3C2F04F652CF1F0A9B9EBF00BB285AD519DD0F2C830696068",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "20D213126955DE205ADC2FD2822BD22DE21CF9A8"
            )),

            // JCB CAPK 11
            Triple("A000000065", "11", mapOf(
                "modulus" to "ACD2B12302EE644F3F835ABD1FC7A6F62CCE48FFEC622AA8EF062BEF6FB8BA8BC68BBF6AB5870EED579BC3973E121303D34841A796D6DCBC41DBF9E52C4609795C0CCF7EE86FA1D5CB041071ED2C51D2202F63F1156C58A92D38BC60BDF424E1776E2BC9648078A03B36FB554375FC53D57C73F5160EA59F3AFC5398EC7B67758D65C8A53CEAA8CFBB0F",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "4ABFFD6B1C51212D05552E431C5B17007D2F5E6D"
            )),

            // UnionPay CAPK 05
            Triple("A000000333", "05", mapOf(
                "modulus" to "B8048ABC30C90D976336543E3FD7091C8FE4800DF820ED55E7E94813ED00555B573FECA3D84AF6131A651D66CFF4284FB13B635EDD0EE40176D8BF04B7FD1C7BACF9AC7327DFAA8AA72D10DB3B8E70B2DDD811CB4196525EA386ACC33C0D9D4575916469C4E4F53E8E1C912CC618CB22DDE7C3568E90022E6BBA770202E4522A2DD623D180E215BD1D1507FE3DC90CA310D27B3EFCCD8F83DE3052CAD1E48938C68D095AAC91B5F37E28BB49EC7ED597",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "E881E390675D44C2F37637D010FD6861F6528FA4"
            ))
        )
        capks.forEach { (rid, index, data) ->
            try {
                val capkV2 = CapkV2().apply {
                    this.hashInd = 0x01.toByte()
                    this.arithInd = 0x01.toByte()
                    this.index = index.toInt(16).toByte()
                    this.rid = UtilHelper.hexStringToByteArray(rid)
                    this.modul = UtilHelper.hexStringToByteArray(data["modulus"]!!)
                    this.expDate = UtilHelper.hexStringToByteArray(data["expDate"]!!)
                    this.exponent = UtilHelper.hexStringToByteArray(data["exponent"]!!)
                    this.checkSum = UtilHelper.hexStringToByteArray(data["checksum"]!!)
                }
                emv.addCapk(capkV2)
            } catch (e: Exception) {}
        }
    }

    fun generateTransactionId(): String {
        return "TXN${System.currentTimeMillis()}"
    }


    fun detectBrand(pan: String): String {
        if (pan.isEmpty()) return CardBrand.UNKNOWN.displayName

        // Remove spaces and non-digits
        val cleanPan = pan.replace(Regex("[^0-9]"), "")

        if (cleanPan.length < 6) return CardBrand.UNKNOWN.displayName

        // Get BIN (Bank Identification Number) - first 6-8 digits
        val bin6 = cleanPan.substring(0, 6)
        val bin8 = if (cleanPan.length >= 8) cleanPan.substring(0, 8) else bin6

        return when {
            // NAPAS (Vietnam domestic) - 970xxx
            bin6.startsWith("970") -> CardBrand.NAPAS.displayName

            // VISA - starts with 4
            cleanPan.startsWith("4") -> CardBrand.VISA.displayName

            // MasterCard - 51-55, 2221-2720
            bin6.substring(0, 2) in listOf("51", "52", "53", "54", "55") -> CardBrand.MASTERCARD.displayName
            bin6.substring(0, 4).toIntOrNull()?.let { it in 2221..2720 } == true -> CardBrand.MASTERCARD.displayName

            // JCB - starts with 35
            bin6.startsWith("35") -> CardBrand.JCB.displayName

            // American Express - 34, 37
            bin6.substring(0, 2) in listOf("34", "37") -> CardBrand.AMEX.displayName

            // UnionPay - starts with 62
            cleanPan.startsWith("62") -> CardBrand.UNIONPAY.displayName

            // Discover - 6011, 622126-622925, 644-649, 65
            bin6.startsWith("6011") -> CardBrand.DISCOVER.displayName
            bin8.toIntOrNull()?.let { it in 622126..622925 } == true -> CardBrand.DISCOVER.displayName
            bin6.substring(0, 3).toIntOrNull()?.let { it in 644..649 } == true -> CardBrand.DISCOVER.displayName
            bin6.startsWith("65") -> CardBrand.DISCOVER.displayName

            else -> CardBrand.UNKNOWN.displayName
        }
    }


    fun validatePAN(pan: String): Boolean {
        return pan.isNotEmpty() && pan.length in 13..19 && pan.all { it.isDigit() }
    }

    fun setEmvTlvs(emv: EMVOptV2, terminal: Terminal?) {
        val evmConfigs = terminal?.evmConfigs
        setGlobalTlvs(emv, terminal)

        evmConfigs?.forEach { config ->
            when (config.vendorName.uppercase(Locale.getDefault())) {
                "JCB" -> setJcbTlvs(emv, config)
                "NAPAS" -> setNapasTlvs(emv, config)
                "VISA" -> setPayWaveTlvs(emv, config)
                "MASTERCARD" -> setPayPassTlvs(emv, config)
                "UNIONPAY", "UNION PAY" -> setQpbocTlvs(emv, config)
                "AMEX", "AMERICAN EXPRESS" -> setExpressPayTlvs(emv, config)
            }
        }
    }

    fun parseEmvTlv(tlvHex: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            var idx = 0
            val data = tlvHex.uppercase(Locale.getDefault())
            while (idx + 2 <= data.length) {
                val firstByte = data.substring(idx, idx + 2).toInt(16)
                val tag = if ((firstByte and 0x1F) == 0x1F) {
                    data.substring(idx, idx + 4)
                } else {
                    data.substring(idx, idx + 2)
                }
                idx += tag.length
                if (idx + 2 > data.length) break

                val lenHex = data.substring(idx, idx + 2)
                val length = lenHex.toInt(16)
                idx += 2

                val valueLenChars = length * 2
                if (idx + valueLenChars > data.length) break

                val value = data.substring(idx, idx + valueLenChars)
                result[tag] = value
                idx += valueLenChars
            }
        } catch (e: Exception) {}
        return result
    }

    fun setGlobalTlvs(emv: EMVOptV2, terminal: Terminal?) {
        val config: EvmConfig = terminal?.evmConfigs?.firstOrNull() ?: EvmConfig()

        val globalTags = arrayOf(
            "9F1A", "5F2A", "5F36", "9F33", "9F35", "9F40",
            "9F66", "9F09", "9F1C", "9F15", "9F16", "9F1E"
        )

        val ttq = when (config.vendorName.uppercase(Locale.getDefault())) {
            "VISA" -> "26000080"
            "MASTERCARD" -> "3600C080"
            else -> "3600C080"
        }
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

        emv.setTlvList(CardConstants.OP_NORMAL, globalTags, globalValues)
    }



    fun parseEmvData(emvData: String): EmvCardData? {
        try {
            if (emvData.isEmpty()) return null

            // Parse TLV để lấy tags map
            val tags = parseEmvTlv(emvData)

            Timber.d("🔍 Parsing EMV data, found tags: ${tags.keys.joinToString()}")

            // 🔥 Strategy 1: Try Tag 5A for PAN
            var pan = tags["5A"] ?: ""

            // 🔥 Strategy 2: Try Tag 5F24 for expiry (format YYMMDD)
            var expiry = tags["5F24"]?.let { exp ->
                if (exp.length >= 4) {
                    val yy = exp.substring(0, 2)
                    val mm = exp.substring(2, 4)

                    // Validate month
                    val monthInt = mm.toIntOrNull()
                    if (monthInt == null || monthInt < 1 || monthInt > 12) {
                        Timber.w("Invalid month in Tag 5F24: $mm")
                        return@let null
                    }

                    "$mm$yy"  // Convert YYMM → MMyy
                } else {
                    Timber.w("Tag 5F24 too short: $exp")
                    null
                }
            }

            // 🔥 Fallback: Parse from Tag 57 (Track 2 Equivalent) if needed
            if (pan.isEmpty() || expiry == null) {
                tags["57"]?.let { track2Hex ->
                    val track2Data = parseTrack2FromHex(track2Hex)
                    if (track2Data != null) {
                        if (pan.isEmpty()) {
                            pan = track2Data.pan
                            Timber.d("📌 PAN from Tag 57: ****${pan.takeLast(4)}")
                        }
                        if (expiry == null) {
                            expiry = track2Data.expiry
                            Timber.d("📌 Expiry from Tag 57: $expiry")
                        }
                    }
                }
            }

            // Validate we got the data
            if (pan.isEmpty()) {
                Timber.w("Cannot extract PAN from EMV data")
                return null
            }

            if (expiry == null || expiry.isEmpty()) {
                Timber.w("Cannot extract expiry from EMV data")
                return null
            }

            Timber.d("✅ Parsed EMV: PAN=****${pan.takeLast(4)}, Expiry=$expiry (format: MMyy)")

            return EmvCardData(pan, expiry)

        } catch (e: Exception) {
            Timber.e(e, "Error parsing EMV data")
            return null
        }
    }
    fun parseMagneticTrack2(track2: String): MagneticCardData? {
        try {
            val parts = track2.split("=", "D", "d")
            if (parts.isEmpty()) return null

            val pan = parts[0].trim()

            if (pan.length < 13 || pan.length > 19 || !pan.all { it.isDigit() }) {
                return null
            }

            // 🔥 FIX: Convert YYMM → MMyy
            val expiry = if (parts.size > 1 && parts[1].length >= 4) {
                val yymmFromTrack = parts[1].substring(0, 4)  // "2512" = YY=25, MM=12

                // Validate
                if (!yymmFromTrack.all { it.isDigit() }) {
                    Timber.w("Invalid expiry format in track2: $yymmFromTrack")
                    return null
                }

                val yy = yymmFromTrack.substring(0, 2)  // "25"
                val mm = yymmFromTrack.substring(2, 4)  // "12"

                // Validate month
                val monthInt = mm.toIntOrNull()
                if (monthInt == null || monthInt < 1 || monthInt > 12) {
                    Timber.w("Invalid month in expiry: $mm")
                    return null
                }

                "$mm$yy"  // ← Convert to MMyy: "1225"
            } else {
                Timber.w("Expiry data not found in track2")
                ""
            }

            // Log để verify
            Timber.d("🔍 Parsed expiry: $expiry (format: MMyy)")

            return MagneticCardData(pan, expiry)

        } catch (e: Exception) {
            Timber.e(e, "Error parsing track2")
            return null
        }
    }

    fun createTerminalParam(config: EvmConfig? = null): EmvTermParamV2 {
        return EmvTermParamV2().apply {
            capability = config?.terminalCap9F33 ?: "E0F8C8"
            terminalType = config?.terminalType9F35 ?: "22"
            countryCode = config?.countryCode9F1A ?: "0704"
            currencyCode = "704"
            currencyExp = config?.transCurrencyExp ?: "02"
            surportPSESel = true
            addCapability = "0300C00000"
            TTQ = "26000080"
        }
    }
    fun buildRequestSale(request: PaymentAppRequest, card: RequestSale.Data.Card): RequestSale {
        val mode = getCardMode(card.type)
        val amount = request.merchantRequestData?.amount ?: 0
        val posEntryMode = when (mode) {
            "MAGNETIC" -> "90"
            "CHIP" -> "05"
            "CONTACTLESS" -> "07"
            else -> "00"
        }

        return RequestSale(
            requestData = request,
            requestId = UUID.randomUUID().toString(),
            data = RequestSale.Data(
                card = RequestSale.Data.Card(
                    mode = mode,
                    ksn = card.ksn,
                    pin = card.pin,
                    type = card.type,
                    track1 = card.track1,
                    track2 = card.track2,
                    track3 = card.track3,
                    newPin = card.newPin,
                    emvData = card.emvData,
                    clearPan = card.clearPan,
                    expiryDate = card.expiryDate,
                ),
                device = RequestSale.Data.Device(
                    posEntryMode = posEntryMode,
                    posConditionCode = "00"
                ),
                payment = RequestSale.Data.PaymentData(
                    currency = "VND",
                    transAmount = amount.toString(),
                )
            )
        )
    }

    fun returnSaleResponse(saleResult: SaleResultData, originalRequest: PaymentAppRequest): PaymentAppResponse {

        val additionalData = buildAdditionalData(saleResult)
        val paymentResponseData = PaymentResponseData(
            refNo = saleResult.data?.refNo,
            additionalData = additionalData,
            tip = saleResult.requestData?.tip,
            tid = saleResult.requestData?.tid,
            mid = saleResult.requestData?.mid,
            description = saleResult.status?.message,
            status = saleResult.status?.code ?: "99",
            transactionId = saleResult.header?.transId,
            billNumber = saleResult.requestData?.billNumber,
            referenceId = saleResult.requestData?.referenceId,
            transactionTime = saleResult.header?.transmitsDateTime,
            amount = saleResult.data?.totalAmount?.toLongOrNull() ?: 0,
            ccy = saleResult.data?.currency ?: saleResult.requestData?.currency,
        )

        val response = PaymentAppResponse(
            type = originalRequest.type,
            action = originalRequest.action,
            paymentResponseData = paymentResponseData
        )
        return response
    }

    private fun getCardMode(cardType: String?): String {
        if (cardType == null) return "UNKNOWN"
        return when (cardType.uppercase(Locale.getDefault())) {
            "MAGNETIC" -> "MAGNETIC"
            "CHIP" -> "CHIP"
            "CONTACTLESS" -> "CONTACTLESS"
            else -> "UNKNOWN"
        }
    }

    private fun setJcbTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125", "DF8126"
        )
        val values = arrayOf(
            "E0", "F8", "F8", "E8", "00", "00",
            config.tacDefault, config.tacDenial, config.tacOnline,
            config.floorLimit9F1B.takeLast(8)
        )
        emv.setTlvList(CardConstants.OP_JSPEEDY, tags, values)
    }

    private fun setQpbocTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf("DF69", "DF70", "DF71", "DF72", "DF73")
        val values = arrayOf("E0", "F8", "F8", "E8", "00")
        emv.setTlvList(CardConstants.OP_QPBOC, tags, values)
    }

    private fun setNapasTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125", "DF8126"
        )
        val values = arrayOf(
            "E0", "F8", "F8", "E8", "00", "00",
            config.tacDefault, config.tacDenial, config.tacOnline,
            config.floorLimit9F1B.takeLast(8)
        )
        emv.setTlvList(CardConstants.OP_PAYPASS, tags, values)
    }

    private fun setPayWaveTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811B", "DF811D", "DF811E",
            "DF811F", "DF8120", "DF8121", "DF8122", "DF8123", "DF8124",
            "DF8125", "DF812C"
        )
        val values = arrayOf(
            "000000100000", "000000000000", "000000999999", "30", "02", "00",
            "000001000000", "000000999999", "000001000000", "0000000000",
            config.tacDefault, config.tacDenial, config.tacOnline, "000000100000"
        )
        emv.setTlvList(CardConstants.OP_PAYWAVE, tags, values)
    }

    private fun setPayPassTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125", "DF8126", "DF811B", "DF811D",
            "DF8122", "DF8120", "DF8121"
        )
        val values = arrayOf(
            "E0", "F8", "F8", "E8", "00", "00",
            config.tacDefault, config.tacDenial, config.tacOnline,
            config.floorLimit9F1B.takeLast(8),
            "30", "02", "0000000000", "000000000000", "000000000000"
        )
        emv.setTlvList(CardConstants.OP_PAYPASS, tags, values)
    }

    private fun setExpressPayTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811F", "DF811E", "DF812C",
            "DF8123", "DF8124", "DF8125"
        )
        val values = arrayOf(
            "E0", "F8", "F8", "E8", "00", "00",
            config.tacDefault, config.tacDenial, config.tacOnline
        )
        emv.setTlvList(CardConstants.OP_EXPRESSPAY, tags, values)
    }

    private fun parseTrack2FromHex(track2Hex: String): EmvCardData? {
        try {
            // Convert hex to ASCII
            val track2String = if (track2Hex.contains('D', ignoreCase = true) || track2Hex.contains('=')) {
                track2Hex  // Đã là ASCII
            } else {
                // Convert từ hex
                track2Hex.chunked(2)
                    .mapNotNull {
                        try {
                            it.toInt(16).toChar()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .joinToString("")
                    .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
            }

            // Split by separator
            val parts = track2String.split('D', '=', 'd')
            if (parts.size < 2) return null

            val pan = parts[0].trim()
            if (pan.length < 13 || pan.length > 19 || !pan.all { it.isDigit() }) {
                return null
            }

            val secondPart = parts[1]
            if (secondPart.length < 4) return null

            val yymmFromTrack = secondPart.substring(0, 4)
            if (!yymmFromTrack.all { it.isDigit() }) return null

            val yy = yymmFromTrack.substring(0, 2)
            val mm = yymmFromTrack.substring(2, 4)

            val monthInt = mm.toIntOrNull()
            if (monthInt == null || monthInt < 1 || monthInt > 12) {
                return null
            }

            val expiry = "$mm$yy"

            return EmvCardData(pan, expiry)

        } catch (e: Exception) {
            return null
        }
    }

    private fun buildAdditionalData(saleResult: SaleResultData): Map<String, Any> {
        val data = mutableMapOf<String, Any>()

        // Data section
        saleResult.data?.let { d ->
            d.emv?.let { data["emv"] = it }
            d.refNo?.let { data["refNo"] = it }
            d.batchNo?.let { data["batchNo"] = it }
            d.traceNo?.let { data["traceNo"] = it }
            d.currency?.let { data["currency"] = it }
            d.cardBrand?.let { data["cardBrand"] = it }
            d.cardHolder?.let { data["cardHolder"] = it }
            d.cardNumber?.let { data["cardNumber"] = it }
            d.totalAmount?.let { data["totalAmount"] = it }
            d.approveCode?.let { data["approveCode"] = it }
            d.isoResponseCode?.let { data["isoResponseCode"] = it }
        }

        // Header section
        saleResult.header?.let { h ->
            h.transId?.let { data["transId"] = it }
            h.provider?.let { data["provider"] = it }
            h.transType?.let { data["transType"] = it }
            h.terminalId?.let { data["terminalId"] = it }
            h.merchantId?.let { data["merchantId"] = it }
            h.merchantTransId?.let { data["merchantTransId"] = it }
            h.transmitsDateTime?.let { data["transmitsDateTime"] = it }
        }

        // Request ID
        saleResult.requestId?.let { data["requestId"] = it }

        // Status
        saleResult.status?.let { s ->
            s.code?.let { data["statusCode"] = it }
            s.message?.let { data["statusMessage"] = it }
        }

        return data
    }
}