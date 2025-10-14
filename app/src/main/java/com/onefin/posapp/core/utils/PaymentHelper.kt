package com.onefin.posapp.core.utils

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import com.atg.pos.app.OneFinSDK
import com.atg.pos.onefin.OneFinMainActivity
import com.google.gson.Gson
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentHelper @Inject constructor(
    private val gson: Gson
) {
    
    companion object {
        private const val TAG = "PaymentHelper"
        const val REQUEST_CODE_PAYMENT = 1001
        
        // Payment types
        const val TYPE_CARD = "card"
        const val TYPE_QR = "qr"
        const val TYPE_MEMBER = "member"
        
        // Intent extras keys
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_REQUEST_DATA = "merchant_request_data"
        private const val EXTRA_PAYMENT_RESPONSE = "payment_response_data"
        private const val EXTRA_MEMBER_RESPONSE = "member_response_data"
    }
    
    private var isSDKInitialized = false
    
    /**
     * Initialize OneFin SDK
     * Call this in Application.onCreate()
     */
    fun initSDK(application: Application, onInitialized: (() -> Unit)? = null) {
        if (isSDKInitialized) {
            Log.w(TAG, "OneFin SDK already initialized")
            onInitialized?.invoke()
            return
        }
        
        try {
            OneFinSDK.initSdk(application) {
                isSDKInitialized = true
                Log.d(TAG, "OneFin SDK initialized successfully")
                onInitialized?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OneFin SDK", e)
        }
    }
    
    /**
     * Start card payment
     */
    fun startCardPayment(
        activity: Activity,
        amount: Long,
        orderId: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        val requestData = buildMap {
            put("type", TYPE_CARD)
            put("action", 0)
            put("amount", amount)
            put("orderId", orderId)
            putAll(additionalData)
        }
        
        startPayment(activity, requestData)
    }
    
    /**
     * Start QR payment
     */
    fun startQRPayment(
        activity: Activity,
        amount: Long,
        orderId: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        val requestData = buildMap {
            put("type", TYPE_QR)
            put("action", 0)
            put("amount", amount)
            put("orderId", orderId)
            putAll(additionalData)
        }
        
        startPayment(activity, requestData)
    }
    
    /**
     * Start member payment
     */
    fun startMemberPayment(
        activity: Activity,
        amount: Long,
        orderId: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        val requestData = buildMap {
            put("type", TYPE_MEMBER)
            put("action", 0)
            put("amount", amount)
            put("orderId", orderId)
            putAll(additionalData)
        }
        
        startPayment(activity, requestData)
    }
    
    /**
     * Start payment with custom request data
     */
    fun startPayment(
        activity: Activity,
        requestData: Map<String, Any>
    ) {
        if (!isSDKInitialized) {
            Log.e(TAG, "OneFin SDK not initialized. Call initSDK() first!")
            return
        }
        
        try {
            val jsonRequest = gson.toJson(requestData)
            Log.d(TAG, "Starting payment with data: $jsonRequest")
            
            val intent = Intent(activity, OneFinMainActivity::class.java).apply {
                putExtra(EXTRA_TYPE, requestData["type"] as? String ?: TYPE_CARD)
                putExtra(EXTRA_ACTION, requestData["action"] as? Int ?: 0)
                putExtra(EXTRA_REQUEST_DATA, jsonRequest)
            }
            
            activity.startActivityForResult(intent, REQUEST_CODE_PAYMENT)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting payment", e)
        }
    }
    
    /**
     * Parse payment result from onActivityResult
     */
    fun parsePaymentResult(resultCode: Int, data: Intent?): PaymentResult {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                val responseData = data?.getStringExtra(EXTRA_PAYMENT_RESPONSE)
                    ?: data?.getStringExtra(EXTRA_MEMBER_RESPONSE)
                
                if (responseData != null) {
                    parseSuccessResponse(responseData)
                } else {
                    PaymentResult.Error("No response data received")
                }
            }
            Activity.RESULT_CANCELED -> {
                val errorData = data?.getStringExtra(EXTRA_PAYMENT_RESPONSE)
                    ?: data?.getStringExtra(EXTRA_MEMBER_RESPONSE)
                
                if (errorData != null) {
                    PaymentResult.Cancelled(errorData)
                } else {
                    PaymentResult.Cancelled("User cancelled payment")
                }
            }
            else -> {
                PaymentResult.Error("Unknown result code: $resultCode")
            }
        }
    }
    
    /**
     * Parse success response
     */
    private fun parseSuccessResponse(responseData: String): PaymentResult {
        return try {
            val jsonObject = JSONObject(responseData)
            val prettyJson = jsonObject.toString(2)
            
            Log.d(TAG, "Payment success: $prettyJson")
            
            PaymentResult.Success(
                rawData = prettyJson,
                jsonObject = jsonObject
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            PaymentResult.Error("Parse error: ${e.message}")
        }
    }
    
    /**
     * Check if SDK is initialized
     */
    fun isInitialized(): Boolean = isSDKInitialized
}

/**
 * Payment result sealed class
 */
sealed class PaymentResult {
    /**
     * Payment successful
     */
    data class Success(
        val rawData: String,
        val jsonObject: JSONObject
    ) : PaymentResult() {
        
        fun getString(key: String): String? = jsonObject.optString(key)
        
        fun getInt(key: String): Int = jsonObject.optInt(key)
        
        fun getLong(key: String): Long = jsonObject.optLong(key)
        
        fun getBoolean(key: String): Boolean = jsonObject.optBoolean(key)
        
        override fun toString(): String = rawData
    }
    
    /**
     * Payment cancelled by user
     */
    data class Cancelled(val message: String) : PaymentResult()
    
    /**
     * Payment error
     */
    data class Error(val message: String) : PaymentResult()
}