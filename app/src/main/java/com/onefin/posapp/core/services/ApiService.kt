package com.onefin.posapp.core.services

import com.onefin.posapp.core.models.ResultApi
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.core.config.ApiConstants
import com.onefin.posapp.core.config.AppConfig
import com.onefin.posapp.core.config.StorageKeys
import com.onefin.posapp.core.models.ApiResponseModel
import com.onefin.posapp.core.models.ResultType
import com.onefin.posapp.core.utils.EncryptHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.onefin.posapp.core.models.ApiException
import com.onefin.posapp.core.models.BusinessException

@Singleton
class ApiService @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {
    private val baseUrl: String
        get() = AppConfig.baseUrl

    // OkHttpClient với logging (nếu enable)
    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // Thêm logging interceptor nếu enable
        if (AppConfig.enableLogging) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        builder.build()
    }

    suspend fun post(endpoint: String, body: Map<String, Any>): Any? = withContext(Dispatchers.IO) {
        try {
            // 1. Build headers
            val headers = buildHeaders()

            // 2. Encrypt and sign body
            val encryptedBody = encryptAndSignBody(body)

            // 3. Create request
            val requestBody = encryptedBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl$endpoint")
                .post(requestBody)
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            // 4. Execute
            val response = client.newCall(request).execute()

            // 5. Handle response
            if (!response.isSuccessful) {
                throw ApiException("HTTP Error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw ApiException("Empty response")
            handleSuccessfulResponse(responseBody)

        } catch (e: Exception) {
            when (e) {
                is BusinessException -> throw e
                is ApiException -> throw e
                else -> throw ApiException(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun get(endpoint: String, queryParams: Map<String, Any>? = null): Any? = withContext(Dispatchers.IO) {
        try {
            // 1. Build headers
            val headers = buildHeaders()

            // 2. Build URL with query params
            val urlBuilder = StringBuilder("$baseUrl$endpoint")
            queryParams?.let { params ->
                if (params.isNotEmpty()) {
                    urlBuilder.append("?")
                    params.entries.forEachIndexed { index, entry ->
                        if (index > 0) urlBuilder.append("&")
                        urlBuilder.append("${entry.key}=${entry.value}")
                    }
                }
            }

            // 3. Create request
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            // 4. Execute
            val response = client.newCall(request).execute()

            // 5. Handle response
            if (!response.isSuccessful) {
                throw ApiException("HTTP Error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw ApiException("Empty response")
            handleSuccessfulResponse(responseBody)

        } catch (e: Exception) {
            when (e) {
                is BusinessException -> throw e
                is ApiException -> throw e
                else -> throw ApiException(e.message ?: "Unknown error")
            }
        }
    }

    private fun buildHeaders(): Map<String, String> {
        val serial = sharedPreferences.getString(StorageKeys.SERIAL, null)
            ?: throw ApiException("Device Serial not found")

        val token = sharedPreferences.getString(StorageKeys.TOKEN, null)

        // Build X-Header data
        val headerData = mapOf(
            "Serial" to serial,
            "RequestId" to EncryptHelper.generateRandomString(16),
            "RequestTime" to System.currentTimeMillis(),
            "LatLng" to "10.762622,106.660172"
        )

        // Encrypt X-Header
        val encryptedHeader = EncryptHelper.encrypt(
            gson.toJson(headerData),
            sharedPreferences
        )

        val contentType = ApiConstants.CONTENT_TYPE
        val headers = mutableMapOf(
            "X-Padding" to "true",
            "X-Header-Serial" to serial,
            "X-Header" to encryptedHeader,
            "Content-Type" to contentType
        )

        token?.let {
            headers["Authorization"] = "Bearer $it"
        }

        return headers
    }

    private fun encryptAndSignBody(data: Map<String, Any>): String {
        val jsonData = gson.toJson(data)

        // Encrypt
        val encryptedData = EncryptHelper.encrypt(jsonData, sharedPreferences)

        // Sign
        val signature = EncryptHelper.sign(encryptedData, sharedPreferences)

        // Return as JSON string
        return gson.toJson(mapOf(
            "Data" to encryptedData,
            "Signature" to signature
        ))
    }

    private fun handleSuccessfulResponse(responseBody: String): Any? {
        // 1. Parse ApiResponseModel
        val apiResponse = gson.fromJson(responseBody, ApiResponseModel::class.java)

        // 2. Verify signature
        val isValid = EncryptHelper.verifySign(apiResponse.data, apiResponse.signature)
        if (!isValid) {
            throw ApiException("Invalid server signature")
        }

        // 3. Decrypt
        val decryptedData = EncryptHelper.decrypt(apiResponse.data, sharedPreferences)

        // 4. Parse ResultApi
        val resultApi = gson.fromJson<ResultApi<Any>>(
            decryptedData,
            object : TypeToken<ResultApi<Any>>() {}.type
        )

        // 5. Handle result type
        return when (resultApi.type) {
            ResultType.SUCCESS -> resultApi.data
            ResultType.ERROR -> throw BusinessException(resultApi.description)
            ResultType.EXCEPTION -> throw ApiException(resultApi.description)
        }
    }
}

// Extension functions
inline fun <reified T> ApiService.postForType(endpoint: String, body: Map<String, Any>): T? {
    val result = kotlinx.coroutines.runBlocking {
        post(endpoint, body)
    }
    return if (result != null) {
        Gson().fromJson(Gson().toJson(result), T::class.java)
    } else null
}

inline fun <reified T> ApiService.getForType(endpoint: String, queryParams: Map<String, Any>? = null): T? {
    val result = kotlinx.coroutines.runBlocking {
        get(endpoint, queryParams)
    }
    return if (result != null) {
        Gson().fromJson(Gson().toJson(result), T::class.java)
    } else null
}