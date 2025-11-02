package com.onefin.posapp.core.models.data

import com.google.gson.annotations.SerializedName

data class RabbitNotify(
    @SerializedName("Type")
    val type: Int,

    @SerializedName("Title")
    val title: String,

    @SerializedName("Content")
    val content: String,

    @SerializedName("DateTime")
    val dateTime: String,

    @SerializedName("JsonObject")
    val jsonObject: String?
)

enum class RabbitNotifyType(val value: Int) {
    LOGOUT(1),
    LOCKED(2),
    UNLOCKED(3),
    REQUEST_PAYMENT(4),
    REQUEST_INVOICE(5),
    QR_SUCCESS(10),
    PRINT_INVOICE(11),
    UPDATE_SOFTWARE(12),
    PUSH_IMAGE(13),
    PUSH_VIDEO(14),
    SETTLEMENT(15),
    CHANGE_TID_MID(16),
    LOCK_USER(20),
    UNKNOWN(-1);

    companion object {
        fun fromValue(value: Int): RabbitNotifyType {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}