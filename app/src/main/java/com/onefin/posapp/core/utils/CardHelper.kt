package com.onefin.posapp.core.utils

import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.EvmConfig
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.RequestSale
import com.sunmi.pay.hardware.aidlv2.bean.CapkV2
import com.sunmi.pay.hardware.aidlv2.bean.EmvTermParamV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber
import java.util.Locale
import java.util.UUID

object CardHelper {
    fun injectCapks(emv: EMVOptV2) {
        val capks = listOf(
            // Mastercard CAPK FA (Common for contactless)
            Triple("A000000004", "FA", mapOf(
                "modulus" to "A90FCD55AA2D5D9963E35ED0F440177699832F49C6BAB15CDAE5794BE93F934D4462D5D12762E48C38BA83D8445DEAA74195A301A102B2F114EADA0D180EE5E7A5C73E0C4E11F67A43DDAB5D55683B1474CC0627F44B8D3088A492FFAADAD4F42422D0E7013536C3C49AD3D0FAE96459B0F6B1B6056538A3D6D44640F94467B108867DEC40FAAECD740C00E2B7A8852DDF",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "5BED4068D96EA16D2D77E03D6036FC7A160EA99C"
            )),

            // Visa CAPK 08 (Common for contactless)
            Triple("A000000003", "08", mapOf(
                "modulus" to "D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24EC00AD880B3CE50D088111217C1C2A3C32F2F20969507EC45A3DF52A405D3A0FF41FBFFD869CEF90775D35E5B0FA1C91AD5A3C2F04F652CF1F0A9B9EBF00BB285AD519DD0F2C830696068",
                "exponent" to "03",
                "expDate" to "251231",
                "checksum" to "20D213126955DE205ADC2FD2822BD22DE21CF9A8"
            ))
        )

        capks.forEach { (rid, index, data) ->
            try {
                val capkV2 = CapkV2().apply {
                    this.rid = UtilHelper.hexStringToByteArray(rid)
                    this.index = index.toInt(16).toByte()
                    this.hashInd = 0x01.toByte()
                    this.arithInd = 0x01.toByte()
                    this.modul = UtilHelper.hexStringToByteArray(data["modulus"]!!)
                    this.exponent = UtilHelper.hexStringToByteArray(data["exponent"]!!)
                    this.expDate = UtilHelper.hexStringToByteArray(data["expDate"]!!)
                    this.checkSum = UtilHelper.hexStringToByteArray(data["checksum"]!!)
                }

                val result = emv.addCapk(capkV2)
                val status = if (result == 0) "✅" else "❌"

            } catch (e: Exception) {
            }
        }
    }

    fun generateTransactionId(): String {
        return "TXN${System.currentTimeMillis()}"
    }

