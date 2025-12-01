package com.onefin.posapp.core.utils

import timber.log.Timber

object EmvDataValidator {

    // Tags bắt buộc cho NAPAS transaction
    private val REQUIRED_TAGS = listOf(
        "9F26", // Application Cryptogram (ARQC)
        "9F27", // Cryptogram Information Data (CID)
        "9F36", // Application Transaction Counter (ATC)
        "95",   // Terminal Verification Results (TVR)
        "9A",   // Transaction Date
        "9C",   // Transaction Type
        "9F02", // Amount Authorized
    )

    // Tags nên remove vì không cần thiết cho NAPAS
    private val TAGS_TO_REMOVE = listOf(
        "9F35", // Terminal Type (không cần thiết)
        "9F41", // Transaction Sequence Counter (không cần thiết)
        "50",   // Application Label (không cần thiết)
        "5F20", // Cardholder Name (không cần thiết)
        "9F0B", // Cardholder Name Extended (không cần thiết)
        "5F2D", // Language Preference (không cần thiết)
        "9F09", // Application Version Number (không cần thiết)
        "9F1E", // IFD Serial Number (không cần thiết)
    )

    /**
     * Validate và clean EMV data
     * Remove các tag không cần thiết, empty tags, và invalid data
     */
    fun validateAndCleanEmvData(emvData: String): Result<String> {
        return try {
            if (emvData.isEmpty()) {
                return Result.failure(Exception("EMV data is empty"))
            }

            Timber.d("📦 Validating EMV data (length: ${emvData.length})...")

            val tags = parseEmvTlv(emvData)
            Timber.d("   Parsed ${tags.size} tags")

            // Remove invalid/empty tags
            val validTags = tags.filter { (tag, value) ->
                when {
                    // ❌ Remove tag 5A nếu rỗng hoặc chỉ có length byte
                    tag == "5A" && value.length <= 2 -> {
                        Timber.w("   ❌ Removing empty PAN tag (5A)")
                        false
                    }

                    // ❌ Remove tag 57 (Track2 trong EMV)
                    // Lý do: Track2 đã có riêng, và tag 57 thường có "D" thay vì "="
                    tag == "57" -> {
                        Timber.w("   ❌ Removing Track2 EMV tag (57)")
                        false
                    }

                    // ❌ Remove tag 56 nếu rỗng
                    tag == "56" && (value.isEmpty() || value == "00") -> {
                        Timber.w("   ❌ Removing empty Track1 tag (56)")
                        false
                    }

                    // ❌ Remove tag 9F34 nếu rỗng (CVM Results)
                    tag == "9F34" && (value == "00" || value.isEmpty()) -> {
                        Timber.w("   ❌ Removing empty CVM Results (9F34)")
                        false
                    }

                    // ❌ Remove các tag không cần thiết cho NAPAS
                    tag in TAGS_TO_REMOVE -> {
                        Timber.d("   🗑️ Removing unnecessary tag: $tag")
                        false
                    }

                    // ❌ Remove empty values
                    value.isEmpty() -> {
                        Timber.w("   ❌ Removing empty tag: $tag")
                        false
                    }

                    // ✅ Keep valid tags
                    else -> true
                }
            }

            Timber.d("   ✅ Kept ${validTags.size} valid tags (removed ${tags.size - validTags.size})")

            // Check required tags
            val missingTags = REQUIRED_TAGS.filter { !validTags.containsKey(it) }
            if (missingTags.isNotEmpty()) {
                Timber.w("   ⚠️ Missing recommended tags: ${missingTags.joinToString()}")
                // Don't fail, just log warning - some tags may be optional
            }

            // Log important tags for debugging
            logImportantTags(validTags)

            // Build cleaned EMV data
            val cleanedData = buildEmvTlv(validTags)
            Timber.d("   ✅ Cleaned EMV data length: ${cleanedData.length}")

            Result.success(cleanedData)

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to validate EMV data")
            Result.failure(e)
        }
    }

    /**
     * Parse EMV TLV data thành Map
     */
    fun parseEmvTlv(emvData: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        val data = emvData.uppercase()

        try {
            while (index < data.length) {
                if (index + 2 > data.length) break

                // Read tag (1 or 2 bytes)
                val firstByte = data.substring(index, index + 2)
                val tag = if ((firstByte.toInt(16) and 0x1F) == 0x1F) {
                    // Two-byte tag
                    if (index + 4 > data.length) break
                    data.substring(index, index + 4).also { index += 4 }
                } else {
                    // One-byte tag
                    firstByte.also { index += 2 }
                }

                if (index + 2 > data.length) break

                // Read length
                val lengthByte = data.substring(index, index + 2).toInt(16)
                index += 2

                val length = if ((lengthByte and 0x80) == 0x80) {
                    // Multi-byte length
                    val numBytes = lengthByte and 0x7F
                    if (index + numBytes * 2 > data.length) break
                    val lengthHex = data.substring(index, index + numBytes * 2)
                    index += numBytes * 2
                    lengthHex.toInt(16)
                } else {
                    lengthByte
                }

                // Read value
                val valueEndIndex = index + length * 2
                if (valueEndIndex > data.length) break

                val value = data.substring(index, valueEndIndex)
                index = valueEndIndex

                result[tag] = value
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing EMV TLV at index $index")
        }

        return result
    }

    /**
     * Build EMV TLV data từ Map
     */
    fun buildEmvTlv(tags: Map<String, String>): String {
        val sb = StringBuilder()

        // Sort tags để đảm bảo thứ tự consistent
        val sortedTags = tags.toSortedMap()

        sortedTags.forEach { (tag, value) ->
            if (value.isNotEmpty()) {
                sb.append(tag)

                // Calculate and append length
                val length = value.length / 2
                if (length < 128) {
                    sb.append(String.format("%02X", length))
                } else {
                    // Multi-byte length (not common for our use case)
                    sb.append(String.format("%02X", 0x81))
                    sb.append(String.format("%02X", length))
                }

                sb.append(value)
            }
        }

        return sb.toString()
    }

    /**
     * Log các tag quan trọng để debug
     */
    private fun logImportantTags(tags: Map<String, String>) {
        val importantTags = mapOf(
            "9F26" to "Application Cryptogram (ARQC)",
            "9F27" to "Cryptogram Information Data (CID)",
            "9F36" to "Application Transaction Counter (ATC)",
            "95" to "Terminal Verification Results (TVR)",
            "9A" to "Transaction Date",
            "9C" to "Transaction Type",
            "9F02" to "Amount Authorized",
            "9F03" to "Amount Other",
            "9F10" to "Issuer Application Data (IAD)",
            "9F33" to "Terminal Capabilities",
            "9F37" to "Unpredictable Number",
            "82" to "Application Interchange Profile (AIP)",
            "84" to "Dedicated File Name (AID)",
            "9F06" to "Application Identifier (AID)"
        )

        Timber.d("   📋 ====== IMPORTANT EMV TAGS ======")
        var foundCount = 0
        importantTags.forEach { (tag, name) ->
            val value = tags[tag]
            if (value != null) {
                Timber.d("      ✅ $tag ($name): ${value.take(20)}${if (value.length > 20) "..." else ""}")
                foundCount++
            }
        }
        Timber.d("   Found $foundCount/${importantTags.size} important tags")
        Timber.d("   ===================================")
    }
}