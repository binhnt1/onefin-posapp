package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName

/**
 * Terminal model
 */
data class Terminal(
    @SerializedName("MID")
    val mid: String = "",
    
    @SerializedName("TID")
    val tid: String = "",
    
    @SerializedName("Name")
    val name: String = "",
    
    @SerializedName("Logo")
    val logo: String = "",
    
    @SerializedName("Phone")
    val phone: String = "",
    
    @SerializedName("Image")
    val image: String = "",
    
    @SerializedName("Provider")
    val provider: String = "",
    
    @SerializedName("BankName")
    val bankName: String? = null,
    
    @SerializedName("AccountName")
    val accountName: String? = null,
    
    @SerializedName("BankNapasId")
    val bankNapasId: String? = null,
    
    @SerializedName("AccountNumber")
    val accountNumber: String? = null,
    
    @SerializedName("SystemConfig")
    val systemConfig: SystemConfig? = null,
    
    @SerializedName("MerchantConfig")
    val merchantConfig: MerchantConfig? = null
)

/**
 * NFC Config model
 */
data class NfcConfig(
    @SerializedName("ispin")
    val isPin: String = "0",
    
    @SerializedName("nfckey")
    val nfcKey: String = "",
    
    @SerializedName("currency")
    val currency: String = "704",
    
    @SerializedName("nfclimit")
    val nfcLimit: String = "0",
    
    @SerializedName("nfckeytype")
    val nfcKeyType: String = "1"
)

/**
 * System Config model
 */
data class SystemConfig(
    @SerializedName("RabbitUrl")
    val rabbitUrl: String = "",
    
    @SerializedName("AllowCancel")
    val allowCancel: Boolean = false
)

/**
 * Merchant Config model
 */
data class MerchantConfig(
    @SerializedName("type")
    val type: String = "",
    
    @SerializedName("driver")
    val driver: String = "",
    
    @SerializedName("employee")
    val employee: String = "",
    
    @SerializedName("nfcConfig")
    val nfcConfig: NfcConfig? = null
)