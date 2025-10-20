package com.onefin.posapp.ui.base

import android.content.Context
import androidx.activity.ComponentActivity
import com.onefin.posapp.core.managers.RabbitMQManager
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.LocaleHelper
import jakarta.inject.Inject

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
}