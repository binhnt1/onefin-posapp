package com.onefin.posapp.core.utils

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.atg.pos.app.OneFinSDK
import com.atg.pos.onefin.OneFinMainActivity
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.data.MerchantRequestData
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentStatusCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentHelper @Inject constructor(
    private val gson: Gson
) {
    private var isSDKInitialized = false

    fun isInitialized(): Boolean = isSDKInitialized

    fun buildResultIntentSuccess(response: PaymentAppResponse): Intent {
        return Intent().apply {
            putExtra(ResultConstants.RESULT_TYPE, response.type)
            putExtra(ResultConstants.RESULT_ACTION, response.action)
            putExtra(
                ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                gson.toJson(response.paymentResponseData)
            )
        }
    }

    fun startPayment(activity: Activity, requestData: PaymentAppRequest) {
        if (!isSDKInitialized) return

        try {
            val jsonRequest = gson.toJson(requestData.merchantRequestData)
            val intent = Intent(activity, OneFinMainActivity::class.java).apply {
                putExtra("type", requestData.type)
                putExtra("action", requestData.action)
                putExtra("merchant_request_data", jsonRequest)
            }
            activity.startActivityForResult(intent, ResultConstants.REQUEST_CODE_PAYMENT)
        } catch (e: Exception) {
            e.printStackTrace()
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
                onInitialized?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun buildResultIntentError(request: PaymentAppRequest, errorMessage: String?): Intent {
        val response = createPaymentAppResponseError(request, errorMessage)
        return buildResultIntentError(response, errorMessage)
    }

    fun buildResultIntentError(response: PaymentAppResponse?, errorMessage: String?): Intent {
        return Intent().apply {
            putExtra(ResultConstants.RESULT_ERROR, errorMessage)
            putExtra(ResultConstants.RESULT_TYPE, response?.type)
            putExtra(ResultConstants.RESULT_ACTION, response?.action)
            putExtra(
                ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                gson.toJson(response?.paymentResponseData)
            )
        }
    }

    fun createPaymentAppRequest(account: Account, request: PaymentAppRequest): PaymentAppRequest {
        val amount = request.merchantRequestData?.amount ?: 0
        val referenceId = request.merchantRequestData?.referenceId ?: UtilHelper.getCurrentTimeStamp()
        val billNumber = request.merchantRequestData?.billNumber ?: UtilHelper.generateRandomBillNumber()
        val additionalData = request.merchantRequestData?.additionalData ?: gson.toJson(mapOf(
            "driver_phone" to "1055",
            "trip_distance" to "5km",
            "driver_name" to "Mai Linh",
            "agency_phone" to "02838298888",
            "agency_name" to "Tập đoàn Mai Linh",
            "agency_add" to "19 Đ. 39, Phường An Khánh, Tp. Thủ Đức, Tp. Hồ Chí Minh",
            "src_address" to "19 Đ. 39, Phường An Khánh, Tp. Thủ Đức, Tp. Hồ Chí Minh",
            "dst_address" to "Saigon Pearl, 92 Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh, Tp. Hồ Chí Minh"
        ))

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

    fun buildResultIntentSuccess(request: PaymentAppRequest, transactionId: String?, transactionTime: String?): Intent {
        val response = createPaymentAppResponseSuccess(request, transactionId, transactionTime)
        return Intent().apply {
            putExtra(ResultConstants.RESULT_TYPE, response.type)
            putExtra(ResultConstants.RESULT_ACTION, response.action)
            putExtra(
                ResultConstants.RESULT_PAYMENT_RESPONSE_DATA,
                gson.toJson(response.paymentResponseData)
            )
        }
    }

    private fun createPaymentAppResponseError(request: PaymentAppRequest, message: String? = null): PaymentAppResponse {
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

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?, onSuccess: (String) -> Unit, onError: (String, String) -> Unit) {
        if (requestCode != ResultConstants.REQUEST_CODE_PAYMENT) return
        val returnedData = data?.getStringExtra("payment_response_data")
            ?: data?.getStringExtra("member_response_data")
            ?: "NO_DATA"

        try {
            if (resultCode == Activity.RESULT_OK) {
                onSuccess(returnedData)
            } else {
                onError("PAYMENT_CANCELED", returnedData)
            }
        } catch (e: Exception) {
            onError("PROCESSING_ERROR", "Error parsing response: ${e.message}")
        }
    }

    private fun createPaymentAppResponseSuccess(request: PaymentAppRequest, transactionId: String?, transactionTime: String?): PaymentAppResponse {
        val responseData = PaymentResponseData(
            refNo = transactionId,
            transactionId = transactionId,
            transactionTime = transactionTime,
            status = PaymentStatusCode.SUCCESS,
            description = "Thanh toán thành công",
            tip = request.merchantRequestData?.tip,
            tid = request.merchantRequestData?.tid,
            mid = request.merchantRequestData?.mid,
            ccy = request.merchantRequestData?.ccy,
            amount = request.merchantRequestData?.amount,
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