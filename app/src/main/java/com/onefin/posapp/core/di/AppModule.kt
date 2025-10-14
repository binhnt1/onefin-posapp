package com.onefin.posapp.core.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provide SharedPreferences
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(
            "onefin_prefs",
            Context.MODE_PRIVATE
        )
    }

    /**
     * Provide Gson
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * Provide Application Context
     */
    @Provides
    @Singleton
    fun provideContext(
        @ApplicationContext context: Context
    ): Context {
        return context
    }

    /**
     * Provide ValidationHelper
     */
    @Provides
    @Singleton
    fun provideValidationHelper(
        @ApplicationContext context: Context
    ): com.onefin.posapp.core.utils.ValidationHelper {
        return com.onefin.posapp.core.utils.ValidationHelper(context)
    }

    /**
     * Provide LocaleHelper
     */
    @Provides
    @Singleton
    fun provideLocaleHelper(
        sharedPreferences: SharedPreferences
    ): com.onefin.posapp.core.utils.LocaleHelper {
        return com.onefin.posapp.core.utils.LocaleHelper(sharedPreferences)
    }

    /**
     * Provide DeviceHelper
     */
    @Provides
    @Singleton
    fun provideDeviceHelper(
        @ApplicationContext context: Context
    ): com.onefin.posapp.core.utils.DeviceHelper {
        return com.onefin.posapp.core.utils.DeviceHelper(context)
    }
}