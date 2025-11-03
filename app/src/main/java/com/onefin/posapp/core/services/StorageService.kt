package com.onefin.posapp.core.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.onefin.posapp.core.config.StorageKeys
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.onefin.posapp.core.config.MifareConstants
import com.onefin.posapp.core.config.PrefsName
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.data.NfcConfigResponse
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PkeyConfigResponse

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
            remove(MifareConstants.NFC_CONFIG)
            remove(MifareConstants.PKEY_CONFIG)
            remove(MifareConstants.NFC_CONFIG_TIMESTAMP)
            remove(MifareConstants.PKEY_CONFIG_TIMESTAMP)
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

    // ==================== NfcConfig ====================
    fun clearNfcConfig() {
        sharedPreferences.edit().apply {
            remove(MifareConstants.NFC_CONFIG)
            remove(MifareConstants.NFC_CONFIG_TIMESTAMP)
            apply()
        }
    }
    fun getNfcConfig(): NfcConfigResponse? {
        val configJson = sharedPreferences.getString(MifareConstants.NFC_CONFIG, null)
        val timestamp = sharedPreferences.getLong(MifareConstants.NFC_CONFIG_TIMESTAMP, 0L)

        if (configJson == null || timestamp == 0L) {
            return null
        }

        // Check expiry
        val currentTime = System.currentTimeMillis()
        if (currentTime - timestamp > MifareConstants.NFC_CONFIG_CACHE_DURATION) {
            // Expired, clear and return null
            sharedPreferences.edit {
                remove(MifareConstants.NFC_CONFIG)
                remove(MifareConstants.NFC_CONFIG_TIMESTAMP)
            }
            return null
        }

        // Parse and return
        return try {
            gson.fromJson(configJson, NfcConfigResponse::class.java)
        } catch (e: Exception) {
            // Invalid, clear and return null
            sharedPreferences.edit {
                remove(MifareConstants.NFC_CONFIG)
                remove(MifareConstants.NFC_CONFIG_TIMESTAMP)
            }
            null
        }
    }
    fun saveNfcConfig(config: NfcConfigResponse) {
        val configJson = gson.toJson(config)
        val timestamp = System.currentTimeMillis()

        sharedPreferences.edit {
            putString(MifareConstants.NFC_CONFIG, configJson)
            putLong(MifareConstants.NFC_CONFIG_TIMESTAMP, timestamp)
        }
    }

    // ==================== PkeyConfig ====================
    fun clearPkeyConfig() {
        sharedPreferences.edit().apply {
            remove(MifareConstants.PKEY_CONFIG)
            remove(MifareConstants.PKEY_CONFIG_TIMESTAMP)
            apply()
        }
    }
    fun getPkeyConfig(): PkeyConfigResponse? {
        val configJson = sharedPreferences.getString(MifareConstants.PKEY_CONFIG, null)
        val timestamp = sharedPreferences.getLong(MifareConstants.PKEY_CONFIG_TIMESTAMP, 0L)

        if (configJson == null || timestamp == 0L) {
            return null
        }

        // Check expiry
        val currentTime = System.currentTimeMillis()
        if (currentTime - timestamp > MifareConstants.PKEY_CONFIG_CACHE_DURATION) {
            // Expired, clear and return null
            sharedPreferences.edit {
                remove(MifareConstants.PKEY_CONFIG)
                remove(MifareConstants.PKEY_CONFIG_TIMESTAMP)
            }
            return null
        }

        // Parse and return
        return try {
            gson.fromJson(configJson, PkeyConfigResponse::class.java)
        } catch (e: Exception) {
            // Invalid, clear and return null
            sharedPreferences.edit {
                remove(MifareConstants.PKEY_CONFIG)
                remove(MifareConstants.PKEY_CONFIG_TIMESTAMP)
            }
            null
        }
    }
    fun savePkeyConfig(config: PkeyConfigResponse) {
        val configJson = gson.toJson(config)
        val timestamp = System.currentTimeMillis()

        sharedPreferences.edit {
            putString(MifareConstants.PKEY_CONFIG, configJson)
            putLong(MifareConstants.PKEY_CONFIG_TIMESTAMP, timestamp)
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