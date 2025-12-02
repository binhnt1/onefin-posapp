package com.onefin.posapp

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
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

    private var isInitialize = false

    @Volatile
    private var isPrinterServiceBound = false
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val printerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sunmiPrinterService = SunmiPrinterService.Stub.asInterface(service)
            Log.d("PosApplication", "Sunmi Printer service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sunmiPrinterService = null
            Log.d("PosApplication", "Sunmi Printer service disconnected")
        }
    }

    private var isPaymentSDKLogged = false

    override fun onCreate() {
        super.onCreate()

        if (!isMainProcess()) {
            Log.d("PosApp", "➡️ Skip initialization: not main process")
            return
        }

        Log.d("PosApp", "🚀 Initializing PosApplication in main process")
        registerActivityLifecycleCallbacks(activityTracker)

        val criticalPathDuration = System.currentTimeMillis() - System.currentTimeMillis()
        Log.d("Performance", "🚀 Critical path: ${criticalPathDuration}ms")

        try {
            val startTime = System.currentTimeMillis()
            paymentHelper.initSDK(this@PosApplication)
            val duration = System.currentTimeMillis() - startTime
            if (!isPaymentSDKLogged) {
                isPaymentSDKLogged = true
                Log.d("Performance", "✅ Payment SDK: ${duration}ms")
            }
        } catch (e: Exception) {
            Log.e("PosApp", "❌ Payment SDK failed", e)
        }

        initializeBackgroundServices()
    }

    @SuppressLint("ServiceCast")
    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val process = manager.runningAppProcesses.firstOrNull { it.pid == pid }
        return process?.processName == packageName
    }

    private fun bindSerial() {
        val deviceSerial = deviceHelper.getDeviceSerial()
        if (!deviceSerial.isEmpty())
            storageService.saveSerial(deviceSerial)
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
                Log.d("PosApp", "✅ Printer service unbound")
            } catch (_: Exception) {}
        }
    }

    private fun bindPrinterService() {
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"

            var bound = bindService(intent, printerServiceConnection, BIND_AUTO_CREATE)
            Log.d("PosApplication", "Binding Woyou printer service: $bound")

            if (!bound) {
                val intent2 = Intent()
                intent2.setPackage("com.sunmi.peripheral")
                intent2.action = "com.sunmi.peripheral.printer.SunmiPrinterService"
                bound = bindService(intent2, printerServiceConnection, BIND_AUTO_CREATE)
                Log.d("PosApplication", "Binding Sunmi printer service: $bound")
            }

            if (bound) {
                isPrinterServiceBound = true
            } else {
                Log.e("PosApplication", "Failed to bind printer service")
            }
        } catch (e: Exception) {
            Log.e("PosApplication", "Error binding printer service", e)
        }
    }

    private val initLock = Any()
    private fun initializeBackgroundServices() {
        synchronized(initLock) {
            if (isInitialize) return
            isInitialize = true
        }

        bindPrinterService()
        if (storageService.isLoggedIn()) {
            rabbitMQManager.startAfterLogin()
        }
    }
}
