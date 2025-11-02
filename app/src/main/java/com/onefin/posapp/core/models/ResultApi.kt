package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName

enum class ResultType(val value: Int) {
    SUCCESS(1),
    ERROR(2),
    EXCEPTION(3);

    companion object {
        fun fromValue(value: Int): ResultType {
            return ResultType.entries.find { it.value == value } ?: EXCEPTION
        }
    }
}

data class ResultApi<T>(
    @SerializedName("Object")
    val data: T? = null,

    @SerializedName("Type")
    val typeValue: Int = 3,

    @SerializedName("Total")
    val total: Long? = null,

    @SerializedName("ObjectExtra")
    val objectExtra: Any? = null,

    @SerializedName("Description")
    val description: String = "Unknown error occurred."
) {
    val type: ResultType
        get() = ResultType.fromValue(typeValue)

    /**
     * Check if result is success
     */
    fun isSuccess(): Boolean = type == ResultType.SUCCESS

    /**
     * Check if result is error
     */
    fun isError(): Boolean = type == ResultType.ERROR

    /**
     * Check if result is exception
     */
    fun isException(): Boolean = type == ResultType.EXCEPTION

    /**
     * Get data or throw exception
     */
    fun getOrThrow(): T {
        return when (type) {
            ResultType.EXCEPTION -> throw ApiException(description)
            ResultType.ERROR -> throw BusinessException(description)
            ResultType.SUCCESS -> data ?: throw ApiException("Data is null")
        }
    }
}

public class ApiException(message: String) : Exception(message) {
    override fun toString(): String = message ?: "API Exception"
}

public class BusinessException(message: String) : Exception(message) {
    override fun toString(): String = message ?: "Business Exception"
}
