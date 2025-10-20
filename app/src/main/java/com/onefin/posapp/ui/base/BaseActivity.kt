package com.onefin.posapp.ui.base

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import com.onefin.posapp.core.managers.RabbitMQManager
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.LocaleHelper
import jakarta.inject.Inject
import java.io.Serializable

abstract class BaseActivity : ComponentActivity() {
    @Inject
    lateinit var localeHelper: LocaleHelper

    @Inject
    lateinit var storageService: StorageService
    @Inject
    lateinit var rabbitMQManager: RabbitMQManager

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.Companion.getLanguageStatic(newBase)
        super.attachBaseContext(LocaleHelper.Companion.setLocaleStatic(newBase, language))
    }

    protected fun getPaymentAppRequest(): PaymentAppRequest? {
        val rawObject: Serializable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("REQUEST_DATA", Serializable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("REQUEST_DATA")
        }

        @Suppress("UNCHECKED_CAST")
        return rawObject as? PaymentAppRequest
    }
}