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
import com.onefin.posapp.core.models.data.PaymentRequest
import com.onefin.posapp.core.models.data.PaymentRequestType
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

    fun buildPaymentAppRequest(
        account: Account,
        paymentRequest: PaymentRequest
    ): PaymentAppRequest {

        val type = when (paymentRequest.type) {
            PaymentRequestType.QR -> "qr"
            PaymentRequestType.CARD -> "card"
            PaymentRequestType.MEMBER -> "member"
            else -> "qr"
        }

        val amount = paymentRequest.amount
        val action = paymentRequest.action.value
        val referenceId = paymentRequest.referenceId ?: UtilHelper.getCurrentTimeStamp()
        val billNumber = paymentRequest.billNumber ?: UtilHelper.generateRandomBillNumber()
        val additionalData = paymentRequest.additionalData ?: mapOf(
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
            tip = 0,
            ccy = "704",
            amount = amount,
            billNumber = billNumber,
            referenceId = referenceId,
            tid = account.terminal.tid,
            mid = account.terminal.mid,
            additionalData = additionalData,
            isEnterPin = paymentRequest.isEnterPin ?: true,
            message = paymentRequest.message ?: "Thanh toán từ RabbitMQ",
        )

        return PaymentAppRequest(
            type = type,
            action = action,
            merchantRequestData = merchantRequest
        )
    }

}