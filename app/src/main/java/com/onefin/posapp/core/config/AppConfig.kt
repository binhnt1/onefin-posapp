package com.onefin.posapp.core.config


object AppConfig {
    
    enum class Environment {
        DEV, STG, PROD
    }
    
    private val CURRENT_ENV = Environment.STG
    
    val baseUrl: String
        get() = when (CURRENT_ENV) {
            Environment.DEV -> "https://localhost:44344"
            Environment.PROD -> "https://pos-gateway.onefin.vn"
            Environment.STG -> "https://sit-pos-gateway.onefin.vn"
        }
    
    val enableLogging: Boolean
        get() = CURRENT_ENV != Environment.PROD
}