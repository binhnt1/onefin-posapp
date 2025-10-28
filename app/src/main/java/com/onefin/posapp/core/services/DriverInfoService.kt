package com.onefin.posapp.core.services

import com.onefin.posapp.core.database.dao.DriverInfoDao
import com.onefin.posapp.core.database.entity.DriverInfoEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverInfoService @Inject constructor(
    private val driverInfoDao: DriverInfoDao
) {
    suspend fun exists(
        tid: String,
        mid: String,
        serial: String,
        driverNumber: String,
        employeeCode: String
    ): Boolean {
        return driverInfoDao.exists(tid, mid, serial, driverNumber, employeeCode) > 0
    }

    suspend fun insert(driverInfo: DriverInfoEntity): Long {
        return driverInfoDao.insert(driverInfo)
    }

    suspend fun getLatestBySerial(serial: String): DriverInfoEntity? {
        return driverInfoDao.getLatestBySerial(serial)
    }
}