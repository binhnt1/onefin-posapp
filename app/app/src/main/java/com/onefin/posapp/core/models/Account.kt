package com.onefin.posapp.core.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * Token model
 */
data class Token(
    @SerializedName("AccessToken")
    val accessToken: String = "",
    
    @SerializedName("RefreshToken")
    val refreshToken: String = "",
    
    @SerializedName("Expires")
    val expiresString: String = ""
) {
    /**
     * Convert expires string to Date
     */
    val expires: Date
        get() {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                format.parse(expiresString) ?: Date()
            } catch (e: Exception) {
                Date()
            }
        }
    
    /**
     * Check if token is expired
     */
    fun isExpired(): Boolean {
        return expires.before(Date())
    }
    
    /**
     * Check if token is valid
     */
    fun isValid(): Boolean {
        return accessToken.isNotEmpty() && !isExpired()
    }
    
    companion object {
        /**
         * Create Token with Date
         */
        fun create(accessToken: String, refreshToken: String, expires: Date): Token {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            return Token(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresString = format.format(expires)
            )
        }
    }
}

/**
 * Account model
 */
data class Account(
    @SerializedName("Name")
    val name: String = "",
    
    @SerializedName("Email")
    val email: String = "",
    
    @SerializedName("Token")
    val token: Token = Token(),
    
    @SerializedName("Terminal")
    val terminal: Terminal = Terminal()
) {
    /**
     * Check if account is valid
     */
    fun isValid(): Boolean {
        return name.isNotEmpty() && token.isValid()
    }
    
    /**
     * Get access token
     */
    fun getAccessToken(): String {
        return token.accessToken
    }
    
    /**
     * Get refresh token
     */
    fun getRefreshToken(): String {
        return token.refreshToken
    }
}