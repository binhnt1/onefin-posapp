package com.onefin.posapp.core.services

import android.content.SharedPreferences
import com.google.gson.Gson
import com.onefin.posapp.core.config.StorageKeys
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.onefin.core.models.Account

@Singleton
class StorageService @Inject constructor(
    private val sharedPreferences: SharedPreferences,
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

    // ==================== UTILITY ====================
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }
    fun hasSerial(): Boolean {
        return getSerial() != null
    }
}