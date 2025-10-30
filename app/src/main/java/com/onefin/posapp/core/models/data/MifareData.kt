package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import timber.log.Timber
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
            Timber.d("🔍 === PARSING CARDHOLDER NAME ===")

            val blocks = icData.split("|")
            Timber.d("   Total blocks: ${blocks.size}")

            for (i in blocks.indices) {
                val blockHex = blocks[i]
                Timber.d("   📦 Block $i:")
                Timber.d("      Hex: $blockHex")

                if (blockHex.isEmpty() || blockHex.matches(Regex("^[0F]+$"))) {
                    Timber.d("      ⏭️  Skip (empty/padding)")
                    continue
                }

                try {
                    val bytes = hexToBytes(blockHex)
                    Timber.d("      Raw bytes: ${bytes.contentToString()}")

                    // Thử nhiều encoding
                    val utf8 = String(bytes, Charsets.UTF_8)
                    val ascii = String(bytes, Charsets.US_ASCII)
                    val iso = String(bytes, Charsets.ISO_8859_1)

                    Timber.d("      UTF-8: [$utf8]")
                    Timber.d("      ASCII: [$ascii]")
                    Timber.d("      ISO-8859-1: [$iso]")

                    // Kiểm tra xem có phải là text không
                    val cleanUtf8 = utf8.replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "").trim()
                    if (cleanUtf8.length >= 2 && cleanUtf8.any { it.isLetter() }) {
                        Timber.d("      ✅ Found potential name in UTF-8: [$cleanUtf8]")
                    }

                    val cleanAscii = ascii.replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "").trim()
                    if (cleanAscii.length >= 2 && cleanAscii.any { it.isLetter() }) {
                        Timber.d("      ✅ Found potential name in ASCII: [$cleanAscii]")
                    }

                } catch (e: Exception) {
                    Timber.e(e, "      ❌ Error parsing block $i")
                }
            }

            // Thử parse như code cũ
            for (i in 1 until blocks.size) {
                val blockData = blocks[i]
                val name = parseNameFromBlock(blockData)
                if (!name.isNullOrEmpty()) {
                    Timber.d("   ✅ FINAL NAME FOUND: [$name]")
                    return name.trim()
                }
            }

            Timber.d("   ❌ No name found in any block")
            return null

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception parsing cardholder name")
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

        // Skip empty blocks (all 0 or all F)
        if (hexBlock.matches(Regex("^[0F]+$"))) return null

        try {
            val bytes = hexToBytes(hexBlock)

            // Method 1: Tìm chuỗi ký tự liên tiếp có thể đọc được
            val validChars = bytes.filter { byte ->
                val char = byte.toInt() and 0xFF
                char in 32..126 || char in 0xC0..0xFF // ASCII printable + extended
            }

            if (validChars.size >= 2) {
                val text = String(validChars.toByteArray(), Charsets.ISO_8859_1).trim()
                if (text.length >= 2 && text.any { it.isLetter() }) {
                    return text
                }
            }

            // Method 2: Decode thẳng UTF-8 và clean
            val utf8Text = String(bytes, Charsets.UTF_8)
                .replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "") // Remove control chars
                .trim()

            if (utf8Text.length >= 2 && utf8Text.any { it.isLetter() }) {
                return utf8Text
            }

            // Method 3: Tìm pattern name trong EMV (tag 5F20)
            // Format: 5F20 [length] [data]
            if (hexBlock.contains("5F20", ignoreCase = true)) {
                val index = hexBlock.indexOf("5F20", ignoreCase = true)
                if (index + 6 < hexBlock.length) {
                    val lengthHex = hexBlock.substring(index + 4, index + 6)
                    val length = lengthHex.toIntOrNull(16) ?: 0

                    if (length > 0 && index + 6 + (length * 2) <= hexBlock.length) {
                        val nameHex = hexBlock.substring(index + 6, index + 6 + (length * 2))
                        val nameBytes = hexToBytes(nameHex)
                        val name = String(nameBytes, Charsets.ISO_8859_1)
                            .trim()
                            .replace(Regex("[^\\x20-\\x7E]"), "")

                        if (name.isNotEmpty()) {
                            return name
                        }
                    }
                }
            }

            // Method 4: Decode ISO-8859-1 (Latin-1) - common for cards
            val isoText = String(bytes, Charsets.ISO_8859_1)
                .replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "")
                .trim()

            if (isoText.length >= 2 && isoText.any { it.isLetter() }) {
                return isoText
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