package com.onefin.posapp.core.utils

import android.annotation.SuppressLint

object VietQRHelper {
    @SuppressLint("DefaultLocale")
    fun buildVietQRString(
        bankId: String,
        amount: Long = 0,
        accountNumber: String,
        description: String = ""
    ): String {
        val qrCode = StringBuilder()
        qrCode.append("000201")
        qrCode.append("010212")

        // Consumer account info
        val consumerAcc = StringBuilder()
        consumerAcc.append("00").append(String.format("%02d", bankId.length)).append(bankId)
        consumerAcc.append("01").append(String.format("%02d", accountNumber.length)).append(accountNumber)
        val consumerAccInfo = StringBuilder()
            .append("0010A000000727")
            .append("01").append(String.format("%02d", consumerAcc.length)).append(consumerAcc)
            .append("0208QRIBFTTA")
        qrCode.append("38").append(String.format("%02d", consumerAccInfo.length)).append(consumerAccInfo)

        qrCode.append("5303704")
        if (amount > 0) {
            qrCode.append("54").append(String.format("%02d", amount.toString().length)).append(amount.toString())
        }

        qrCode.append("5802VN")
        if (description.isNotEmpty()) {
            qrCode.append("62").append(String.format("%02d", description.length + 4))
                .append("08").append(String.format("%02d", description.length)).append(description)
        }
        qrCode.append("6304").append(String.format("%04X", calculateCRC(qrCode.toString())))
        return qrCode.toString()
    }

    fun calculateCRC(input: String): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021
        input.toByteArray(Charsets.ISO_8859_1).forEach { b ->
            for (i in 0..7) {
                val bit = (b.toInt() shr (7 - i) and 1) == 1
                val c15 = (crc shr 15 and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) crc = crc xor polynomial
            }
        }
        return crc and 0xFFFF
    }
}