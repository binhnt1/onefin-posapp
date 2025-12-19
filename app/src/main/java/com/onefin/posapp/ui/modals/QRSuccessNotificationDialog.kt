package com.onefin.posapp.ui.modals

import androidx.compose.runtime.*
import com.onefin.posapp.core.managers.QRNotificationManager
import kotlinx.coroutines.delay

@Composable
fun QRSuccessNotificationDialog(
    qrNotificationManager: QRNotificationManager
) {
    val notificationState = qrNotificationManager.notificationState

    // Hiển thị dialog khi có state
    notificationState?.let { state ->
        // Key để force re-render khi có message mới
        key(state.timestamp) {
            SuccessDialog(
                message = state.message,
                countdownSeconds = 10,
                onCountdownComplete = {
                    qrNotificationManager.dismissNotification()
                }
            )
        }
    }
}
