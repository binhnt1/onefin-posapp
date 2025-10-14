package com.onefin.posapp

import android.app.Application
import com.onefin.posapp.core.managers.RabbitMQManager
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application() {
    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var storageService: StorageService

    @Inject
    lateinit var rabbitMQManager: RabbitMQManager

    override fun onCreate() {
        super.onCreate()

        // Nếu user đã login, khởi động RabbitMQ
        if (storageService.isLoggedIn()) {
            rabbitMQManager.startAfterLogin()
        }

        // Gọi initSDK
        paymentHelper.initSDK(this)
    }
}