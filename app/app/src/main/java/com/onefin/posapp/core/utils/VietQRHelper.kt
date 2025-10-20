package com.onefin.posapp.core.utils

object VietQRHelper {
    fun buildVietQRString(
        bankNapasId: String,
        accountNumber: String,
        amount: Long = 0,
        info: String = ""
    ): String {
        // --- Merchant Account Information (ID 38) ---
        val f38_00_guid = "A000000727"
        val f38_01_data = "QRIBFTTA"
        val f38_02_bankId = bankNapasId
        val f38_03_accountNo = accountNumber

        val f38_01_block = "01${f38_01_data.length.toString().padStart(2, '0')}$f38_01_data"
        val f38_02_block = "02${f38_02_bankId.length.toString().padStart(2, '0')}$f38_02_bankId"
        val f38_03_block = "03${f38_03_accountNo.length.toString().padStart(2, '0')}$f38_03_accountNo"

        val merchantAccountInfoData = "00${f38_00_guid.length.toString().padStart(2, '0')}$f38_00_guid$f38_01_block$f38_02_block$f38_03_block"
        val merchantAccountInfo = "38${merchantAccountInfoData.length.toString().padStart(2, '0')}$merchantAccountInfoData"

        // --- Các trường khác ---
        val version = "000201"
        val initMethod = "010212"
        val countryCode = "5802VN"
        val transactionCurrency = "5303704" // VND

        val transactionAmount = if (amount > 0) {
            val amountStr = amount.toString()
            "54${amountStr.length.toString().padStart(2, '0')}$amountStr"
        } else {
            ""
        }

        val additionalInfo = if (info.isNotEmpty()) {
            val purposeOfTransaction = "08${info.length.toString().padStart(2, '0')}$info"
            "62${purposeOfTransaction.length.toString().padStart(2, '0')}$purposeOfTransaction"
        } else {
            ""
        }

        val payloadWithoutCRC = "$version$initMethod$merchantAccountInfo$transactionCurrency$transactionAmount$countryCode$additionalInfo" +
                "6304"
        val crc = calculateCRC16(payloadWithoutCRC)
        return "$payloadWithoutCRC$crc"
    }

    /**
     * Tính toán CRC-16/CCITT-FALSE
     */
    private fun calculateCRC16(data: String): String {
        var crc = 0xFFFF
        val bytes = data.toByteArray(Charsets.UTF_8)

        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
            }
        }

        return (crc and 0xFFFF).toString(16).uppercase().padStart(4, '0')
    }
}