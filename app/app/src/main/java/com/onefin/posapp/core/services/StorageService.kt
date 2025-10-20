package com.onefin.posapp.core.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.onefin.posapp.core.config.StorageKeys
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.onefin.posapp.core.config.PrefsName
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.data.PaymentAppRequest

@Singleton
class StorageService @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val context: Context,
    private val gson: Gson
) {

    // ==================== TOKEN ====================
    fun getToken(): String? {
        return sharedPreferences.getString(StorageKeys.TOKEN, null)
    }
    fun saveToken(token: String) {
        sharedPreferences.edit { putString(StorageKeys.TOKEN, token) }
    }

    // ==================== SERIAL ====================
    fun getSerial(): String? {
        return sharedPreferences.getString(StorageKeys.SERIAL, null)
    }
    fun saveSerial(serial: String) {
        sharedPreferences.edit { putString(StorageKeys.SERIAL, serial) }
    }

    // ==================== ACCOUNT ====================
    fun getAccount(): Account? {
        val accountJson = sharedPreferences.getString(StorageKeys.ACCOUNT, null)
        return accountJson?.let {
            try {
                gson.fromJson(it, Account::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    fun saveAccount(account: Account) {
        val accountJson = gson.toJson(account)
        sharedPreferences.edit { putString(StorageKeys.ACCOUNT, accountJson) }
    }

    // ==================== CLEAR ====================
    fun clearAll() {
        sharedPreferences.edit().apply {
            remove(StorageKeys.TOKEN)
            remove(StorageKeys.SERIAL)
            remove(StorageKeys.ACCOUNT)
            apply()
        }
    }

    fun isExternalPaymentFlow(): Boolean {
        return getExternalPaymentContext() != null
    }
    fun clearExternalPaymentContext() {
        prefs.edit {
            remove(PrefsName.PREF_EXTERNAL_PAYMENT_CONTEXT)
                .remove(PrefsName.PREF_PENDING_PAYMENT_REQUEST)
        }
    }
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PrefsName.APP_PREFS_EX, Context.MODE_PRIVATE)
    }
    fun setExternalPaymentContext(contextId: String?) {
        if (contextId != null) {
            prefs.edit { putString(PrefsName.PREF_EXTERNAL_PAYMENT_CONTEXT, contextId) }
        } else {
            prefs.edit { remove(PrefsName.PREF_EXTERNAL_PAYMENT_CONTEXT) }
        }
    }
    fun getExternalPaymentContext(): String? {
        return prefs.getString(PrefsName.PREF_EXTERNAL_PAYMENT_CONTEXT, null)
    }
    fun setPendingPaymentRequest(request: PaymentAppRequest?) {
        if (request != null) {
            val json = Gson().toJson(request)
            prefs.edit { putString(PrefsName.PREF_PENDING_PAYMENT_REQUEST, json) }
        } else {
            prefs.edit { remove(PrefsName.PREF_PENDING_PAYMENT_REQUEST) }
        }
    }
    fun getPendingPaymentRequest(): PaymentAppRequest? {
        val json = prefs.getString(PrefsName.PREF_PENDING_PAYMENT_REQUEST, null) ?: return null
        return try {
            Gson().fromJson(json, PaymentAppRequest::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== UTILITY ====================
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }
    fun hasSerial(): Boolean {
        return getSerial() != null
    }
}