package com.onefin.posapp.core.utils

import androidx.compose.ui.graphics.Color
import com.onefin.posapp.core.models.EvmConfig
import com.onefin.posapp.core.models.data.StatusInfo
import com.sunmi.pay.hardware.aidlv2.bean.AidV2
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object UtilHelper {

    fun maskCardNumber(cardNumber: String): String {
        if (cardNumber.length < 12) return cardNumber
        return "${cardNumber.take(6)}******${cardNumber.takeLast(4)}"
    }
    fun getCurrentTimeStamp(): String {
        return System.currentTimeMillis().toString()
    }
    fun generateRandomBillNumber(): String {
        return buildString {
            append("TOT")
            repeat(7) {
                append(Random.nextInt(0, 10))
            }
        }
    }
    fun formatCurrency(input: String, unit: String?): String {
        if (input.isEmpty()) return ""

        val cleanInput = input.replace(".", "")
        val number = cleanInput.toLongOrNull() ?: return ""

        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
        if (unit != null) {
            return "${formatter.format(number)} $unit"
        }
        return formatter.format(number)
    }
    fun formatCurrency(number: Long, unit: String?): String {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("vi").setRegion("VN").build())
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,###", symbols)
        if (unit != null) {
            return "${formatter.format(number)} $unit"
        }
        return formatter.format(number)
    }
    fun formatCardNumber(cardNumber: String?): String {
        if (cardNumber.isNullOrEmpty()) return ""
        if (cardNumber.contains("*")) return cardNumber

        return if (cardNumber.length >= 16) {
            "${cardNumber.take(4)} **** **** ${cardNumber.takeLast(4)}"
        } else {
            cardNumber
        }
    }
    fun getStatusInfo(processStatus: Int?): StatusInfo {
        return when (processStatus) {
            -1 -> StatusInfo(
                text = "Đã hủy",
                textColor = Color(0xFFDC2626),
                backgroundColor = Color(0xFFFEE2E2)
            )
            0 -> StatusInfo(
                text = "Khởi tạo",
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
            1 -> StatusInfo(
                text = "Đã tính phí",
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            2 -> StatusInfo(
                text = "Đã kết toán",
                textColor = Color(0xFF16A34A),
                backgroundColor = Color(0xFFDCFCE7)
            )
            else -> StatusInfo(
                text = "Không xác định",
                textColor = Color(0xFF6B7280),
                backgroundColor = Color(0xFFF3F4F6)
            )
        }
    }

    fun getTodayDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getYesterdayDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun getDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    /**
     * Helper functions for byte conversion
     */
    fun hexStringToByteArray(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)

        // SỬA LẠI TẠI ĐÂY
        var cleanHex = hex.replace(" ", "").uppercase(Locale.getDefault())
        // Nếu độ dài là số lẻ, thêm một số '0' vào đầu
        if (cleanHex.length % 2 != 0) {
            cleanHex = "0$cleanHex"
        }

        val len = cleanHex.length
        val data = ByteArray(len / 2)

        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun stringToByteArray(str: String, maxLength: Int): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        return if (bytes.size > maxLength) {
            bytes.copyOf(maxLength)
        } else {
            bytes.copyOf(maxLength) // Pads with zeros
        }
    }

    fun decimalStringToByteArray(decimal: String, length: Int): ByteArray {
        return try {
            val value = decimal.toLongOrNull() ?: 0L
            val result = ByteArray(length)
            var temp = value
            for (i in length - 1 downTo 0) {
                result[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            result
        } catch (e: Exception) {
            ByteArray(length)
        }
    }

    fun stringToByte(str: String): Byte {
        return try {
            str.toIntOrNull()?.toByte() ?: 0.toByte()
        } catch (e: Exception) {
            0.toByte()
        }
    }

    fun evmConfigToAidV2(config: EvmConfig): AidV2 {
        return AidV2().apply {
            try {
                // AID - hex string to byte[]
                aid = hexStringToByteArray(config.aid9F06)

                // TAC - hex strings to byte[5]
                TACDefault = hexStringToByteArray(config.tacDefault).copyOf(5)
                TACOnline = hexStringToByteArray(config.tacOnline).copyOf(5)
                TACDenial = hexStringToByteArray(config.tacDenial).copyOf(5)

                // Floor limit - hex string to byte[]
                floorLimit = hexStringToByteArray(config.floorLimit9F1B)

                // Threshold - decimal string to byte[4]
                if (config.threshold.isNotEmpty()) {
                    threshold = decimalStringToByteArray(config.threshold, 4)
                }

                // Target percentages - string to byte
                if (config.targetPercent.isNotEmpty()) {
                    targetPer = stringToByte(config.targetPercent)
                }
                if (config.maxTargetPercent.isNotEmpty()) {
                    maxTargetPer = stringToByte(config.maxTargetPercent)
                }

                // Version - hex string to byte[2]
                version = hexStringToByteArray(config.version9F09).copyOf(2)

                // DDOL - hex string to byte[]
                if (config.defaultDDOL.isNotEmpty()) {
                    dDOL = hexStringToByteArray(config.defaultDDOL)
                }

                // TDOL - hex string to byte[]
                if (config.defaultTDOL.isNotEmpty()) {
                    tDOL = hexStringToByteArray(config.defaultTDOL)
                }

                // Merchant name - string to byte[128]
                merchName = stringToByteArray(config.merchantName, 128)

                // Merchant ID - string to byte[16]
                merchId = stringToByteArray(config.merchantId9F16, 16)

                // Terminal ID - string to byte[8]
                termId = stringToByteArray(config.terminalId9F1C, 8)

                // MCC (Merchant Category Code) - hex string to byte[2]
                merchCateCode = hexStringToByteArray(config.mcc9F15).copyOf(2)

                // Acquirer ID - hex string to byte[6]
                if (config.acquierId9F01.isNotEmpty()) {
                    AcquierId = hexStringToByteArray(config.acquierId9F01).copyOf(6)
                }

                // Risk Management Data - hex string to byte[8]
                if (config.riskManagementData9F1D.isNotEmpty()) {
                    val rmd = hexStringToByteArray(config.riskManagementData9F1D)
                    riskManData = rmd.copyOf(8)
                    rMDLen = minOf(rmd.size, 8).toByte()
                }

                // Feature flags - string to byte (0 or 1)
                randTransSel = if (config.enableRandomTransSel == "1") 1.toByte() else 0.toByte()
                velocityCheck = if (config.enableVelocityCheck == "1") 1.toByte() else 0.toByte()
                selFlag = if (config.isFullMatch == "1") 1.toByte() else 0.toByte()

                // 🎯 CVM Limit = 500,000 VND (500 nghìn)
                cvmLmt = when (config.vendorName.uppercase(Locale.getDefault())) {
                    "VISA" -> hexStringToByteArray("000000500000").copyOf(6)  // 500k VND
                    "MASTERCARD" -> hexStringToByteArray("000000500000").copyOf(6)  // 500k VND
                    else -> hexStringToByteArray("000000500000").copyOf(6)  // Default: 500k VND
                }

                // 🎯 Terminal Contactless Limit = 5,000,000 VND (5 triệu)
                termClssLmt = when (config.vendorName.uppercase(Locale.getDefault())) {
                    "VISA" -> hexStringToByteArray("000005000000").copyOf(6)  // 5 triệu VND
                    "MASTERCARD" -> hexStringToByteArray("000005000000").copyOf(6)  // 5 triệu VND
                    else -> hexStringToByteArray("000005000000").copyOf(6)  // Default: 5 triệu VND
                }

                // Terminal offline floor limits
                val floorLimitBytes = if (config.floorLimit9F1B.isNotEmpty()) {
                    hexStringToByteArray(config.floorLimit9F1B).copyOf(6)
                } else {
                    hexStringToByteArray("000000000000").copyOf(6)  // 0 = OK
                }
                termOfflineFloorLmt = floorLimitBytes
                termClssOfflineFloorLmt = hexStringToByteArray("000000000000").copyOf(6)

                // Reference currency (if provided)
                if (config.referCurrencyCode9F3C.isNotEmpty()) {
                    referCurrCode = hexStringToByteArray(config.referCurrencyCode9F3C)
                }
                if (config.referCurrencyExp9F3D.isNotEmpty()) {
                    referCurrExp = stringToByte(config.referCurrencyExp9F3D)
                }

                // Kernel type (0=EMV Contact, 2=MasterCard Contactless, 3=Visa Contactless, etc.)
                kernelType = when (config.vendorName.uppercase(Locale.getDefault())) {
                    "MASTERCARD" -> 2.toByte()
                    "VISA" -> 3.toByte()
                    "AMEX" -> 4.toByte()
                    "JCB" -> 6.toByte()
                    else -> 0.toByte()
                }

                paramType = 2.toByte()

                ttq = when (config.vendorName.uppercase(Locale.getDefault())) {
                    "VISA" -> hexStringToByteArray("3600C080").copyOf(4)
                    "MASTERCARD" -> hexStringToByteArray("3600C080").copyOf(4)
                    "AMEX" -> hexStringToByteArray("2600C080").copyOf(4)
                    "JCB" -> hexStringToByteArray("3600C080").copyOf(4)
                    "NAPAS" -> hexStringToByteArray("3600C080").copyOf(4)
                    else -> hexStringToByteArray("26000080").copyOf(4)
                }

                clsStatusCheck = 1.toByte()

            } catch (e: Exception) {
            }
        }
    }
}