package com.onefin.posapp.core.providers

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.google.gson.Gson
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import androidx.core.net.toUri
import com.onefin.posapp.core.models.data.SaleResultData

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
        Timber.tag("TransactionProvider").d("Provider created")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Timber.tag("TransactionProvider").d("Query called with URI: $uri")

        return when (uriMatcher.match(uri)) {
            TRANS_ID -> {
                val billNumber = uri.lastPathSegment
                if (billNumber == null) {
                    Timber.tag("TransactionProvider").e("Bill number is null")
                    return createErrorCursor("Bill number is required")
                }
                queryTransaction(billNumber)
            }
            else -> {
                Timber.tag("TransactionProvider").e("Unknown URI: $uri")
                createErrorCursor("Invalid URI")
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
        val cursor = MatrixCursor(arrayOf(COLUMN_MEMBER_RESPONSE_DATA))

        try {
            Timber.tag("TransactionProvider").d("Querying transaction: $billNumber")

            // Check if user is logged in
            val account = storageService.getAccount()
            if (account == null) {
                Timber.tag("TransactionProvider").e("User not logged in")
                cursor.addRow(arrayOf(createErrorJson("User not logged in")))
                return cursor
            }

            // Query transaction from API
            val transaction = runBlocking {
                try {
                    val billNumberNew = "PS0000005068"
                    val endpoint = "/api/card/transaction/$billNumberNew"
                    val resultApi = apiService.get(endpoint, emptyMap()) as ResultApi<*>
                    val transactionJson = gson.toJson(resultApi.data)
                    gson.fromJson(transactionJson, SaleResultData::class.java)
                } catch (e: Exception) {
                    Timber.tag("TransactionProvider").e(e, "Failed to query transaction")
                    null
                }
            }

            if (transaction != null) {
                // Return transaction as JSON
                val responseJson = gson.toJson(transaction)
                cursor.addRow(arrayOf(responseJson))
                Timber.tag("TransactionProvider").d("Transaction found: $billNumber")
            } else {
                // Transaction not found
                cursor.addRow(arrayOf(createErrorJson("Transaction not found")))
                Timber.tag("TransactionProvider").w("Transaction not found: $billNumber")
            }

        } catch (e: Exception) {
            Timber.tag("TransactionProvider").e(e, "Error querying transaction")
            cursor.addRow(arrayOf(createErrorJson("Error: ${e.message}")))
        }

        return cursor
    }
    private fun createErrorJson(errorMessage: String): String {
        return gson.toJson(mapOf(
            "status" to "error",
            "message" to errorMessage
        ))
    }
    private fun createErrorCursor(errorMessage: String): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_MEMBER_RESPONSE_DATA))
        cursor.addRow(arrayOf(createErrorJson(errorMessage)))
        return cursor
    }
}