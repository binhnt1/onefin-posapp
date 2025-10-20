package com.onefin.posapp.core.utils

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.atg.pos.app.OneFinSDK
import com.atg.pos.onefin.OneFinMainActivity
import com.google.gson.Gson
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentHelper @Inject constructor(
    private val gson: Gson
) {

    companion object {
        private const val TAG = "PaymentHelper"
        const val REQUEST_CODE_PAYMENT = 1001
    }

    private var isSDKInitialized = false

    fun isInitialized(): Boolean = isSDKInitialized

    fun startPayment(activity: Activity, requestData: PaymentAppRequest) {
        if (!isSDKInitialized) {
            return
        }

        try {
            val jsonRequest = gson.toJson(requestData.merchantRequestData)
            val intent = Intent(activity, OneFinMainActivity::class.java).apply {
                putExtra("type", requestData.type)
                putExtra("action", requestData.action)
                putExtra("merchant_request_data", jsonRequest)
            }
            activity.startActivityForResult(intent, REQUEST_CODE_PAYMENT)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting payment")
        }
    }

    fun initSDK(application: Application, onInitialized: (() -> Unit)? = null) {
        if (isSDKInitialized) {
            onInitialized?.invoke()
            return
        }

        try {
            OneFinSDK.initSdk(application) {
                isSDKInitialized = true
                Timber.tag(TAG).d("OneFin SDK initialized successfully")
                onInitialized?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error initializing OneFin SDK")
        }
    }

    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onSuccess: (String) -> Unit,
        onError: (String, String) -> Unit
    ) {
        if (requestCode != REQUEST_CODE_PAYMENT) return

        // Log toàn bộ data trong Intent
        logCompleteIntentStructure(data, resultCode)

        var returnedData = data?.getStringExtra("payment_response_data")
            ?: data?.getStringExtra("member_response_data")
            ?: "NO_DATA"

        try {
            if (returnedData != "NO_DATA") {
                val jsonObject = JSONObject(returnedData)
                returnedData = jsonObject.toString(2)
            }

            if (resultCode == Activity.RESULT_OK) {
                Timber.tag(TAG).d("Payment success: $returnedData")
                onSuccess(returnedData)
            } else {
                Timber.tag(TAG).w("Payment canceled: $returnedData")
                onError("PAYMENT_CANCELED", returnedData)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing response: ${e.message}")
            onError("PROCESSING_ERROR", "Error parsing response: ${e.message}")
        }
    }

    private fun logCompleteIntentStructure(data: Intent?, resultCode: Int) {
        Timber.tag(TAG).d("=== COMPLETE INTENT STRUCTURE ===")
        Timber.tag(TAG).d("Result Code: $resultCode")

        if (data == null) {
            Timber.tag(TAG).d("Intent is NULL")
            return
        }

        // 1. Basic Intent info
        Timber.tag(TAG).d("\n--- BASIC INFO ---")
        Timber.tag(TAG).d("Intent toString: $data")
        Timber.tag(TAG).d("Action: ${data.action}")
        Timber.tag(TAG).d("Data URI: ${data.data}")
        Timber.tag(TAG).d("Type: ${data.type}")
        Timber.tag(TAG).d("Scheme: ${data.scheme}")
        Timber.tag(TAG).d("Package: ${data.`package`}")
        Timber.tag(TAG).d("Component: ${data.component}")
        Timber.tag(TAG).d("Flags: ${data.flags}")
        Timber.tag(TAG).d("Categories: ${data.categories}")

        // 2. Extras (Bundle)
        Timber.tag(TAG).d("\n--- EXTRAS (BUNDLE) ---")
        val extras = data.extras
        if (extras == null) {
            Timber.tag(TAG).d("No extras")
        } else {
            Timber.tag(TAG).d("Bundle size: ${extras.size()}")
            Timber.tag(TAG).d("Bundle isEmpty: ${extras.isEmpty}")
            Timber.tag(TAG).d("Bundle keySet: ${extras.keySet()}")

            for (key in extras.keySet()) {
                @Suppress("DEPRECATION") val value = extras.get(key)
                val valueType = value?.javaClass?.name ?: "null"
                Timber.tag(TAG).d("  [$key]")
                Timber.tag(TAG).d("    Type: $valueType")
                Timber.tag(TAG).d("    Value: $value")

                // Nếu là String, thử parse JSON để xem cấu trúc
                if (value is String && value.startsWith("{") || value is String && value.startsWith("[")) {
                    try {
                        val json = if (value.startsWith("{")) JSONObject(value) else JSONArray(value)
                        Timber.tag(TAG).d("    JSON formatted:\n${json}")
                    } catch (e: Exception) {
                        // Không phải JSON hợp lệ
                    }
                }
            }
        }

        // 3. ClipData (nếu có)
        Timber.tag(TAG).d("\n--- CLIP DATA ---")
        val clipData = data.clipData
        if (clipData == null) {
            Timber.tag(TAG).d("No ClipData")
        } else {
            Timber.tag(TAG).d("ClipData itemCount: ${clipData.itemCount}")
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                Timber.tag(TAG).d("  Item $i:")
                Timber.tag(TAG).d("    Text: ${item.text}")
                Timber.tag(TAG).d("    URI: ${item.uri}")
                Timber.tag(TAG).d("    Intent: ${item.intent}")
            }
        }

        Timber.tag(TAG).d("\n=== END COMPLETE STRUCTURE ===")
    }

    fun createPaymentAppRequest(account: Account, request: PaymentAppRequest): PaymentAppRequest {
        val amount = request.merchantRequestData?.amount ?: 0
        val referenceId = request.merchantRequestData?.referenceId ?: UtilHelper.getCurrentTimeStamp()
        val billNumber =  request.merchantRequestData?.billNumber ?: UtilHelper.generateRandomBillNumber()
        val additionalData = request.merchantRequestData?.additionalData ?: mapOf(
            "driver_phone" to "1055",
            "trip_distance" to "5km",
            "driver_name" to "Mai Linh",
            "agency_phone" to "02838298888",
            "agency_name" to "Tập đoàn Mai Linh",
            "agency_add" to "19 Đ. 39, Phường An Khánh, Tp. Thủ Đức, Tp. Hồ Chí Minh",
            "src_address" to "19 Đ. 39, Phường An Khánh, Tp. Thủ Đức, Tp. Hồ Chí Minh",
            "dst_address" to "Saigon Pearl, 92 Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh, Tp. Hồ Chí Minh"
        )

        val merchantRequest = MerchantRequestData(
            billNumber = billNumber,
            referenceId = referenceId,
            tid = account.terminal.tid,
            mid = account.terminal.mid,
            additionalData = additionalData,
            tip = request.merchantRequestData?.tip ?: 0,
            ccy = request.merchantRequestData?.ccy ?: "704",
            amount = request.merchantRequestData?.amount ?: 0,
            isEnterPin = request.merchantRequestData?.isEnterPin ?: false,
            message = request.merchantRequestData?.message ?: ("Thanh toán $amount VND"),
        )

        return PaymentAppRequest(
            type = request.type,
            action = request.action,
            merchantRequestData = merchantRequest
        )
    }

    fun createPaymentAppResponseCancel(request: PaymentAppRequest, message: String? = null): PaymentAppResponse {
        val responseData = PaymentResponseData(
            refNo = "",
            transactionId = "",
            transactionTime = "",
            status = PaymentStatusCode.CANCELLED,
            tip = request.merchantRequestData?.tip,
            tid = request.merchantRequestData?.tid,
            mid = request.merchantRequestData?.mid,
            ccy = request.merchantRequestData?.ccy,
            amount = request.merchantRequestData?.amount,
            description = message ?: "Hủy bỏ bởi người dùng",
            billNumber = request.merchantRequestData?.billNumber,
            referenceId = request.merchantRequestData?.referenceId,
            additionalData = request.merchantRequestData?.additionalData,
        )
        return PaymentAppResponse(
            type = request.type,
            action = request.action,
            paymentResponseData = responseData
        )
    }

    fun createPaymentAppResponseSuccess(request: PaymentAppRequest, transactionId: String?, transactionTime: String?, message: String? = null): PaymentAppResponse {
        val responseData = PaymentResponseData(
            refNo = transactionId,
            transactionId = transactionId,
            transactionTime = transactionTime,
            status = PaymentStatusCode.SUCCESS,
            tip = request.merchantRequestData?.tip,
            tid = request.merchantRequestData?.tid,
            mid = request.merchantRequestData?.mid,
            ccy = request.merchantRequestData?.ccy,
            amount = request.merchantRequestData?.amount,
            description = message ?: "Thanh toán thành công",
            billNumber = request.merchantRequestData?.billNumber,
            referenceId = request.merchantRequestData?.referenceId,
            additionalData = request.merchantRequestData?.additionalData,
        )
        return PaymentAppResponse(
            type = request.type,
            action = request.action,
            paymentResponseData = responseData
        )
    }
}