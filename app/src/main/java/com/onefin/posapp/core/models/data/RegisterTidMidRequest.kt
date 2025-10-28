package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class RegisterTidMidRequest(
    @SerializedName("tid")
    val tid: String,

    @SerializedName("mid")
    val mid: String,

    @SerializedName("tseri")
    val tseri: String, // Terminal serial

    @SerializedName("driverNumber")
    val driverNumber: String,

    @SerializedName("employeeCode")
    val employeeCode: String
)

data class RegisterTidMidResponse(
    @SerializedName("tid")
    val tid: String, // "70012336"

    @SerializedName("mid")
    val mid: String, // "700154112000336"

    @SerializedName("acq")
    val acq: String, // "100016"

    @SerializedName("tseri")
    val tseri: String, // "P313E46510098"

    @SerializedName("mercode")
    val mercode: String, // "100000000000062"

    @SerializedName("tercode")
    val tercode: String, // "10001390"

    @SerializedName("currency")
    val currency: String, // "704"

    @SerializedName("merchantid")
    val merchantid: String, // "86B03188" = driverNumber

    @SerializedName("terminalid")
    val terminalid: String // "046265" = employeeCode
)