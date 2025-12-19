package com.onefin.posapp.core.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QRNotificationManager @Inject constructor() {

    var notificationState by mutableStateOf<NotificationState?>(null)
        private set

    fun showNotification(message: String) {
        // Update state với message mới - dialog sẽ tự động re-render
        notificationState = NotificationState(
            message = message,
            timestamp = System.currentTimeMillis()
        )
    }

    fun dismissNotification() {
        notificationState = null
    }

    data class NotificationState(
        val message: String,
        val timestamp: Long // Để force re-render khi message giống nhau
    )
}
