package com.onefin.posapp.core.utils

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateTimeFormatter {
    fun formatDate(dateTimeString: String?): String {
        if (dateTimeString.isNullOrEmpty()) {
            return ""
        }
        return try {
            val zonedDateTime = ZonedDateTime.parse(dateTimeString)
            val localDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            localDateTime.format(formatter)
        } catch (e: Exception) {
            dateTimeString
        }
    }

    fun formatDateTime(dateTimeString: String?): String {
        if (dateTimeString.isNullOrEmpty()) {
            return ""
        }
        return try {
            val zonedDateTime = ZonedDateTime.parse(dateTimeString)
            val localDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            localDateTime.format(formatter)
        } catch (e: Exception) {
            dateTimeString
        }
    }
}