package com.onefin.posapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driver_info")
data class DriverInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "serial")
    val serial: String,

    @ColumnInfo(name = "tid")
    val tid: String,

    @ColumnInfo(name = "mid")
    val mid: String,

    @ColumnInfo(name = "driver_number")
    val driverNumber: String,

    @ColumnInfo(name = "employee_code")
    val employeeCode: String,

    @ColumnInfo(name = "employee_name")
    val employeeName: String? = null,

    @ColumnInfo(name = "acq")
    val acq: String? = null,

    @ColumnInfo(name = "mer_code")
    val mercode: String? = null,

    @ColumnInfo(name = "ter_code")
    val tercode: String? = null,

    @ColumnInfo(name = "currency")
    val currency: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
