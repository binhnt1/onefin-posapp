package com.onefin.posapp.ui.external

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.onefin.posapp.core.config.ResultConstants
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.MemberResultData
import com.onefin.posapp.core.models.data.PaymentAction
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentAppResponse
import com.onefin.posapp.core.models.data.PaymentResponseData
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.PaymentStatusCode
import com.onefin.posapp.core.models.data.VoidResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.PaymentHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.external.changepin.ChangePinStep1Screen
import com.onefin.posapp.ui.external.changepin.ChangePinStep2Screen
import com.onefin.posapp.ui.external.changepin.ChangePinStep3Screen
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

enum class ChangePinStep {
    STEP1_TAP_CARD_AND_OLD_PIN,  // Quẹt thẻ + Nhập PIN cũ
    STEP2_NEW_PIN,                // Nhập PIN mới
    STEP3_CONFIRM_NEW_PIN         // Xác nhận PIN mới
}

@AndroidEntryPoint
class ChangePinActivity : BaseActivity() {

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var cardProcessorManager: CardProcessorManager

    @Inject
    lateinit var nfcPhoneReaderManager: NfcPhoneReaderManager

    private var deviceType: DeviceType = DeviceType.ANDROID_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceType = detectDeviceType()
        val requestData = getPaymentAppRequest()
        if (requestData != null) {
            setContent {
                ChangePinFlow(
                    deviceType = deviceType,
                    apiService = apiService,
                    storageService = storageService,
                    cardProcessorManager = cardProcessorManager,
                    nfcPhoneReaderManager = nfcPhoneReaderManager,
                    onCancel = { errorMessage -> cancelAction(errorMessage) },
                    onSuccess = { memberResult -> returnSuccess(memberResult, requestData) },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.disableForegroundDispatch(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.enableForegroundDispatch(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
            DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                nfcPhoneReaderManager.handleNfcIntent(tag)
            }
        }
    }

    private fun detectDeviceType(): DeviceType {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()

            val isSunmi = manufacturer.contains("sunmi") ||
                    model.contains("p2") ||
                    model.contains("v2") ||
                    model.contains("p1")

            if (isSunmi) DeviceType.SUNMI_P3 else DeviceType.ANDROID_PHONE
        } catch (e: Exception) {
            DeviceType.ANDROID_PHONE
        }
    }

    private fun cancelAction(errorMessage: String? = null) {
        val isExternalFlow = storageService.isExternalPaymentFlow()
        if (isExternalFlow) {
            val pendingRequest = storageService.getPendingPaymentRequest()
            if (pendingRequest != null) {
                val resultIntent = paymentHelper.buildResultIntentError(pendingRequest, errorMessage)
                setResult(RESULT_OK, resultIntent)
                storageService.clearExternalPaymentContext()
                finish()
            } else {
                val currentRequest = getPaymentAppRequest()
                if (currentRequest != null) {
                    val resultIntent = paymentHelper.buildResultIntentError(currentRequest, errorMessage)
                    setResult(RESULT_OK, resultIntent)
                    storageService.clearExternalPaymentContext()
                    finish()
                }
            }
        }
        finish()
    }

    private fun returnSuccess(memberResult: MemberResultData, originalRequest: PaymentAppRequest) {
        val pendingRequest = storageService.getPendingPaymentRequest() ?: originalRequest
        val response = CardHelper.returnMemberResponse(memberResult, pendingRequest)
        val resultIntent = paymentHelper.buildResultIntentSuccess(response)
        setResult(RESULT_OK, resultIntent)
        storageService.clearExternalPaymentContext()
        finish()
    }
}

@Composable
fun ChangePinFlow(
    deviceType: DeviceType,
    apiService: ApiService,
    onCancel: (String?) -> Unit,
    onSuccess: (MemberResultData) -> Unit,
    cardProcessorManager: CardProcessorManager,
    nfcPhoneReaderManager: NfcPhoneReaderManager,
    storageService: com.onefin.posapp.core.services.StorageService,
) {
    var memberResult by remember { mutableStateOf(MemberResultData()) }
    var cardData by remember { mutableStateOf<PaymentResult.Success?>(null) }
    var currentStep by remember { mutableStateOf(ChangePinStep.STEP1_TAP_CARD_AND_OLD_PIN) }

    when (currentStep) {
        ChangePinStep.STEP1_TAP_CARD_AND_OLD_PIN -> {
            ChangePinStep1Screen(
                deviceType = deviceType,
                cardProcessorManager = cardProcessorManager,
                nfcPhoneReaderManager = nfcPhoneReaderManager,
                storageService = storageService,
                onCancel = { onCancel(null) },
                onTimeout = { onCancel("Hết thời gian") },
                onOldPinVerified = { result ->
                    cardData = result
                    currentStep = ChangePinStep.STEP2_NEW_PIN
                }
            )
        }

        ChangePinStep.STEP2_NEW_PIN -> {
            cardData?.let { data ->
                ChangePinStep2Screen(
                    cardData = data,
                    apiService = apiService,
                    onCancel = { onCancel(null) },
                    onTimeout = { onCancel("Hết thời gian") },
                    onSuccess = { resultData ->
                        memberResult = resultData
                        currentStep = ChangePinStep.STEP3_CONFIRM_NEW_PIN
                    }
                )
            }
        }

        ChangePinStep.STEP3_CONFIRM_NEW_PIN -> {
            ChangePinStep3Screen(
                onComplete = {
                    onSuccess(memberResult)
                }
            )
        }
    }
}