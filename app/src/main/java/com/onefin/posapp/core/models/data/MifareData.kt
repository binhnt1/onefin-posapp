package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MifareData(
    val sector0: SectorData,
    val sector1: SectorData,
    val sector2: SectorData?
) {
    data class SectorData(
        val block0: String, // Hex string (32 chars = 16 bytes)
        val block1: String, // Hex string (32 chars = 16 bytes)
        val block2: String  // Hex string (32 chars = 16 bytes)
    )

    fun getTrack2(): String = sector1.block0
    fun getSerialNumber(): String = sector0.block0


    fun getIcData(): String {
        return "${sector0.block0}|${sector0.block1}|${sector0.block2}|" +
                "${sector1.block0}|${sector1.block1}|${sector1.block2}"
    }
    fun getPanFromTrack2(): String? {
        val track2 = getTrack2()
        val separatorIndex = track2.indexOf('D')

        if (separatorIndex <= 0) {
            return null
        }

        return track2.substring(0, separatorIndex)
    }
    fun getExpiryFromTrack2(): String {
        return try {
            val track2 = getTrack2()
            val afterD = track2.substringAfter('D')

            if (afterD.length >= 4) {
                afterD.substring(0, 4) // YYMM
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getCardHolderName(icData: String): String? {
        try {
            val blocks = icData.split("|")

            // Thử đọc từ các block (bỏ qua block 0 vì là manufacturer data)
            for (i in 1 until blocks.size) {
                val blockData = blocks[i]
                val name = parseNameFromBlock(blockData)
                if (!name.isNullOrEmpty()) {
                    return name.trim()
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").uppercase()
        val len = cleanHex.length
        val data = ByteArray(len / 2)

        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
        }
        return data
    }
    private fun parseNameFromBlock(hexBlock: String): String? {
        if (hexBlock.isEmpty() || hexBlock.length % 2 != 0) return null

        try {
            // Convert hex to bytes
            val bytes = hexToBytes(hexBlock)

            // Thử decode UTF-8
            val text = String(bytes, Charsets.UTF_8).trim()

            // Check if it's printable text (tên người thường có ký tự từ 32-126 trong ASCII)
            if (text.isNotEmpty() && text.any { it.code in 32..126 || it.code > 127 }) {
                // Loại bỏ ký tự null và padding
                val cleanText = text.replace("\u0000", "").trim()
                if (cleanText.length >= 2) { // Tên ít nhất 2 ký tự
                    return cleanText
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }
}

data class NfcConfigResponse(
    @SerializedName("ispin")
    val ispin: Int,

    @SerializedName("nfckey")
    val nfckey: String,


    @SerializedName("nfckeytype")
    val nfckeytype: Int = 1,

    @SerializedName("nfclimit")
    val nfclimit: Long = 0L
) : Serializable {

    fun isPinRequired(): Boolean = ispin == 1

    fun hasTransactionLimit(): Boolean = nfclimit > 0

    fun getTransactionLimitAmount(): Double {
        return if (nfclimit > 0) {
            nfclimit / 100.0
        } else {
            0.0
        }
    }

    fun exceedsLimit(amount: Long): Boolean {
        if (!hasTransactionLimit()) return false
        return amount > nfclimit
    }
}

data class PkeyConfigResponse(
    @SerializedName("pkey")
    val pkey: String,

    @SerializedName("keyexpdate")
    val keyexpdate: String
) : Serializable