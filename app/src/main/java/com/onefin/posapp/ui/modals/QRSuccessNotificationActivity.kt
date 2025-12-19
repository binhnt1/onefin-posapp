package com.onefin.posapp.ui.modals

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme

class QRSuccessNotificationActivity : BaseActivity() {

    companion object {
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        private var currentInstance: QRSuccessNotificationActivity? = null

        fun show(context: Context, message: String) {
            // Đóng modal cũ nếu đang mở
            currentInstance?.finish()
            currentInstance = null

            val intent = Intent(context, QRSuccessNotificationActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track instance hiện tại
        currentInstance = this

        message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Thanh toán thành công"

        setContent {
            PosAppTheme {
                SuccessDialog(
                    message = message,
                    countdownSeconds = 10,
                    onCountdownComplete = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update message nếu có intent mới (do launchMode singleTask)
        setIntent(intent)
        message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Thanh toán thành công"

        // Recreate để reset countdown
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear instance khi destroy
        if (currentInstance == this) {
            currentInstance = null
        }
    }
}
