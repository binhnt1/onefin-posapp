package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data  class RequestSale(
    @SerializedName("data")
    val data: Data,

    @SerializedName("requestId")
    val requestId: String,

    @SerializedName("requestData")
    val requestData: PaymentAppRequest,
) : Serializable {
    data class Data(
        @SerializedName("card")
        val card: Card,

        @SerializedName("bank")
        val bank: String? = null,

        @SerializedName("device")
        val device: Device,

        @SerializedName("payment")
        val payment: PaymentData
    ) : Serializable {
        data class Card(
            @SerializedName("tc")
            val tc: String? = null,

            @SerializedName("aid")
            val aid: String? = null,

            @SerializedName("ksn")
            val ksn: String? = null,

            @SerializedName("pin")
            val pin: String? = null,

            @SerializedName("type")
            val type: String? = null,

            @SerializedName("mode")
            val mode: String? = null,

            @SerializedName("newPin")
            val newPin: String? = null,

            @SerializedName("track1")
            val track1: String? = null,

            @SerializedName("track2")
            val track2: String? = null,

            @SerializedName("track3")
            val track3: String? = null,

            @SerializedName("emvData")
            val emvData: String? = null,

            @SerializedName("clearPan")
            val clearPan: String,

            @SerializedName("holderName")
            val holderName: String? = null,

            @SerializedName("issuerName")
            val issuerName: String? = null,

            @SerializedName("expiryDate")
            val expiryDate: String
        ) : Serializable

        data class Device(
            @SerializedName("posEntryMode")
            val posEntryMode: String,

            @SerializedName("posConditionCode")
            val posConditionCode: String
        ) : Serializable

        data class PaymentData(
            @SerializedName("currency")
            val currency: String = "704",

            @SerializedName("transAmount")
            val transAmount: String
        ) : Serializable
    }
}