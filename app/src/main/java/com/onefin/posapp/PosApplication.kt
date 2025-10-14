package com.onefin.posapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PosApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Khởi tạo các service cần thiết ở đây nếu có
    }
}