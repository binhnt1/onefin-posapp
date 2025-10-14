package com.onefin.posapp.ui.base

import android.content.Context
import androidx.activity.ComponentActivity
import com.onefin.posapp.core.utils.LocaleHelper

/**
 * Base Activity để xử lý locale cho tất cả các màn hình
 */
abstract class BaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.Companion.getLanguageStatic(newBase)
        super.attachBaseContext(LocaleHelper.Companion.setLocaleStatic(newBase, language))
    }
}