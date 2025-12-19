package com.onefin.posapp.ui.modals

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme

class QRSuccessNotificationActivity : BaseActivity() {

    companion object {
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"

        fun show(context: Context, message: String) {
            val intent = Intent(context, QRSuccessNotificationActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Thanh toán thành công"

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
}
