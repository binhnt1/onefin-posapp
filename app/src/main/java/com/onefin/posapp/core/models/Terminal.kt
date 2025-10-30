package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName
import com.onefin.posapp.core.models.data.CapkData

/**
 * Terminal model
 */
data class Terminal(
    @SerializedName("BDK")
    val bdk: String? = "",

    @SerializedName("KSN")
    val ksn: String? = "",

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
    val accountName: String = "",
    
    @SerializedName("BankNapasId")
    val bankNapasId: String = "",
    
    @SerializedName("AccountNumber")
    val accountNumber: String = "",

    @SerializedName("EvmConfigs")
    val evmConfigs: List<EvmConfig>? = null,

    @SerializedName("MerchantCompany")
    val merchantCompany: String = "",
    
    @SerializedName("SystemConfig")
    val systemConfig: SystemConfig? = null,
    
    @SerializedName("MerchantConfig")
    val merchantConfig: Map<String, Any?>? = null
)

/**
 * Evm Configs model
 */
data class EvmConfig(
    @SerializedName("AppName")
    var appName: String = "",

    @SerializedName("AID_9F06")
    val aid9F06: String = "A0000000041010",

    @SerializedName("MCC_9F15")
    val mcc9F15: String = "9999",

    @SerializedName("TACDenial")
    val tacDenial: String = "0010000000",

    @SerializedName("TACOnline")
    val tacOnline: String = "D84004F800",

    @SerializedName("threshold")
    val threshold: String = "",

    @SerializedName("vendorName")
    val vendorName: String = "MasterCard",

    @SerializedName("TACDefault")
    val tacDefault: String = "D84000A800",

    @SerializedName("defaultDDOL")
    val defaultDDOL: String = "9F3704",

    @SerializedName("defaultTDOL")
    val defaultTDOL: String = "9F02065F2A029A039C0195059F3704",

    @SerializedName("isFullMatch")
    val isFullMatch: String = "0",

    @SerializedName("version_9F09")
    val version9F09: String = "0002",

    @SerializedName("merchantName")
    val merchantName: String = "A",

    @SerializedName("targetPercent")
    val targetPercent: String = "",

    @SerializedName("acquierId_9F01")
    val acquierId9F01: String = "",

    @SerializedName("floorLimit_9F1B")
    val floorLimit9F1B: String = "000000000000",

    @SerializedName("merchantId_9F16")
    val merchantId9F16: String = "12345678901234",

    @SerializedName("terminalId_9F1C")
    val terminalId9F1C: String = "12345678",

    @SerializedName("transCurrencyExp")
    var transCurrencyExp: String = "00",

    @SerializedName("terminalCap_9F33")
    val terminalCap9F33: String = "E0B8C8",

    @SerializedName("maxTargetPercent")
    val maxTargetPercent: String = "99",

    @SerializedName("countryCode_9F1A")
    val countryCode9F1A: String = "0704",

    @SerializedName("terminalType_9F35")
    val terminalType9F35: String = "22",

    @SerializedName("enableTerminalInfo")
    val enableTerminalInfo: String = "1",

    @SerializedName("exTerminalCap_9F40")
    val exTerminalCap9F40: String = "",

    @SerializedName("enableVelocityCheck")
    val enableVelocityCheck: String = "1",

    @SerializedName("enableRandomTransSel")
    val enableRandomTransSel: String = "1",

    @SerializedName("enableFloorLimitCheck")
    val enableFloorLimitCheck: String = "1",

    @SerializedName("referCurrencyExp_9F3D")
    val referCurrencyExp9F3D: String = "",

    @SerializedName("referCurrencyCode_9F3C")
    val referCurrencyCode9F3C: String = "",

    @SerializedName("transCurrencyCode_5F2A")
    val transCurrencyCode5F2A: String = "0704",

    @SerializedName("riskManagementData_9F1D")
    val riskManagementData9F1D: String = "6C00800000000000",

    @SerializedName("displayOfflinePINRetryTime")
    val displayOfflinePINRetryTime: String = "1",

    @SerializedName("capks")
    val capks: List<CapkData>? = null
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