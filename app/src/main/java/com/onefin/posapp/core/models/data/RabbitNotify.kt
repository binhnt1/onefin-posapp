package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class RabbitNotify(
    @SerializedName("Type") val type: Int,
    @SerializedName("Title") val title: String,
    @SerializedName("Content") val content: String,
    @SerializedName("DateTime") val dateTime: String,
    @SerializedName("JsonObject") val jsonObject: String?
): Serializable