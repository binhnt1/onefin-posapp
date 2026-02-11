package com.onefin.posapp.core.utils

import android.content.Context
import com.google.gson.Gson
import com.onefin.posapp.core.models.data.CvmConfig
import com.onefin.posapp.core.models.data.CvmTlvValues

object ResourceHelper {
    fun getDefaultTlvValues(): CvmTlvValues {
        return CvmTlvValues(
            cvmRequiredLimit = "000000000000",
            readerCvmRequiredLimit = "000000000000",
            pinRequiredLimit = "000000000000",
            contactlessTransLimit = "999999999999",
            contactlessCvmLimit = "999999999999",
            isSignatureRequired = true,
            isPinRequired = false
        )
    }
    fun loadCvmFromAssets(context: Context): CvmConfig? {
        try {
            val inputStream = context.assets.open("CVM.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val cvmConfig: CvmConfig = Gson().fromJson(jsonString, CvmConfig::class.java)
            return cvmConfig

        } catch (_: Exception) {
            return null
        }
    }
    fun convertToTlv(cvmConfig: CvmConfig, cardBrand: String, entryMode: String): CvmTlvValues {

        // Lấy config cho card brand
        val brandConfig = when (cardBrand.lowercase()) {
            "jcb" -> cvmConfig.sale.jcb
            "visa" -> cvmConfig.sale.visa
            "napas" -> cvmConfig.sale.napas
            "master", "mastercard" -> cvmConfig.sale.master
            "unionpay", "union pay" -> cvmConfig.sale.unionpay
            else -> null
        }

        // Lấy config cho entry mode
        if (brandConfig != null) {
            val modeConfig = when (entryMode.lowercase()) {
                "chip" -> brandConfig.chip
                "magstripe" -> brandConfig.magstripe
                "contactless" -> brandConfig.contactless
                else -> null
            }

            if (modeConfig != null) {
                val signatureRule = modeConfig.signature
                val signatureAmount = signatureRule?.amount ?: 0L
                val needSignature = signatureRule?.isSignatureRequired() ?: false

                // Parse PIN rules
                val pinRule = modeConfig.pin
                val pinAmount = pinRule?.amount ?: 0L
                val needPin = pinRule?.isPinRequired() ?: false

                // Convert amounts to hex strings (12 characters)
                val signatureLimitHex = amountToHex12(signatureAmount)
                val pinLimitHex = amountToHex12(pinAmount)

                // Logic cho contactless
                val contactlessLimit = if (entryMode.lowercase() == "contactless") {
                    if (signatureAmount > 0) {
                        signatureLimitHex
                    } else {
                        "999999999999"
                    }
                } else {
                    "999999999999"
                }
                val contactlessCvmLimit = if (entryMode.lowercase() == "contactless") {
                    if (needSignature || needPin) {
                        minOf(signatureAmount, pinAmount).let {
                            if (it == 0L) signatureLimitHex else amountToHex12(it)
                        }
                    } else {
                        "999999999999"
                    }
                } else {
                    "999999999999"
                }
                return CvmTlvValues(
                    isPinRequired = needPin,
                    pinRequiredLimit = pinLimitHex,
                    isSignatureRequired = needSignature,
                    cvmRequiredLimit = signatureLimitHex,
                    contactlessTransLimit = contactlessLimit,
                    contactlessCvmLimit = contactlessCvmLimit,
                    readerCvmRequiredLimit = signatureLimitHex,
                )
            }
        }
        return getDefaultTlvValues()
    }

    private fun amountToHex12(amount: Long): String {
        return amount.toString().padStart(12, '0')
    }
}