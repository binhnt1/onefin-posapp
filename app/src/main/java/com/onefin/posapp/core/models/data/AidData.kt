package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class AidData(
    @SerializedName("paypass")
    val paypass: AidEntry? = null,

    @SerializedName("paywave")
    val paywave: AidEntry? = null,

    @SerializedName("pure")
    val pure: AidEntry? = null,

    @SerializedName("jcb")
    val jcb: AidEntry? = null,

    @SerializedName("qpboc")
    val qpboc: AidEntry? = null
) {
    fun getEntry(): Pair<String, AidEntry>? {
        return when {
            paypass != null -> "paypass" to paypass
            paywave != null -> "paywave" to paywave
            pure != null -> "pure" to pure
            jcb != null -> "jcb" to jcb
            qpboc != null -> "qpboc" to qpboc
            else -> null
        }
    }
}

data class AidEntry(
    @SerializedName("baseAID")
    val baseAid: BaseAid,

    @SerializedName("paypassAID")
    val paypassAid: PaypassAid? = null,

    @SerializedName("paywaveAID")
    val paywaveAid: PaywaveAid? = null,

    @SerializedName("pureAID")
    val pureAid: PureAid? = null,

    @SerializedName("jcbAID")
    val jcbAid: JcbAid? = null,

    @SerializedName("qpbocAID")
    val qpbocAid: QpbocAid? = null
)

data class BaseAid(
    @SerializedName("AID_9F06")
    val aid: String,

    @SerializedName("AppName")
    val appName: String? = null,

    @SerializedName("MCC_9F15")
    val mcc: String? = null,

    @SerializedName("TACDefault")
    val tacDefault: String? = null,

    @SerializedName("TACDenial")
    val tacDenial: String? = null,

    @SerializedName("TACOnline")
    val tacOnline: String? = null,

    @SerializedName("acquierId_9F01")
    val acquirerId: String? = null,

    @SerializedName("countryCode_9F1A")
    val countryCode: String? = null,

    @SerializedName("defaultDDOL")
    val defaultDdol: String? = null,

    @SerializedName("defaultTDOL")
    val defaultTdol: String? = null,

    @SerializedName("floorLimit_9F1B")
    val floorLimit: String? = null,

    @SerializedName("terminalCap_9F33")
    val terminalCap: String? = null,

    @SerializedName("terminalType_9F35")
    val terminalType: String? = null,

    @SerializedName("transCurrencyCode_5F2A")
    val transCurrencyCode: String? = null,

    @SerializedName("version_9F09")
    val version: String? = null,

    @SerializedName("exTerminalCap_9F40")
    val exTerminalCap: String? = null,

    @SerializedName("riskManagementData_9F1D")
    val riskManagementData: String? = null,

    @SerializedName("merchantId_9F16")
    val merchantId: String? = null,

    @SerializedName("terminalId_9F1C")
    val terminalId: String? = null,

    @SerializedName("merchantName")
    val merchantName: String? = null,

    @SerializedName("vendorName")
    val vendorName: String? = null
)

/**
 * PayPass specific AID (MasterCard Contactless)
 */
data class PaypassAid(
    @SerializedName("CLCVMLimit")
    val clCvmLimit: String? = null,

    @SerializedName("CLFloorLimit")
    val clFloorLimit: String? = null,

    @SerializedName("CLTransLimitCDCVM_DF8125")
    val clTransLimitCdcvm: String? = null,

    @SerializedName("CLTransLimitNoCDCVM_DF8124")
    val clTransLimitNoCdcvm: String? = null,

    @SerializedName("TTQ_9F66")
    val ttq: String? = null,

    @SerializedName("transType_9C")
    val transType: String? = null
)

/**
 * PayWave specific AID (Visa Contactless)
 */
data class PaywaveAid(
    @SerializedName("CLCVMLimit")
    val clCvmLimit: String? = null,

    @SerializedName("CLFloorLimit")
    val clFloorLimit: String? = null,

    @SerializedName("CLTransLimit")
    val clTransLimit: String? = null,

    @SerializedName("TTQ_9F66")
    val ttq: String? = null
)

/**
 * Pure specific AID (NAPAS)
 */
data class PureAid(
    @SerializedName("CLCVMLimit")
    val clCvmLimit: String? = null,

    @SerializedName("CLFloorLimit")
    val clFloorLimit: String? = null,

    @SerializedName("CLTransLimit")
    val clTransLimit: String? = null,

    @SerializedName("TTQ_9F66")
    val ttq: String? = null,

    @SerializedName("mCTLSAppCapa")
    val mctlsAppCapa: String? = null,

    @SerializedName("transType_9C")
    val transType: String? = null
)

/**
 * JCB specific AID
 */
data class JcbAid(
    @SerializedName("CLCVMLimit")
    val clCvmLimit: String? = null,

    @SerializedName("CLFloorLimit")
    val clFloorLimit: String? = null,

    @SerializedName("CLTransLimit")
    val clTransLimit: String? = null,

    @SerializedName("transType_9C")
    val transType: String? = null
)

/**
 * QPBOC specific AID (UnionPay)
 */
data class QpbocAid(
    @SerializedName("CLCVMLimit")
    val clCvmLimit: String? = null,

    @SerializedName("CLFloorLimit")
    val clFloorLimit: String? = null,

    @SerializedName("CLTransLimit")
    val clTransLimit: String? = null,

    @SerializedName("TTQ_9F66")
    val ttq: String? = null
)