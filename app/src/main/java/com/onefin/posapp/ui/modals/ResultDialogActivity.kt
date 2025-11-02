package com.onefin.posapp.ui.modals

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme

class ResultDialogActivity : BaseActivity() {

    companion object {
        private const val EXTRA_IS_SUCCESS = "EXTRA_IS_SUCCESS"
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"

        fun showSuccess(context: Context, message: String) {
            val intent = Intent(context, ResultDialogActivity::class.java).apply {
                putExtra(EXTRA_IS_SUCCESS, true)
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun showError(context: Context, message: String) {
            val intent = Intent(context, ResultDialogActivity::class.java).apply {
                putExtra(EXTRA_IS_SUCCESS, false)
                putExtra(EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, false)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        setContent {
            PosAppTheme {
                if (isSuccess) {
                    AlertDialog(
                        content = message,
                        onDismiss = { finish() }
                    )
                } else {
                    ErrorDialog(message = message)
                    // Auto dismiss after 3 seconds for error
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 3000)
                }
            }
        }
    }
}