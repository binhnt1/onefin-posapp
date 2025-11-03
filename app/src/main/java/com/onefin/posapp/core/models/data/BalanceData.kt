package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class BalanceData(
    @SerializedName("Status")
    val status: Int,

    @SerializedName("Amount")
    val amount: Long
)