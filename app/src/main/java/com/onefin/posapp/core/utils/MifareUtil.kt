package com.onefin.posapp.core.utils

import android.annotation.SuppressLint
import android.text.TextUtils
import com.onefin.posapp.core.config.MifareConstants
import com.onefin.posapp.core.models.data.MifareData
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object MifareUtil {

    fun buildPinBlock(pin: String, pan: String, pkeyHex: String): String? {
        return try {
            // Validate inputs
            if (pin.length !in 4..12) {
                Timber.e("❌ Invalid PIN length: ${pin.length}, must be 4-12 digits")
                return null
            }

            if (!pin.all { it.isDigit() }) {
                Timber.e("❌ PIN must contain only digits")
                return null
            }

            if (pkeyHex.length != 32) {
                Timber.e("❌ Invalid pkey length: ${pkeyHex.length}, expected 32 hex chars")
                return null
            }

            // Step 1: Build PIN block part 1
            // Format: 0 + Length(1 digit) + PIN(4-12 digits) + F padding to 16 hex chars
            val pinLength = pin.length
            val part1 = "0$pinLength$pin".padEnd(16, 'F')

            // Step 2: Build PAN block part 2
            // Format: 0000 + last 12 digits of PAN (excluding check digit)
            val panDigits = pan.filter { it.isDigit() }
            if (panDigits.length < 13) {
                Timber.e("❌ PAN too short: ${panDigits.length}, need at least 13 digits")
                return null
            }

            // Take last 13 digits, then drop the last one (check digit)
            val last12Digits = panDigits.takeLast(13).dropLast(1)
            val part2 = "0000$last12Digits"

            // Step 3: XOR two parts
            val xored = xorHexStrings(part1, part2)

            // Step 4: Encrypt with 3DES
            val keyBytes = hexStr2Bytes(pkeyHex)
            val dataBytes = hexStr2Bytes(xored)
            val encrypted = encrypt3DES(dataBytes, keyBytes)

            // Step 5: Convert to hex string
            encrypted.joinToString("") { "%02X".format(it) }

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception building PIN block")
            null
        }
    }

    fun readMifareCard(readCardOpt: ReadCardOptV2, keyHex: String): MifareData? {
        try {
            val sector0 = readMifareSector(readCardOpt, 0, keyHex)
            if (sector0 != null) {
                val sector1 = readMifareSector(readCardOpt, 1, keyHex)
                if (sector1 != null) {
                    // Try sector 2 (optional)
                    val sector2 = readMifareSector(readCardOpt, 2, keyHex)
                    return MifareData(
                        sector0 = sector0,
                        sector1 = sector1,
                        sector2 = sector2
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception reading Mifare card")
        }
        return null
    }

    fun readMifareSector(readCardOpt: ReadCardOptV2, sector: Int, keyHex: String): MifareData.SectorData? {
        return try {
            val keyBytes = hexStr2Bytes(keyHex)
            if (keyBytes.size != 6) {
                return null
            }

            val startBlock = sector * MifareConstants.BLOCKS_PER_SECTOR
            val authResult = readCardOpt.mifareAuth(
                MifareConstants.KEY_TYPE_A,
                startBlock,
                keyBytes
            )
            if (authResult != 0) {
                return null
            }

            // Read 3 blocks
            val block0 = readMifareBlock(readCardOpt, startBlock + 0)
            val block1 = readMifareBlock(readCardOpt, startBlock + 1)
            val block2 = readMifareBlock(readCardOpt, startBlock + 2)
            if (block0 == null || block1 == null || block2 == null) {
                return null
            }

            Timber.d("   ✅ Read 3 blocks successfully")
            Timber.d("      Block 0: ${block0.take(32)}...")
            Timber.d("      Block 1: ${block1.take(32)}...")
            Timber.d("      Block 2: ${block2.take(32)}...")

            MifareData.SectorData(
                block0 = block0,
                block1 = block1,
                block2 = block2
            )

        } catch (e: Exception) {
            Timber.e(e, "   ❌ Exception reading sector $sector")
            null
        }
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
    private fun xorHexStrings(hex1: String, hex2: String): String {
        require(hex1.length == hex2.length) { "Hex strings must have same length" }

        val bytes1 = hexStr2Bytes(hex1)
        val bytes2 = hexStr2Bytes(hex2)
        val result = ByteArray(bytes1.size)

        for (i in bytes1.indices) {
            result[i] = (bytes1[i].toInt() xor bytes2[i].toInt()).toByte()
        }

        return result.joinToString("") { "%02X".format(it) }
    }
    @SuppressLint("GetInstance")
    private fun encrypt3DES(data: ByteArray, key: ByteArray): ByteArray {
        return try {
            require(data.size == 8) { "Data must be 8 bytes for DES block" }
            require(key.size == 16) { "Key must be 16 bytes for Two-key 3DES" }

            // Create 24-byte key from 16-byte key (Two-key 3DES: K1, K2, K1)
            val key24 = ByteArray(24)
            System.arraycopy(key, 0, key24, 0, 16)  // Copy K1, K2
            System.arraycopy(key, 0, key24, 16, 8)  // Copy K1 again

            // Create SecretKeySpec
            val secretKey = SecretKeySpec(key24, "DESede")

            // Create cipher
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Encrypt
            cipher.doFinal(data)

        } catch (e: Exception) {
            Timber.e(e, "❌ 3DES encryption failed")
            throw e
        }
    }
    private fun readMifareBlock(readCardOpt: ReadCardOptV2, block: Int): String? {
        return try {
            val outData = ByteArray(16) // Mifare Classic block = 16 bytes
            val result = readCardOpt.mifareReadBlock(block, outData)

            if (result != 16) {
                Timber.e("❌ Failed to read block $block: result=$result")
                return null
            }

            // Convert to hex string (uppercase)
            outData.joinToString("") { "%02X".format(it) }

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception reading block $block")
            null
        }
    }
}