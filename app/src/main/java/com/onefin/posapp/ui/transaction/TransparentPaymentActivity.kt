package com.onefin.posapp.ui.transaction

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.io.Serializable
import androidx.activity.OnBackPressedCallback

@AndroidEntryPoint
class TransparentPaymentActivity : AppCompatActivity() {
    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var storageService: StorageService

    private var sdkActivityFinished = false
    private var pendingRequest: PaymentAppRequest? = null

    private val lifecycleCallback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (activity::class.simpleName == this@TransparentPaymentActivity::class.simpleName) {
                return
            }
            // Nếu là SDK activity đã finish
            if (activity !== this@TransparentPaymentActivity && !sdkActivityFinished) {
                sdkActivityFinished = true
                Handler(Looper.getMainLooper()).postDelayed({
                    if (sdkActivityFinished) {
                        cancelAction("SDK activity đã bị hủy")
                    }
                }, 500)
            }
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawObject: Serializable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("REQUEST_DATA", Serializable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("REQUEST_DATA")
        }

        @Suppress("UNCHECKED_CAST")
        val requestData = rawObject as? PaymentAppRequest
        if (requestData != null) {
            pendingRequest = requestData

            // ✅ Đăng ký lifecycle callback
            application.registerActivityLifecycleCallbacks(lifecycleCallback)
            paymentHelper.startPayment(this, requestData)
        } else {
            finish()
        }

        // Back button trên TransparentPaymentActivity
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelAction()
                }
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        sdkActivityFinished = false
        paymentHelper.handleActivityResult(
            requestCode, resultCode, data,
            onSuccess = { response ->
                // Timber.tag("PAYMENT").d(response)
            },
            onError = { code, message ->
                val errorMessage = getString(R.string.error_generic_format, message)
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )
        finish()
    }

    override fun onDestroy() {
        application.unregisterActivityLifecycleCallbacks(lifecycleCallback)
        super.onDestroy()
    }

    private fun cancelAction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val request = pendingRequest ?: storageService.getPendingPaymentRequest()
            if (request != null) {
                val resultIntent = paymentHelper.buildResultIntentError(request, errorMessage)
                setResult(RESULT_OK, resultIntent)
                storageService.clearExternalPaymentContext()
            }
        }
        finish()
    }
}