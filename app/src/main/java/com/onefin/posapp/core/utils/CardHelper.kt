package com.onefin.posapp.core.utils

import android.annotation.SuppressLint
import com.github.devnied.emvnfccard.model.EmvCard
import com.onefin.posapp.core.models.data.EmvCardData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.models.data.MemberResultData
import com.onefin.posapp.core.models.data.VoidResultData
import com.onefin.posapp.core.models.enums.CardBrand
import com.sunmi.pay.hardware.aidl.AidlConstants
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CardHelper {
    fun detectBrand(pan: String): String {
        if (pan.isEmpty()) return CardBrand.UNKNOWN.displayName

        val cleanPan = pan.replace(Regex("[^0-9]"), "")

        if (cleanPan.length < 6) return CardBrand.UNKNOWN.displayName

        val bin6 = cleanPan.take(6)
        val bin8 = if (cleanPan.length >= 8) cleanPan.take(8) else bin6

        return when {
            bin6.startsWith("970") -> CardBrand.NAPAS.displayName
            cleanPan.startsWith("4") -> CardBrand.VISA.displayName
            bin6.take(2) in listOf("51", "52", "53", "54", "55") -> CardBrand.MASTERCARD.displayName
            bin6.take(4).toIntOrNull()?.let { it in 2221..2720 } == true -> CardBrand.MASTERCARD.displayName
            bin6.startsWith("35") -> CardBrand.JCB.displayName
            bin6.take(2) in listOf("34", "37") -> CardBrand.AMEX.displayName
            cleanPan.startsWith("62") -> CardBrand.UNIONPAY.displayName
            bin6.startsWith("6011") -> CardBrand.DISCOVER.displayName
            bin8.toIntOrNull()?.let { it in 622126..622925 } == true -> CardBrand.DISCOVER.displayName
            bin6.take(3).toIntOrNull()?.let { it in 644..649 } == true -> CardBrand.DISCOVER.displayName
            bin6.startsWith("65") -> CardBrand.DISCOVER.displayName
            bin6.startsWith("1207") -> CardBrand.MEMBER.displayName
            else -> CardBrand.UNKNOWN.displayName
        }
    }

    fun extractEmvDataHex(emvCard: EmvCard): String {
        val tlvMap = mutableMapOf<String, String>()

        emvCard.cardNumber?.let {
            val panDigits = it.replace(" ", "")
            tlvMap["5A"] = panDigits
        }

        emvCard.track2?.raw?.let {
            tlvMap["57"] = UtilHelper.byteArrayToHexString(it)
        }

        emvCard.expireDate?.let {
            formatExpiry(it)?.let { expiry ->
                tlvMap["5F24"] = expiry
            }
        }

        buildHolderName(emvCard)?.let {
            tlvMap["5F20"] = UtilHelper.stringToHexString(it)
        }

        emvCard.applications.firstOrNull()?.let { app ->
            app.aid?.let {
                tlvMap["9F06"] = UtilHelper.byteArrayToHexString(it)
            }

            app.applicationLabel?.let {
                tlvMap["50"] = UtilHelper.stringToHexString(it)
            }
        }

        return buildTlvHexString(tlvMap)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseMagneticTrack1(track1: String): String? {
        try {
            if (track1.isEmpty()) return null

            val cleaned = track1
                .removePrefix("%B")
                .removePrefix("%b")
                .removePrefix("B")
                .removePrefix("b")
                .removeSuffix("?")
                .trim()

            if (cleaned.isEmpty() || !cleaned[0].isDigit()) return null

            val parts = cleaned.split("^")
            if (parts.size < 2) return null

            val rawName = parts[1].trim()
            if (rawName.isEmpty()) return null

            val name = if (rawName.contains("/")) {
                val nameParts = rawName.split("/", limit = 2)
                if (nameParts.size == 2) {
                    val lastName = nameParts[0].trim()
                    val firstAndMiddle = nameParts[1].trim()
                    "$firstAndMiddle $lastName"
                } else {
                    rawName.replace("/", " ")
                }
            } else {
                rawName
            }

            val normalized = name.trim()
                .replace(Regex("\\s+"), " ")
                .uppercase()
            return normalized

        } catch (e: Exception) {
            return null
        }
    }

    private fun parseMagneticTrack2(track2: String): EmvCardData? {
        try {
            val parts = track2.split("=", "D", "d")
            if (parts.isEmpty()) return null

            val pan = parts[0].trim()
            val expiryAndMore = parts[1]
            if (pan.length !in 13..19 || !pan.all { it.isDigit() }) {
                return null
            }

            val expiry = if (parts.size > 1 && parts[1].length >= 4) {
                val yymmFromTrack = parts[1].substring(0, 4)

                if (!yymmFromTrack.all { it.isDigit() }) return null

                val yy = yymmFromTrack.take(2)
                val mm = yymmFromTrack.substring(2, 4)

                val monthInt = mm.toIntOrNull()
                if (monthInt == null || monthInt < 1 || monthInt > 12) return null

                "$mm$yy"
            } else {
                ""
            }

            val serviceCode = if (expiryAndMore.length >= 7) {
                expiryAndMore.substring(4, 7)
            } else {
                null
            }

            return EmvCardData(pan, expiry, serviceCode)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseMagneticCard(track1: String, track2: String): EmvCardData? {
        try {
            val track2Data = parseMagneticTrack2(track2) ?: return null

            val holderName = parseMagneticTrack1(track1)
            val issuerName = BinLookupHelper.lookupIssuer(track2Data.pan) ?: ""
            return EmvCardData(track2Data.pan, track2Data.expiry, holderName, issuerName)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseEmvData(emvData: String, track1: String, track2: String): EmvCardData? {
        try {
            Timber.d("=== parseEmvData START ===")
            Timber.d("Input lengths - emvData: ${emvData.length}, track1: ${track1.length}, track2: ${track2.length}")

            if (emvData.isEmpty()) {
                Timber.w("EMV data is empty")
                return null
            }

            val tags = parseEmvTlv(emvData)
            Timber.d("Parsed ${tags.size} tags: ${tags.keys.joinToString(",")}")

            // ISSUE 1: Nên remove 'F' padding từ PAN
            var pan = tags["5A"]?.takeIf { it.isNotEmpty() }?.replace("F", "")?.replace("f", "") ?: ""
            Timber.d("PAN from tag 5A: ${if (pan.isNotEmpty()) "${pan.take(6)}...${pan.takeLast(4)}" else "Empty"}")

            var expiry = tags["5F24"]
                ?.takeIf { it.isNotEmpty() }
                ?.let { exp ->
                    Timber.d("Raw expiry (5F24): $exp")
                    if (exp.length >= 4) {
                        val yy = exp.take(2)
                        val mm = exp.substring(2, 4)

                        val monthInt = mm.toIntOrNull()
                        if (monthInt == null || monthInt < 1 || monthInt > 12) {
                            Timber.w("Invalid month: $mm")
                            return@let null
                        }

                        "$mm$yy"
                    } else {
                        Timber.w("Expiry too short: ${exp.length}")
                        null
                    }
                }
            Timber.d("Parsed expiry: $expiry")

            // Fallback to Track2
            if (pan.isEmpty() || expiry == null) {
                Timber.d("Attempting fallback to Track2 (tag 57)")
                tags["57"]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { track2Hex ->
                        Timber.d("Track2 hex: $track2Hex")
                        val track2Data = parseTrack2FromHex(track2Hex)
                        if (track2Data != null) {
                            Timber.d("Track2 parsed: PAN=${track2Data.pan.take(6)}..., Expiry=${track2Data.expiry}")
                            if (pan.isEmpty()) {
                                pan = track2Data.pan
                            }
                            if (expiry == null) {
                                expiry = track2Data.expiry
                            }
                        } else {
                            Timber.w("Failed to parse Track2")
                        }
                    }
            }

            if (pan.isEmpty()) {
                Timber.e("PAN is empty after all attempts")
                return null
            }
            if (expiry == null || expiry.isEmpty()) {
                Timber.e("Expiry is empty after all attempts")
                return null
            }

            // Parse cardholder name
            var cardholderName = tags["5F20"]
                ?.takeIf { it.isNotEmpty() }
                ?.let { hex ->
                    try {
                        Timber.d("Parsing name from 5F20: $hex")
                        val name = hex.chunked(2)
                            .map { it.toInt(16).toChar() }
                            .joinToString("")
                            .trim()
                        Timber.d("Parsed name (5F20): $name")
                        name
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse 5F20")
                        null
                    }
                } ?: ""

            // Fallback to tag 9F0B
            if (cardholderName.isEmpty()) {
                Timber.d("Attempting fallback name from 9F0B")
                cardholderName = tags["9F0B"]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { hex ->
                        try {
                            val name = hex.chunked(2)
                                .map { it.toInt(16).toChar() }
                                .joinToString("")
                                .trim()
                            Timber.d("Parsed name (9F0B): $name")
                            name
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to parse 9F0B")
                            null
                        }
                    } ?: ""
            }

            // ISSUE 2: Name formatting có thể sai với một số định dạng
            if (cardholderName.isNotEmpty() && cardholderName.contains("/")) {
                val originalName = cardholderName
                val names = cardholderName.split("/").reversed()
                cardholderName = names.joinToString(" ").trim()
                Timber.d("Name reformatted: '$originalName' -> '$cardholderName'")
            }

            val issuerName = BinLookupHelper.lookupIssuer(pan) ?: ""
            Timber.d("Issuer from BIN: $issuerName")

            val emvData = EmvCardData(pan, expiry, cardholderName, issuerName)

            // Fallback to magnetic data
            val cardData = parseMagneticCard(track1, track2)
            if (cardData != null) {
                Timber.d("Magnetic data available - Name: ${cardData.holderName}, Issuer: ${cardData.issuerName}")
            }

            if (emvData.holderName.isNullOrEmpty()) {
                emvData.holderName = cardData?.holderName
                Timber.d("Using magnetic holder name: ${emvData.holderName}")
            }
            if (emvData.issuerName.isNullOrEmpty()) {
                emvData.issuerName = cardData?.issuerName
                Timber.d("Using magnetic issuer name: ${emvData.issuerName}")
            }

            Timber.d("=== parseEmvData SUCCESS ===")
            return emvData

        } catch (e: Exception) {
            Timber.e(e, "Exception in parseEmvData")
            return null
        }
    }

    fun buildRequestSale(request: PaymentAppRequest, card: RequestSale.Data.Card, device: RequestSale.Data.Device): RequestSale {
        val amount = request.merchantRequestData?.amount ?: 0
        val requestId = UtilHelper.generateRequestId()
        return RequestSale(
            requestData = request,
            requestId = requestId,
            data = RequestSale.Data(
                card = RequestSale.Data.Card(
                    ksn = card.ksn,
                    pin = card.pin,
                    type = card.type,
                    mode = card.mode,
                    track1 = card.track1,
                    track2 = card.track2,
                    track3 = card.track3,
                    newPin = card.newPin,
                    emvData = card.emvData,
                    clearPan = card.clearPan,
                    expiryDate = card.expiryDate,
                    holderName = card.holderName,
                    issuerName = card.issuerName,
                ),
                device = RequestSale.Data.Device(
                    posEntryMode = device.posEntryMode,
                    posConditionCode = device.posConditionCode
                ),
                payment = RequestSale.Data.PaymentData(
                    currency = "VND",
                    transAmount = amount.toString(),
                )
            )
        )
    }

    fun parsePosEntryMode(tag9F39: String?, cardType: AidlConstants.CardType, transactionHasPin: Boolean = false, deviceHasPinPad: Boolean = true): String {
        // Byte 1: Entry Mode
        val entryMode = when {
            tag9F39 == "07" || tag9F39 == "91" -> "07"          // Contactless
            tag9F39 == "05" -> "05"                             // Contact chip
            cardType == AidlConstants.CardType.MAGNETIC -> "02"
            cardType == AidlConstants.CardType.NFC -> "07"
            cardType == AidlConstants.CardType.IC -> "05"
            else -> "05"
        }

        // Byte 2: PIN Capability
        val pinCapability = when {
            transactionHasPin -> "1"
            deviceHasPinPad -> "1"
            else -> "0"
        }
        return "$entryMode$pinCapability"
    }

    fun returnSaleResponse(saleResult: SaleResultData, originalRequest: PaymentAppRequest): PaymentAppResponse {
        val paymentResponseData = PaymentResponseData(
            refNo = saleResult.data?.refNo,
            description = saleResult.status?.message,
            status = saleResult.status?.code ?: "99",
            transactionId = saleResult.header?.transId,
            tip = originalRequest.merchantRequestData?.tip,
            tid = originalRequest.merchantRequestData?.tid,
            mid = originalRequest.merchantRequestData?.mid,
            transactionTime = saleResult.header?.transmitsDateTime,
            amount = saleResult.data?.totalAmount?.toLongOrNull() ?: 0,
            billNumber = originalRequest.merchantRequestData?.billNumber,
            referenceId = originalRequest.merchantRequestData?.referenceId,
            additionalData = originalRequest.merchantRequestData?.additionalData,
            ccy = saleResult.data?.currency ?: saleResult.data?.currency ?: "704",
        )
        if (paymentResponseData.amount != null)
            paymentResponseData.amount = paymentResponseData.amount!! * 100

        val response = PaymentAppResponse(
            type = originalRequest.type,
            action = originalRequest.action,
            paymentResponseData = paymentResponseData
        )
        return response
    }
    fun returnVoidResponse(voidResult: VoidResultData, originalRequest: PaymentAppRequest): PaymentAppResponse {
        val paymentResponseData = PaymentResponseData(
            refNo = voidResult.data?.refNo,
            description = voidResult.status?.message,
            status = voidResult.status?.code ?: "99",
            transactionId = voidResult.header?.transId,
            tip = originalRequest.merchantRequestData?.tip,
            tid = originalRequest.merchantRequestData?.tid,
            mid = originalRequest.merchantRequestData?.mid,
            transactionTime = voidResult.header?.transmitsDateTime,
            amount = voidResult.data?.totalAmount?.toLongOrNull() ?: 0,
            billNumber = originalRequest.merchantRequestData?.billNumber,
            referenceId = originalRequest.merchantRequestData?.referenceId,
            additionalData = originalRequest.merchantRequestData?.additionalData,
            ccy = voidResult.data?.currency ?: voidResult.data?.currency ?: "704",
        )
        if (paymentResponseData.amount != null)
            paymentResponseData.amount = paymentResponseData.amount!! * 100

        val response = PaymentAppResponse(
            type = originalRequest.type,
            action = originalRequest.action,
            paymentResponseData = paymentResponseData
        )
        return response
    }
    fun returnMemberResponse(result: MemberResultData, originalRequest: PaymentAppRequest): PaymentAppResponse {
        val paymentResponseData = PaymentResponseData(
            refNo = result.refno,
            description = result.errorDesc,
            status = result.respcode ?: "99",
            tip = result.tip?.toLongOrNull() ?: 0,
            amount = result.amount?.toLongOrNull() ?: 0,
            balance = result.balance?.toLongOrNull() ?: 0,
            tid = result.tid ?: originalRequest.merchantRequestData?.tid,
            mid = result.mid ?: originalRequest.merchantRequestData?.mid,
            additionalData = originalRequest.merchantRequestData?.additionalData,
        )
        if (paymentResponseData.amount != null)
            paymentResponseData.amount = paymentResponseData.amount!! * 100

        val response = PaymentAppResponse(
            type = originalRequest.type,
            action = originalRequest.action,
            paymentResponseData = paymentResponseData
        )
        return response
    }

    @SuppressLint("DefaultLocale")
    fun buildMagneticEmvData(
        track2: String,
        pan: String,
        expiryDate: String,  // Format: MMYY (e.g., "1225")
        serviceCode: String? = null,
        amount: Long = 0,
        currencyCode: String = "0704",  // VND
        countryCode: String = "0704"    // VN
    ): String {
        try {
            val tlvBuilder = StringBuilder()

            // 1. AID (Tag 4F) - Default Visa/Mastercard AID
            val aid = when {
                pan.startsWith("4") -> "A0000000031010"  // Visa
                pan.startsWith("5") -> "A0000000041010"  // Mastercard
                else -> "A0000000031010"  // Default Visa
            }
            tlvBuilder.append(buildTlv("4F", aid))
            Timber.d("Tag 4F (AID): $aid")

            // 2. Currency Code (Tag 5F2A)
            tlvBuilder.append(buildTlv("5F2A", currencyCode))
            Timber.d("Tag 5F2A (Currency): $currencyCode")

            // 3. PAN Sequence Number (Tag 5F34)
            tlvBuilder.append(buildTlv("5F34", "01"))
            Timber.d("Tag 5F34 (PAN Seq): 01")

            // 4. AIP (Tag 82) - Application Interchange Profile
            // 1C00 = SDA supported, Cardholder verification supported
            tlvBuilder.append(buildTlv("82", "1C00"))
            Timber.d("Tag 82 (AIP): 1C00")

            // 5. Dedicated File Name (Tag 84) - Same as AID
            tlvBuilder.append(buildTlv("84", aid))
            Timber.d("Tag 84 (DF Name): $aid")

            // 6. TVR (Tag 95) - Terminal Verification Results
            // 8080008000 = Default offline approved
            tlvBuilder.append(buildTlv("95", "8080008000"))
            Timber.d("Tag 95 (TVR): 8080008000")

            // 7. Transaction Date (Tag 9A) - YYMMDD
            val currentDate = getCurrentDateYYMMDD()
            tlvBuilder.append(buildTlv("9A", currentDate))
            Timber.d("Tag 9A (Date): $currentDate")

            // 8. Transaction Type (Tag 9C)
            // 00 = Purchase
            tlvBuilder.append(buildTlv("9C", "00"))
            Timber.d("Tag 9C (Type): 00")

            // 9. Amount (Tag 9F02) - 12 digits (6 bytes)
            val amountHex = String.format("%012d", amount)
            tlvBuilder.append(buildTlv("9F02", amountHex))
            Timber.d("Tag 9F02 (Amount): $amountHex")

            // 10. IAD (Tag 9F10) - Issuer Application Data
            // Format: Version(2) + Derivation(2) + CVR(6) + DAC/ICC(4)
            val iadVersion = "06"      // Version 6
            val derivation = "01"      // Derivation method
            val cvr = "0A0360A400"     // Card Verification Results
            val iad = "$iadVersion$derivation$cvr"
            tlvBuilder.append(buildTlv("9F10", iad))
            Timber.d("Tag 9F10 (IAD): $iad")

            // 11. Terminal Country Code (Tag 9F1A)
            tlvBuilder.append(buildTlv("9F1A", countryCode))
            Timber.d("Tag 9F1A (Country): $countryCode")

            // 12. Application Cryptogram (Tag 9F26) - TC
            // Generate pseudo cryptogram from track2
            val cryptogram = generatePseudoCryptogram(track2, amount.toString())
            tlvBuilder.append(buildTlv("9F26", cryptogram))
            Timber.d("Tag 9F26 (Cryptogram): $cryptogram")

            // 13. CID (Tag 9F27) - Cryptogram Information Data
            // 40 = TC (Transaction Certificate)
            tlvBuilder.append(buildTlv("9F27", "40"))
            Timber.d("Tag 9F27 (CID): 40")

            // 14. Terminal Capabilities (Tag 9F33)
            // E0F8C8 = Full capabilities
            tlvBuilder.append(buildTlv("9F33", "E0F8C8"))
            Timber.d("Tag 9F33 (Terminal Cap): E0F8C8")

            // 15. ATC (Tag 9F36) - Application Transaction Counter
            // Random 2-byte counter
            val atc = String.format("%04X", (Math.random() * 65535).toInt())
            tlvBuilder.append(buildTlv("9F36", atc))
            Timber.d("Tag 9F36 (ATC): $atc")

            // 16. Unpredictable Number (Tag 9F37)
            val unpredictableNumber = generateUnpredictableNumber()
            tlvBuilder.append(buildTlv("9F37", unpredictableNumber))
            Timber.d("Tag 9F37 (UN): $unpredictableNumber")

            // 17. Amount Other (Tag 9F03) - Always 0 for single currency
            tlvBuilder.append(buildTlv("9F03", "000000000000"))
            Timber.d("Tag 9F03 (Amount Other): 000000000000")

            val result = tlvBuilder.toString()
            Timber.d("=== MAGNETIC EMV DATA COMPLETE ===")
            Timber.d("Total length: ${result.length} chars")
            Timber.d("Data: ${result.take(100)}...")

            return result

        } catch (e: Exception) {
            Timber.e(e, "Failed to build magnetic EMV data")
            return ""
        }
    }

    private fun generatePseudoCryptogram(track2: String, amount: String): String {
        try {
            // Generate pseudo cryptogram from track2 + amount
            // Sử dụng hash để tạo cryptogram 8 bytes
            val input = "$track2$amount${System.currentTimeMillis()}"
            val bytes = input.toByteArray()

            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes)

            // Lấy 8 bytes đầu
            return hash.take(8)
                .joinToString("") { "%02X".format(it) }

        } catch (e: Exception) {
            Timber.e(e, "Failed to generate cryptogram")
            // Fallback: random 8 bytes
            val random = java.util.Random()
            return buildString {
                repeat(8) {
                    append(String.format("%02X", random.nextInt(256)))
                }
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getCurrentDateYYMMDD(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) % 100
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return String.format("%02d%02d%02d", year, month, day)
    }

    private fun generateUnpredictableNumber(): String {
        return (0..3).joinToString("") {
            (0..255).random().toString(16).padStart(2, '0')
        }.uppercase()
    }

    private fun buildHolderName(emvCard: EmvCard): String? {
        val first = emvCard.holderFirstname?.trim()
        val last = emvCard.holderLastname?.trim()

        return when {
            !first.isNullOrEmpty() && !last.isNullOrEmpty() -> "$first $last"
            !first.isNullOrEmpty() -> first
            !last.isNullOrEmpty() -> last
            else -> null
        }
    }

    private fun formatExpiry(date: java.util.Date?): String? {
        return try {
            date?.let {
                val sdf = SimpleDateFormat("yyMMdd", Locale.US)
                sdf.format(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildTlv(tag: String, value: String): String {
        val valueBytes = value.length / 2
        val length = valueBytes.toString(16).padStart(2, '0').uppercase()
        return "$tag$length$value"
    }

    private fun parseTrack2FromHex(track2Hex: String): EmvCardData? {
        try {
            if (track2Hex.isEmpty()) return null

            val track2String = if (track2Hex.contains('D', ignoreCase = true) || track2Hex.contains('=')) {
                track2Hex
            } else {
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

            if (track2String.isEmpty()) return null

            val parts = track2String.split('D', '=', 'd')
            if (parts.size < 2) return null

            val pan = parts[0].trim()
            if (pan.length !in 13..19 || !pan.all { it.isDigit() }) {
                return null
            }

            val secondPart = parts[1]
            if (secondPart.length < 4) return null

            val yymmFromTrack = secondPart.take(4)
            if (!yymmFromTrack.all { it.isDigit() }) return null

            val yy = yymmFromTrack.take(2)
            val mm = yymmFromTrack.substring(2, 4)

            val monthInt = mm.toIntOrNull()
            if (monthInt == null || monthInt < 1 || monthInt > 12) return null

            val expiry = "$mm$yy"

            return EmvCardData(pan, expiry)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun buildTlvHexString(tlvMap: Map<String, String>): String {
        val sb = StringBuilder()

        tlvMap.forEach { (tag, value) ->
            val valueBytes = value.length / 2
            val lengthHex = String.format("%02X", valueBytes)
            sb.append(tag).append(lengthHex).append(value)
        }

        return sb.toString()
    }
}