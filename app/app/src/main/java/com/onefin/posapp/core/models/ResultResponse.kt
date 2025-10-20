package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName

data class ApiResponseModel(
    @SerializedName("Data")
    val data: String = "",
    
    @SerializedName("Signature")
    val signature: String = ""
)