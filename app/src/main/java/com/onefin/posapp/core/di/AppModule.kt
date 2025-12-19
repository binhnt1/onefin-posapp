package com.onefin.posapp.core.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
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
        return GsonBuilder().disableHtmlEscaping().create()
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

    /**
     * Provide RabbitMQService
     */
    @Provides
    @Singleton
    fun provideRabbitMQService(
        storageService: StorageService
    ): com.onefin.posapp.core.services.RabbitMQService {
        return com.onefin.posapp.core.services.RabbitMQService(storageService)
    }

    @Provides
    @Singleton
    fun providePaymentHelper(
        gson: Gson
    ): com.onefin.posapp.core.utils.PaymentHelper {
        return com.onefin.posapp.core.utils.PaymentHelper(gson)
    }

    /**
     * Provide ActivityTracker
     */
    @Provides
    @Singleton
    fun provideActivityTracker(): com.onefin.posapp.core.managers.ActivityTracker {
        return com.onefin.posapp.core.managers.ActivityTracker()
    }

    /**
     * Provide TTSManager
     */
    @Provides
    @Singleton
    fun provideTTSManager(
        @ApplicationContext context: Context,
        apiService: javax.inject.Provider<ApiService>,
        gson: Gson
    ): com.onefin.posapp.core.managers.TTSManager {
        return com.onefin.posapp.core.managers.TTSManager(context, apiService, gson)
    }

    /**
     * Provide PrinterHelper
     */
    @Provides
    @Singleton
    fun providePrinterHelper(
        @ApplicationContext context: Context
    ): com.onefin.posapp.core.utils.PrinterHelper {
        return com.onefin.posapp.core.utils.PrinterHelper(context)
    }

    /**
     * Provide ReceiptPrinter
     */
    @Provides
    @Singleton
    fun provideReceiptPrinter(
        @ApplicationContext context: Context,
        printerHelper: com.onefin.posapp.core.utils.PrinterHelper
    ): com.onefin.posapp.core.utils.ReceiptPrinter {
        return com.onefin.posapp.core.utils.ReceiptPrinter(context, printerHelper)
    }

    /**
     * Provide CardProcessorManager
     */
    @Provides
    @Singleton
    fun provideCardProcessorManager(
        apiService: ApiService,
        storageService: StorageService,
        @ApplicationContext context: Context,
    ): com.onefin.posapp.core.managers.CardProcessorManager {
        return com.onefin.posapp.core.managers.CardProcessorManager(context, apiService, storageService)
    }

    /**
     * Provide NfcPhoneReaderManager
     */
    @Provides
    @Singleton
    fun provideNfcPhoneReaderManager(
        @ApplicationContext context: Context,
        storageService: StorageService
    ): com.onefin.posapp.core.managers.NfcPhoneReaderManager {
        return com.onefin.posapp.core.managers.NfcPhoneReaderManager(context, storageService)
    }
}