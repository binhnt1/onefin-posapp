package com.onefin.posapp.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import com.onefin.posapp.core.models.data.DeviceInfo
import com.onefin.posapp.core.models.data.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getDeviceSerial(): String {
        return getSunmiSerial() ?: getAndroidId()
    }

    @SuppressLint("PrivateApi")
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    private fun getSunmiSerial(): String? {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)

            val serial = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    getMethod.invoke(systemPropertiesClass, "ro.sunmi.serial") as? String
                }

                else -> {
                    try {
                        Build.getSerial()
                    } catch (e: SecurityException) {
                        getMethod.invoke(systemPropertiesClass, "ro.sunmi.serial") as? String
                    }
                }
            }

            if (serial.isNullOrEmpty()) null else serial

        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            if (androidId.isNullOrEmpty() || androidId == "9774d56d682e549c") {
                generateUUID()
            } else {
                androidId
            }
        } catch (e: Exception) {
            generateUUID()
        }
    }

    private fun generateUUID(): String {
        return UUID.randomUUID().toString()
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    fun getDeviceBrand(): String {
        return Build.BRAND
    }

    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getSdkVersion(): Int {
        return Build.VERSION.SDK_INT
    }

    fun isSunmiDevice(): Boolean {
        return Build.MANUFACTURER.equals("SUNMI", ignoreCase = true) ||
                Build.BRAND.equals("SUNMI", ignoreCase = true)
    }

    fun getDeviceType(): DeviceType {
        val model = getDeviceModel().lowercase()
        return when {
            model.contains("p2") -> DeviceType.SUNMI_P2
            model.contains("p3") -> DeviceType.SUNMI_P3
            else -> DeviceType.ANDROID_PHONE
        }
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            isSunmi = isSunmiDevice(),
            serial = getDeviceSerial(),
            manufacturer = Build.MANUFACTURER,
            sdkVersion = Build.VERSION.SDK_INT,
            androidVersion = Build.VERSION.RELEASE,
        )
    }
}