package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class CapkData(
    @SerializedName("RID")
    val rid: String?,           // RID (5 bytes hex)

    @SerializedName("publicKeyIndex")
    val index: String?,         // Key Index (1 byte hex)

    @SerializedName("publicKeyHashAlgorithm")
    val hashAlgo: String?,      // Hash Algorithm (01=SHA-1)

    @SerializedName("PublicKeyAlgorithm")
    val arithInd: String?,      // Arithmetic Indicator (01=RSA)

    @SerializedName("publicKeyModulus")
    val modul: String?,         // Modulus (public key)

    @SerializedName("publicKeyExponent")
    val exponent: String?,      // Exponent (usually "03" or "010001")

    @SerializedName("publicKeyExpiredDate")
    val expDate: String?,       // Expiration date (YYMMDD)

    @SerializedName("checkValue")
    val checkSum: String?       // SHA-1 hash of modulus + exponent
)