    fun extractExpiryFromTrack2(track2: String): String {
        return try {
            val parts = track2.split("D", "=")
            if (parts.size >= 2 && parts[1].length >= 4) {
                parts[1].substring(0, 4)
            } else ""
        } catch (e: Exception) {
            ""
        }
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
                else -> Timber.tag("CardHelper").w("Unknown vendor: ${config.vendorName}")
            }
        }
    }

    fun parseEmvTlv(tlvHex: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            var idx = 0
            val data = tlvHex.uppercase(Locale.getDefault())
            while (idx + 2 <= data.length) {
                // read tag: if first byte low 5 bits == 0x1F then two-byte tag
                val firstByte = data.substring(idx, idx + 2).toInt(16)
                val tag = if ((firstByte and 0x1F) == 0x1F) {
                    data.substring(idx, idx + 4).also { /* 2-byte tag */ }
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
        } catch (e: Exception) {
        }
        return result
    }

    fun setGlobalTlvs(emv: EMVOptV2, terminal: Terminal?) {
        val config: EvmConfig = terminal?.evmConfigs?.firstOrNull() ?: EvmConfig()

        val globalTags = arrayOf(
            "9F1A",  // Terminal Country Code
            "5F2A",  // Transaction Currency Code
            "5F36",  // Transaction Currency Exponent
            "9F33",  // Terminal Capabilities
            "9F35",  // Terminal Type
            "9F40",  // Additional Terminal Capabilities
            "9F66",  // TTQ
            "9F09",  // Terminal Application Version
            "9F1C",  // Terminal ID
            "9F15",  // MCC
            "9F16",  // Merchant ID
            "9F1E"   // IFD Serial Number
        )

        val globalValues = arrayOf(
            config.countryCode9F1A,
            config.transCurrencyCode5F2A,
            config.transCurrencyExp,
            config.terminalCap9F33,
            config.terminalType9F35,
            config.exTerminalCap9F40,
            "3600C080",
            config.version9F09,
            terminal?.tid ?: config.terminalId9F1C,
            config.mcc9F15,
            terminal?.mid ?: config.merchantId9F16,
            "00000001"
        )

        emv.setTlvList( CardConstants.OP_NORMAL, globalTags, globalValues)
        Timber.tag("CardHelper").d("Global TLVs configured")
    }

    fun createTerminalParam(config: EvmConfig? = null): EmvTermParamV2 {
        return EmvTermParamV2().apply {
            // ✅ Capability phải hỗ trợ đầy đủ contactless
            capability = config?.terminalCap9F33 ?: "E0F8C8"  // Thay đổi từ E0B8C8 -> E0F8C8
            terminalType = config?.terminalType9F35 ?: "22"
            countryCode = config?.countryCode9F1A ?: "0704"
            currencyCode = config?.transCurrencyCode5F2A ?: "0704"
            currencyExp = config?.transCurrencyExp ?: "02"
            surportPSESel = true
            // ✅ Additional capability cho contactless
            addCapability = config?.exTerminalCap9F40 ?: "F000F0A001"
            // ✅ TTQ phù hợp cho contactless
            TTQ = "3600C080"  // Thay đổi từ 3600C080
        }
    }

    fun buildRequestSale(request: PaymentAppRequest, card: RequestSale.Data.Card): RequestSale {
        val mode = getCardMode(card.type)
        val amount = request.merchantRequestData?.amount ?: 0
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
                    posEntryMode = "010",
                    posConditionCode = "00"
                ),
                payment = RequestSale.Data.PaymentData(
                    currency = "VND",
                    transAmount = amount.toString(),
                )
            )
        )
    }


    private fun getCardMode(cardType: String?): String {
        if (cardType == null) return  "UNKNOWN"
        return when (cardType.uppercase(Locale.getDefault())) {
            "MAGNETIC" -> "MAGNETIC"
            "CHIP" -> "CHIP"
            "CONTACTLESS" -> "CONTACTLESS"
            else -> "UNKNOWN"
        }
    }
    // JCB
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
        Timber.tag("CardHelper").d("JCB configured for AID: ${config.aid9F06}")
    }
    // Union
    private fun setQpbocTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF69", "DF70", "DF71", "DF72", "DF73"
        )

        val values = arrayOf(
            "E0", "F8", "F8", "E8", "00"
        )

        emv.setTlvList(CardConstants.OP_QPBOC, tags, values)
        Timber.tag("CardHelper").d("qPBOC configured for AID: ${config.aid9F06}")
    }
    // Napas
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
        Timber.tag("CardHelper").d("NAPAS configured for AID: ${config.aid9F06}")
    }
    // Visa
    private fun setPayWaveTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117",  // Reader CVM Required Limit
            "DF8118",  // Reader Contactless Floor Limit
            "DF8119",  // Reader Contactless Transaction Limit (No CVM)
            "DF811B",  // Torn Transaction Log Lifetime
            "DF811D",  // Relay Resistance Grace Period
            "DF811E",  // Contactless Application Not Allowed
            "DF811F",  // Reader Contactless Transaction Limit (CVM)
            "DF8120",  // Mag-stripe Transaction Limit (No CVM)
            "DF8121",  // Mag-stripe Transaction Limit (CVM)
            "DF8122",  // Time Out Value
            "DF8123",  // Terminal Action Code - Default
            "DF8124",  // Terminal Action Code - Denial
            "DF8125",  // Terminal Action Code - Online
            "DF812C"   // Mag-stripe CVM Required Limit
        )

        val values = arrayOf(
            "E0",
            "F8",
            "F8",
            "30",
            "02",
            "00",
            "E8",
            "000000000000",
            "000000000000",
            "0000000000",
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            "00"
        )

        emv.setTlvList(CardConstants.OP_PAYWAVE, tags, values)
        Timber.tag("CardHelper").d("PayWave configured for AID: ${config.aid9F06}")
    }
    // MasterCard
    private fun setPayPassTlvs(emv: EMVOptV2, config: EvmConfig) {
        val tags = arrayOf(
            "DF8117",  // Reader CVM Required Limit
            "DF8118",  // Reader Contactless Floor Limit
            "DF8119",  // Reader Contactless Transaction Limit (No CVM)
            "DF811F",  // Reader Contactless Transaction Limit (CVM)
            "DF811E",  // Contactless Application Not Allowed
            "DF812C",  // Mag-stripe CVM Required Limit
            "DF8123",  // Terminal Action Code - Default
            "DF8124",  // Terminal Action Code - Denial
            "DF8125",  // Terminal Action Code - Online
            "DF8126",  // Terminal Floor Limit
            "DF811B",  // Torn Transaction Log Lifetime
            "DF811D",  // Relay Resistance Grace Period
            "DF8122",  // Time Out Value
            "DF8120",  // Mag-stripe Transaction Limit (No CVM)
            "DF8121"   // Mag-stripe Transaction Limit (CVM)
        )

        val values = arrayOf(
            "E0",
            "F8",
            "F8",
            "E8",
            "00",
            "00",
            config.tacDefault,
            config.tacDenial,
            config.tacOnline,
            config.floorLimit9F1B.takeLast(8),
            "30",
            "02",
            "0000000000",
            "000000000000",
            "000000000000"
        )

        emv.setTlvList(CardConstants.OP_PAYPASS, tags, values)
        Timber.tag("CardHelper").d("PayPass configured for AID: ${config.aid9F06}")
    }
    // Amex
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
        Timber.tag("CardHelper").d("ExpressPay configured for AID: ${config.aid9F06}")
    }
}