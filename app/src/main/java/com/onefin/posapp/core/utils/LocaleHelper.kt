package com.onefin.posapp.core.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.onefin.posapp.core.config.LanguageConstants
import com.onefin.posapp.core.config.PrefsName
import com.onefin.posapp.core.config.StorageKeys
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class LocaleHelper @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    
    fun setLocale(context: Context, language: String): Context {
        saveLanguage(language)
        return updateResources(context, language)
    }

    fun getLanguage(): String {
        return sharedPreferences.getString(
            StorageKeys.LANGUAGE, 
            LanguageConstants.DEFAULT_LANGUAGE
        ) ?: LanguageConstants.DEFAULT_LANGUAGE
    }

    private fun saveLanguage(language: String) {
        sharedPreferences.edit {
            putString(StorageKeys.LANGUAGE, language)
        }
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale.Builder().setLanguage(language).build()
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        return context.createConfigurationContext(configuration)
    }
    
    companion object {
        // Helper methods để dùng trong Activity.attachBaseContext()
        fun getLanguageStatic(context: Context): String {
            val prefs = context.getSharedPreferences(PrefsName.APP_PREFS, Context.MODE_PRIVATE)
            return prefs.getString(
                StorageKeys.LANGUAGE,
                LanguageConstants.DEFAULT_LANGUAGE
            ) ?: LanguageConstants.DEFAULT_LANGUAGE
        }
        
        fun setLocaleStatic(context: Context, language: String): Context {
            val locale = Locale.Builder().setLanguage(language).build()
            Locale.setDefault(locale)

            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)

            return context.createConfigurationContext(configuration)
        }
    }
}