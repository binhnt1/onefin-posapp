package com.onefin.posapp.core.config

import android.annotation.SuppressLint


object AppConfig {
    
    enum class Environment {
        DEV, STG, PROD
    }
    
    private val CURRENT_ENV = Environment.PROD
    
    val baseUrl: String
        get() = when (CURRENT_ENV) {
            Environment.DEV -> "https://localhost:44344"
            Environment.PROD -> "https://pos-gateway.onefin.vn"
            Environment.STG -> "https://sit-pos-gateway.onefin.vn"
        }

    val baseRabbitUrl: String
        @SuppressLint("AuthLeak")
        get() = when (CURRENT_ENV) {
            Environment.DEV -> "amqp://sit-posapp:ARGcn5nnmkTE@14.241.228.156:5672/posapp"
            Environment.STG -> "amqp://sit-posapp:ARGcn5nnmkTE@14.241.228.156:5672/posapp"
            Environment.PROD -> "amqp://posapp-device:Pfkjs24hf4fFHgKJFdsa@119.82.132.172:5672/posapp"
        }
    
    val enableLogging: Boolean
        get() = CURRENT_ENV != Environment.PROD
}