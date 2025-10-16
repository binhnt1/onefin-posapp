package com.onefin.posapp.ui.base

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContext {
    private var context: Context? = null

    /**
     * Khởi tạo AppContext trong Application class
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * Lấy Application Context
     */
    fun get(): Context {
        return context ?: throw IllegalStateException(
            "AppContext chưa được khởi tạo. Hãy gọi AppContext.init() trong Application class."
        )
    }

    /**
     * Lấy string từ resources
     */
    fun getString(resId: Int): String {
        return get().getString(resId)
    }

    /**
     * Lấy string từ resources với format arguments
     */
    fun getString(resId: Int, vararg formatArgs: Any): String {
        return get().getString(resId, *formatArgs)
    }
}