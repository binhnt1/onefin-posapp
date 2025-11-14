package com.onefin.posapp.core.providers

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.google.gson.Gson
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.SaleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri

class TransactionProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.onefin.memberapp.transaction.provider"
        const val PATH_TRANS_ID = "transID"
        const val COLUMN_MEMBER_RESPONSE_DATA = "member_response_data"

        val CONTENT_URI: Uri = "content://$AUTHORITY/$PATH_TRANS_ID".toUri()

        private const val TRANS_ID = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "$PATH_TRANS_ID/*", TRANS_ID)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TransactionProviderEntryPoint {
        fun apiService(): ApiService
        fun storageService(): StorageService
        fun gson(): Gson
    }

    private val apiService: ApiService by lazy {
        val appContext = context?.applicationContext ?: throw IllegalStateException()
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            TransactionProviderEntryPoint::class.java
        )
        hiltEntryPoint.apiService()
    }

    private val storageService: StorageService by lazy {
        val appContext = context?.applicationContext ?: throw IllegalStateException()
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            TransactionProviderEntryPoint::class.java
        )
        hiltEntryPoint.storageService()
    }

    private val gson: Gson by lazy {
        val appContext = context?.applicationContext ?: throw IllegalStateException()
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            TransactionProviderEntryPoint::class.java
        )
        hiltEntryPoint.gson()
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            TRANS_ID -> {
                val billNumber = uri.lastPathSegment
                if (billNumber == null) {
                    return createErrorCursor("99", "Lỗi chưa xác định")
                }
                queryTransaction(billNumber)
            }
            else -> {
                createErrorCursor("99", "Lỗi chưa xác định")
            }
        }
    }

    override fun update(
        p0: Uri,
        p1: ContentValues?,
        p2: String?,
        p3: Array<out String?>?
    ): Int {
        TODO("Not yet implemented")
    }

    override fun delete(
        p0: Uri,
        p1: String?,
        p2: Array<out String?>?
    ): Int {
        TODO("Not yet implemented")
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            TRANS_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_TRANS_ID"
            else -> null
        }
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        TODO("Not yet implemented")
    }

    private fun queryTransaction(billNumber: String): Cursor {
        try {
            val account = storageService.getAccount()
            if (account == null) {
                return createErrorCursor("99", "Chưa đăng nhập, vui lòng đăng nhập")
            }

            val saleResult = runBlocking {
                try {
                    val endpoint = "/api/card/transaction/$billNumber"
                    val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                    if (resultApi.isSuccess()) {
                        val transactionJson = gson.toJson(resultApi.data)
                        gson.fromJson(transactionJson, SaleResultData::class.java)
                    } else {
                        val error = SaleResultData(
                            status = SaleResultData.Status(
                                code = "12",
                                message = "12 - Giao dịch không hợp lệ"
                            )
                        )
                        error
                    }
                } catch (e: Exception) {
                    val error = SaleResultData(
                        status = SaleResultData.Status(
                            code = "96",
                            message = "96 - Lỗi hệ thống"
                        )
                    )
                    error
                }
            }

            if (saleResult != null) {
                try {
                    val responseMap = mutableMapOf<String, Any?>()

                    val requestDataMap = when (val requestData = saleResult.requestData) {
                        is String -> {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(requestData, Map::class.java) as? Map<String, Any?>
                        }
                        else -> {
                            val json = gson.toJson(requestData)
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(json, Map::class.java) as? Map<String, Any?>
                        }
                    } ?: emptyMap()

                    responseMap["type"] = requestDataMap["type"] ?: "card"
                    responseMap["action"] = requestDataMap["action"] ?: 1

                    val paymentResponseData = mutableMapOf<String, Any?>()
                    paymentResponseData["status"] = saleResult.status?.code ?: "99"
                    paymentResponseData["description"] = saleResult.status?.message
                    paymentResponseData["ref_no"] = saleResult.data?.refNo
                    paymentResponseData["transaction_id"] = saleResult.header?.transId
                    paymentResponseData["transaction_time"] = saleResult.header?.transmitsDateTime
                    paymentResponseData["amount"] = saleResult.data?.totalAmount?.toLongOrNull() ?: 0L
                    paymentResponseData["ccy"] = saleResult.data?.currency ?: "704"

                    val merchantRequestData = requestDataMap["merchant_request_data"]
                    val merchantDataMap = when (merchantRequestData) {
                        is String -> {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(merchantRequestData, Map::class.java) as? Map<String, Any?>
                        }
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            merchantRequestData as? Map<String, Any?>
                        }
                        else -> null
                    }

                    merchantDataMap?.let { merchantData ->
                        paymentResponseData["bill_number"] = merchantData["bill_number"]
                        paymentResponseData["reference_id"] = merchantData["reference_id"]
                        paymentResponseData["additional_data"] = merchantData["additional_data"]
                        paymentResponseData["tip"] = merchantData["tip"]
                        paymentResponseData["tid"] = merchantData["tid"]
                        paymentResponseData["mid"] = merchantData["mid"]
                    }

                    responseMap["payment_response_data"] = paymentResponseData

                    val responseJson = gson.toJson(responseMap)
                    val cursor = MatrixCursor(arrayOf(COLUMN_MEMBER_RESPONSE_DATA))
                    cursor.addRow(arrayOf(responseJson))
                    return cursor
                } catch (e: Exception) {
                    return createErrorCursor("99", "Lỗi chưa xác định")
                }
            } else {
                return createErrorCursor("99", "Lỗi chưa xác định")
            }
        } catch (e: Exception) {
            return createErrorCursor("99", "Lỗi chưa xác định")
        }
    }

    private fun createErrorCursor(errorCode: String, errorMessage: String): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_MEMBER_RESPONSE_DATA))
        val errorResponse = mapOf(
            "type" to "card",
            "action" to 1,
            "payment_response_data" to mapOf(
                "status" to errorCode,
                "description" to errorMessage
            )
        )
        val responseJson = gson.toJson(errorResponse)
        cursor.addRow(arrayOf(responseJson))
        return cursor
    }
}
