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

    fun formatCustomDateTime(dateTimeString: String?): String {
        if (dateTimeString.isNullOrEmpty()) return ""

        return try {
            val formatted = com.onefin.posapp.core.utils.DateTimeFormatter.formatDateTime(dateTimeString)
            // Convert from "dd/MM/yyyy HH:mm:ss" to "HH:mm, dd/MM/yyyy"
            val parts = formatted.split(" ")
            if (parts.size >= 2) {
                val time = parts[1].substring(0, 5) // HH:mm only
                "${time}, ${parts[0]}"
            } else {
                formatted
            }
        } catch (e: Exception) {
            dateTimeString
        }
    }
}