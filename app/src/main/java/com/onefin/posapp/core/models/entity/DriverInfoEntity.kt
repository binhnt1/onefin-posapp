package com.onefin.posapp.core.models.entity

data class DriverInfoEntity(
    val tid: String,
    val mid: String,
    val serial: String,
    val acq: String? = null,
    val driverNumber: String,
    val employeeCode: String,
    val mercode: String? = null,
    val tercode: String? = null,
    val currency: String? = null,
    val employeeName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)