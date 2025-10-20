package com.onefin.posapp.core.models.data

data class DeviceInfo(
    val serial: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val isSunmi: Boolean
) {
    override fun toString(): String {
        return """
            Device Info:
            - Serial: $serial
            - Manufacturer: $manufacturer
            - Brand: $brand
            - Model: $model
            - Device: $device
            - Android: $androidVersion (API $sdkVersion)
            - Is Sunmi: $isSunmi
        """.trimIndent()
    }
}

enum class DeviceType {
    SUNMI_P2,
    SUNMI_P3,
    OTHER;
}
