package com.onefin.posapp.ui.transaction

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.utils.PaymentHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import java.io.Serializable

@AndroidEntryPoint
class TransparentPaymentActivity : AppCompatActivity() {
    @Inject
    lateinit var paymentHelper: PaymentHelper

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
            paymentHelper.startPayment(this, requestData)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        paymentHelper.handleActivityResult(
            requestCode, resultCode, data,
            onSuccess = { response ->
                Timber.tag("PAYMENT").d(response)
            },
            onError = { code, message ->
                val errorMessage = getString(R.string.error_generic_format, message)
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )
        finish()
    }
}