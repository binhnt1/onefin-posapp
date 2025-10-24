package com.onefin.posapp

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.nfc.NfcAdapter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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

    // THÊM PRINTER SERVICE
    var sunmiPrinterService: SunmiPrinterService? = null
        private set

    @Volatile
    private var isPrinterServiceBound = false
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        val appStartTime = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        registerActivityLifecycleCallbacks(activityTracker)

        val criticalPathDuration = System.currentTimeMillis() - appStartTime
        Timber.tag("Performance").d("🚀 Critical path: ${criticalPathDuration}ms")

        // Background init
        initializeBackgroundServices()
    }

    private fun bindSerial() {
        val deviceSerial = deviceHelper.getDeviceSerial()
        if (!deviceSerial.isEmpty())
            storageService.saveSerial(deviceSerial)
    }

    override fun onTerminate() {
        super.onTerminate()

        // Cleanup
        runBlocking {
            applicationScope.cancel()
            applicationScope.coroutineContext.job.join()
        }

        if (isPrinterServiceBound) {
            try {
                unbindService(printerServiceConnection)
                isPrinterServiceBound = false
                Timber.tag("PosApp").d("✅ Printer service unbound")
            } catch (e: Exception) {
                Timber.tag("PosApp").e(e, "❌ Unbind error")
            }
        }
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
    private fun initializeBackgroundServices() {
        applicationScope.launch {
            // Wave 1: 500ms
            delay(500)
            launch(Dispatchers.IO) {
                bindPrinterService()
            }

            // Wave 2: 1500ms - PAYMENT SDK
            delay(1000)
            launch(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    paymentHelper.initSDK(this@PosApplication)
                    val duration = System.currentTimeMillis() - startTime
                    Timber.tag("Performance").d("✅ Payment SDK: ${duration}ms")
                } catch (e: Exception) {
                    Timber.tag("PosApp").e(e, "❌ Payment SDK failed")
                }
            }

            // Wave 3: 2500ms - RabbitMQ
            delay(1000)
            launch(Dispatchers.IO) {
                if (storageService.isLoggedIn()) {
                    rabbitMQManager.startAfterLogin()
                }
            }
        }
    }
}