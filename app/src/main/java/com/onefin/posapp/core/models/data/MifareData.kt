package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MifareData(
    val sector0: SectorData,
    val sector1: SectorData,
    val sector2: SectorData?
) {
    data class SectorData(
        val block0: String, // Hex string (32 chars = 16 bytes)
        val block1: String, // Hex string (32 chars = 16 bytes)
        val block2: String  // Hex string (32 chars = 16 bytes)
    )

    fun getSerialNumber(): String = sector0.block0

    fun getTrack2(): String = sector1.block0

    fun getIcData(): String {
        return "${sector0.block0}|${sector0.block1}|${sector0.block2}|" +
                "${sector1.block0}|${sector1.block1}|${sector1.block2}|" +
                "${sector2?.block0}|${sector2?.block1}|${sector2?.block2}"
    }

    fun getPanFromTrack2(): String? {
        val track2 = getTrack2()
        val separatorIndex = track2.indexOf('D')

        if (separatorIndex <= 0) {
            return null
        }

        return track2.substring(0, separatorIndex)
    }

    fun getExpiryFromTrack2(): String {
        return try {
            val track2 = getTrack2()
            val afterD = track2.substringAfter('D')

            if (afterD.length >= 4) {
                afterD.substring(0, 4) // YYMM
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

data class NfcConfigResponse(
    @SerializedName("ispin")
    val ispin: Int,

    @SerializedName("nfckey")
    val nfckey: String,


    @SerializedName("nfckeytype")
    val nfckeytype: Int = 1,

    @SerializedName("nfclimit")
    val nfclimit: Long = 0L
) : Serializable {

    fun isPinRequired(): Boolean = ispin == 1

    fun hasTransactionLimit(): Boolean = nfclimit > 0

    fun getTransactionLimitAmount(): Double {
        return if (nfclimit > 0) {
            nfclimit / 100.0
        } else {
            0.0
        }
    }

    fun exceedsLimit(amount: Long): Boolean {
        if (!hasTransactionLimit()) return false
        return amount > nfclimit
    }
}

data class PkeyConfigResponse(
    @SerializedName("pkey")
    val pkey: String,

    @SerializedName("keyexpdate")
    val keyexpdate: String
) : Serializable