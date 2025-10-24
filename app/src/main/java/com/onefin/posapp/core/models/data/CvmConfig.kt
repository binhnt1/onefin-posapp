package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

/**
 * Root CVM Configuration
 */
data class CvmConfig(
    @SerializedName("sale")
    val sale: SaleConfig
)

/**
 * Sale configuration chứa các card brands
 */
data class SaleConfig(
    @SerializedName("visa")
    val visa: CardBrandConfig? = null,

    @SerializedName("master")
    val master: CardBrandConfig? = null,

    @SerializedName("jcb")
    val jcb: CardBrandConfig? = null,

    @SerializedName("napas")
    val napas: CardBrandConfig? = null,

    @SerializedName("unionpay")
    val unionpay: CardBrandConfig? = null,

    @SerializedName("fallback")
    val fallback: String? = null
)

/**
 * Configuration cho từng card brand (Visa, Master, JCB, NAPAS, UnionPay)
 */
data class CardBrandConfig(
    @SerializedName("chip")
    val chip: CardModeConfig? = null,

    @SerializedName("contactless")
    val contactless: CardModeConfig? = null,

    @SerializedName("magstripe")
    val magstripe: CardModeConfig? = null
)

/**
 * Configuration cho từng card mode (chip, contactless, magstripe)
 */
data class CardModeConfig(
    @SerializedName("signature")
    val signature: CvmRule? = null,

    @SerializedName("pin")
    val pin: CvmRule? = null
)

/**
 * CVM Rule - quy tắc cho signature hoặc pin
 */
data class CvmRule(
    @SerializedName("amount")
    val amount: Long = 0,

    @SerializedName("needSign")
    val needSign: String? = null,

    @SerializedName("needPin")
    val needPin: String? = null
) {
    /**
     * Kiểm tra có cần signature không
     */
    fun isSignatureRequired(): Boolean {
        return needSign?.equals("Y", ignoreCase = true) == true
    }

    /**
     * Kiểm tra có cần PIN không
     */
    fun isPinRequired(): Boolean {
        return needPin?.equals("Y", ignoreCase = true) == true
    }

    /**
     * Kiểm tra amount có vượt ngưỡng không
     */
    fun isAmountAboveThreshold(transAmount: Long): Boolean {
        return transAmount >= amount
    }
}

data class CvmTlvValues(
    // Signature limits
    val cvmRequiredLimit: String,           // DF811E - CVM Required Limit
    val readerCvmRequiredLimit: String,     // DF812C - Reader CVM Required Limit

    // PIN limits
    val pinRequiredLimit: String,           // Custom - sẽ dùng để quyết định logic

    // Contactless limits
    val contactlessTransLimit: String,      // DF811F - Contactless Transaction Limit
    val contactlessCvmLimit: String,        // DF8121 - Contactless CVM Limit

    // Flags
    val isSignatureRequired: Boolean,
    val isPinRequired: Boolean
)