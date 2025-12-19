package com.onefin.posapp

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.onefin.posapp.core.managers.ActivityTracker
import com.onefin.posapp.core.managers.RabbitMQManager
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.DeviceHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.sunmi.peripheral.printer.SunmiPrinterService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application() {

    @Inject
    lateinit var deviceHelper: DeviceHelper

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var storageService: StorageService

    @Inject
    lateinit var rabbitMQManager: RabbitMQManager

    @Inject
    lateinit var activityTracker: ActivityTracker

    var sunmiPrinterService: SunmiPrinterService? = null
        private set

    @Volatile
    private var isPrinterServiceBound = false
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val printerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sunmiPrinterService = SunmiPrinterService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sunmiPrinterService = null
        }
    }

    private var isPaymentSDKLogged = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityTracker)
        initializeBackgroundServices()

        // Init Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        runBlocking {
            applicationScope.cancel()
            applicationScope.coroutineContext.job.join()
        }

        if (isPrinterServiceBound) {
            try {
                unbindService(printerServiceConnection)
                isPrinterServiceBound = false
            } catch (_: Exception) {}
        }
    }

    private fun bindPrinterService() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            var bound = bindService(intent, printerServiceConnection, BIND_AUTO_CREATE)

            if (!bound) {
                val intent2 = Intent()
                intent2.setPackage("com.sunmi.peripheral")
                intent2.action = "com.sunmi.peripheral.printer.SunmiPrinterService"
                bound = bindService(intent2, printerServiceConnection, BIND_AUTO_CREATE)
            }

            if (bound) {
                isPrinterServiceBound = true
            }
        } catch (_: Exception) {
        }
    }

    private fun initializeBackgroundServices() {
        // print
        bindPrinterService()

        // rabbit
        if (storageService.isLoggedIn()) {
            rabbitMQManager.startAfterLogin()
        }

        // sdk
        val sdkType = BuildConfig.SDK_TYPE
        if (sdkType == "onefin") {
            try {
                paymentHelper.initSDK(this@PosApplication)
                if (!isPaymentSDKLogged) {
                    isPaymentSDKLogged = true
                }
            } catch (_: Exception) {
            }
        }
    }
}
