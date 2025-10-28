package com.onefin.posapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onefin.posapp.core.database.entity.DriverInfoEntity

@Dao
interface DriverInfoDao {
    @Query("""
        SELECT COUNT(*) FROM driver_info 
        WHERE tid = :tid 
        AND mid = :mid 
        AND serial = :serial 
        AND driver_number = :driverNumber 
        AND employee_code = :employeeCode
    """)
    suspend fun exists(
        tid: String,
        mid: String,
        serial: String,
        driverNumber: String,
        employeeCode: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(driverInfo: DriverInfoEntity): Long

    @Query("SELECT * FROM driver_info WHERE serial = :serial ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestBySerial(serial: String): DriverInfoEntity?
}
