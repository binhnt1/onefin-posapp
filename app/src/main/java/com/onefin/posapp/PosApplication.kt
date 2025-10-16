package com.onefin.posapp

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.onefin.posapp.core.managers.ActivityTracker
import com.onefin.posapp.core.managers.RabbitMQManager
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.base.AppContext
import com.sunmi.peripheral.printer.SunmiPrinterService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application() {
    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var storageService: StorageService

    @Inject
    lateinit var rabbitMQManager: RabbitMQManager

    @Inject
    lateinit var activityTracker: ActivityTracker

    // THÊM PRINTER SERVICE
    var sunmiPrinterService: SunmiPrinterService? = null
        private set

    private val printerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sunmiPrinterService = SunmiPrinterService.Stub.asInterface(service)
            Timber.tag("PosApplication").d("Sunmi Printer service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sunmiPrinterService = null
            Timber.tag("PosApplication").d("Sunmi Printer service disconnected")
        }
    }

    override fun onCreate() {
        super.onCreate()

        AppContext.init(this)

        // Bind printer service
        bindPrinterService()

        // Nếu user đã login, khởi động RabbitMQ
        if (storageService.isLoggedIn()) {
            rabbitMQManager.startAfterLogin()
        }

        // Gọi initSDK
        paymentHelper.initSDK(this)

        // Đăng ký ActivityTracker để theo dõi Activity hiện tại
        registerActivityLifecycleCallbacks(activityTracker)
    }

    private fun bindPrinterService() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"

            var bound = bindService(intent, printerServiceConnection, BIND_AUTO_CREATE)
            Timber.tag("PosApplication").d("Binding Woyou printer service: $bound")

            if (!bound) {
                val intent2 = Intent()
                intent2.setPackage("com.sunmi.peripheral")
                intent2.action = "com.sunmi.peripheral.printer.SunmiPrinterService"
                bound = bindService(intent2, printerServiceConnection, BIND_AUTO_CREATE)
                Timber.tag("PosApplication").d("Binding Sunmi printer service: $bound")
            }

            if (!bound) {
                Timber.tag("PosApplication").e("Failed to bind printer service")
            }
        } catch (e: Exception) {
            Timber.tag("PosApplication").e(e, "Error binding printer service")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            unbindService(printerServiceConnection)
            Timber.tag("PosApplication").d("Printer service unbound")
        } catch (e: Exception) {
            Timber.tag("PosApplication").e(e, "Error unbinding printer service")
        }
    }
}