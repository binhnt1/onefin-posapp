package com.onefin.posapp.core.database.repositories

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.core.database.entity.DriverInfoEntity
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.RegisterTidMidRequest
import com.onefin.posapp.core.models.data.RegisterTidMidResponse
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.DriverInfoService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverInfoRepository @Inject constructor(
    private val gson: Gson,
    private val apiService: ApiService,
    private val driverInfoService: DriverInfoService,
) {
    suspend fun ensureDriverInfoRegistered(
        tid: String?,
        mid: String?,
        serial: String?,
        driverNumber: String?,
        employeeCode: String?,
        employeeName: String?
    ): ResultApi<DriverInfoEntity> {
        try {
            if (tid.isNullOrEmpty()) return errorResult("TID không được để trống")
            if (mid.isNullOrEmpty()) return errorResult("MID không được để trống")
            if (serial.isNullOrEmpty()) return errorResult("Serial không được để trống")
            if (driverNumber.isNullOrEmpty()) return errorResult("Driver number không được để trống")
            if (employeeCode.isNullOrEmpty()) return errorResult("Employee code không được để trống")
            val exists = driverInfoService.exists(tid, mid, serial, driverNumber, employeeCode)

            if (exists) {
                val existing = driverInfoService.getLatestBySerial(serial)
                if (existing != null) {
                    return successResult(existing, "Driver info đã tồn tại")
                }
            }
            return registerAndSave(tid, mid, serial, driverNumber, employeeCode, employeeName)
        } catch (e: Exception) {
            return errorResult(e.toString())
        }
    }

    private suspend fun registerAndSave(
        tid: String,
        mid: String,
        serial: String,
        driverNumber: String,
        employeeCode: String,
        employeeName: String?
    ): ResultApi<DriverInfoEntity> {
        try {
            val requestBody = RegisterTidMidRequest(
                tid = tid,
                mid = mid,
                tseri = serial,
                driverNumber = driverNumber,
                employeeCode = employeeCode
            )

            val mapBody: Map<String, Any> = gson.fromJson(
                gson.toJson(requestBody),
                object : TypeToken<Map<String, Any>>() {}.type
            )

            val resultApi = apiService.post("/api/card/registerTidMid", mapBody) as ResultApi<*>
            if (!resultApi.isSuccess()) {
                return errorResult("Đăng ký thất bại: ${resultApi.description}")
            }

            val response = try {
                if (resultApi.data == null) {
                    return errorResult("API response data is null")
                }
                gson.fromJson(
                    gson.toJson(resultApi.data),
                    RegisterTidMidResponse::class.java
                )
            } catch (e: Exception) {
                return errorResult("Lỗi parse response: ${e.toString()}")
            }

            if (response.tid.isEmpty()) return errorResult("TID trong response không hợp lệ")
            if (response.mid.isEmpty()) return errorResult("MID trong response không hợp lệ")
            val entity = DriverInfoEntity(
                serial = serial,
                tid = response.tid,
                mid = response.mid,
                acq = response.acq,
                mercode = response.mercode,
                tercode = response.tercode,
                currency = response.currency,
                employeeName = employeeName,
                driverNumber = response.merchantid,
                employeeCode = response.terminalid,
                createdAt = System.currentTimeMillis()
            )

            val insertedId = try {
                driverInfoService.insert(entity)
            } catch (e: Exception) {
                return errorResult("Lỗi lưu vào DB: ${e.toString()}")
            }

            return successResult(entity.copy(id = insertedId), "Đăng ký driver thành công")

        } catch (e: Exception) {
            return errorResult(e.toString())
        }
    }

    private fun errorResult(description: String): ResultApi<DriverInfoEntity> {
        return ResultApi(
            data = null,
            typeValue = 2,
            description = description
        )
    }

    private fun successResult(data: DriverInfoEntity, description: String): ResultApi<DriverInfoEntity> {
        return ResultApi(
            data = data,
            typeValue = 1,
            description = description
        )
    }
}