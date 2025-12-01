package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.MifareData
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.MifareUtil
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface PinInputCallback {
    fun requestPinInput(onPinEntered: (String) -> Unit, onCancelled: () -> Unit)
}

class MifareCardProcessor(
    context: Context,
    emvOpt: EMVOptV2,
    terminal: Terminal?,
    pinPadOpt: PinPadOptV2,
    readCardOpt: ReadCardOptV2,
    securityOpt: SecurityOptV2,
    private val pinInputCallback: PinInputCallback? = null,
) : BaseCardProcessor(context, emvOpt, terminal, pinPadOpt, readCardOpt, securityOpt, AidlConstants.CardType.MIFARE) {

    private var mifareData: MifareData? = null

    private val storageService: StorageService by lazy {
        val app = context.applicationContext
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            app,
            StorageServiceEntryPoint::class.java
        )
        entryPoint.storageService()
    }

    override fun processTransaction(info: Bundle) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Timber.d("🎴 ====== MIFARE CARD PROCESSING START ======")

                // Step 2: Read Mifare card data
                if (!readMifareCardData()) {
                    return@launch
                }

                // Step 4: Check if PIN is required
                val nfcConfig = storageService.getNfcConfig()
                if (nfcConfig?.isPinRequired() == true) {
                    withContext(Dispatchers.Main) {
                        promptPinInput()
                    }
                } else {
                    completeTransaction(null)
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ Exception in processTransaction")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Failed to process Mifare card: ${e.message}"
                    )
                )
            }
        }
    }

    private fun promptPinInput() {
        try {
            Timber.d("🔔 === PROMPT PIN INPUT ===")

            val pan = mifareData?.getPanFromTrack2() ?: run {
                Timber.e("❌ PAN not available")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "PAN not available for PIN entry"
                    )
                )
                return
            }

            Timber.d("   📌 PAN: ${pan.take(6)}...${pan.takeLast(4)}")

            if (pinInputCallback == null) {
                Timber.e("❌ PinInputCallback is NULL")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                        "PIN input not configured"
                    )
                )
                return
            }

            Timber.d("   ✅ Requesting PIN input from UI...")

            pinInputCallback.requestPinInput(
                onPinEntered = { clearPin ->
                    Timber.d("   ✅ PIN entered from UI: $clearPin")
                    handleCustomPinInput(clearPin)
                },
                onCancelled = {
                    Timber.w("   ⚠️ PIN entry cancelled by user")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.USER_CANCELLED,
                            "PIN entry cancelled"
                        )
                    )
                }
            )

            Timber.d("🔔 === PROMPT PIN INPUT END ===")

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception in promptPinInput")
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                    "PIN input exception: ${e.message}"
                )
            )
        }
    }

    private suspend fun readMifareCardData(): Boolean {
        val nfcConfig = storageService.getNfcConfig()
        val nfcKey = nfcConfig?.nfckey
        if (nfcKey.isNullOrEmpty()) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.SDK_INIT_FAILED_MIFARE,
                    "NFC key not available"
                )
            )
            return false
        }

        Timber.d("📖 Starting Mifare card read...")
        mifareData = withContext(Dispatchers.IO) {
            MifareUtil.readMifareCard(readCardOpt, nfcKey)
        }
        Timber.d("📊 Mifare data result: ${mifareData != null}")

        if (mifareData == null) {
            Timber.e("❌ Failed to read Mifare card")
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Failed to read Mifare card data"
                )
            )
            return false
        }
        return true
    }

    private fun completeTransaction(pinBlock: String?) {
        try {
            val request = currentPaymentAppRequest ?: run {
                processingComplete(
                    PaymentResult.Error.from(
                        errorType = PaymentErrorHandler.ErrorType.PAYMENT_REQUEST_NOT_INITIALIZED
                    )
                )
                return
            }

            val data = mifareData ?: run {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "Card data not available"
                    )
                )
                return
            }

            val track2 = data.getTrack2()
            val icData = data.getIcData()
            val pan = data.getPanFromTrack2() ?: ""
            val expiry = data.getExpiryFromTrack2()
            val holderName = data.getCardHolderName(icData) ?: ""
            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    ksn = "",
                    track1 = "",
                    clearPan = pan,
                    pin = pinBlock,
                    track2 = track2,
                    emvData = icData,
                    expiryDate = expiry,
                    holderName = holderName,
                    mode = cardType.value.toString(),
                    type = CardHelper.detectBrand(pan),
                    issuerName = storageService.getAccount()?.name,
                ),
                RequestSale.Data.Device(
                    posEntryMode = "00",
                    posConditionCode = "00"
                )
            )
            processingComplete(PaymentResult.Success(requestSale))

        } catch (e: Exception) {
            handleError(
                PaymentResult.Error.from(
                    PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                    "Failed to complete transaction: ${e.message}"
                )
            )
        }
    }

    private fun handleCustomPinInput(clearPin: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Timber.d("🔐 === HANDLE CUSTOM PIN INPUT ===")
                Timber.d("   📌 Clear PIN received: $clearPin")
                Timber.d("   📏 PIN length: ${clearPin.length}")

                // ✅ Validate: PIN must be exactly 6 digits
                if (clearPin.length != 6) {
                    Timber.e("❌ Invalid PIN length")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "PIN phải có đúng 6 số"
                        )
                    )
                    return@launch
                }

                // ✅ Validate: PIN must be all digits
                if (!clearPin.all { it.isDigit() }) {
                    Timber.e("❌ PIN contains non-digits")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "PIN chỉ được chứa số"
                        )
                    )
                    return@launch
                }

                Timber.d("   ✅ PIN validation passed")
                Timber.d("   🚀 Calling completeTransaction with PIN: $clearPin")

                // ✅ Gửi PIN thô cho MIFARE
                completeTransaction(clearPin)

                Timber.d("🔐 === HANDLE CUSTOM PIN INPUT END ===")

            } catch (e: Exception) {
                Timber.e(e, "❌ Exception in handleCustomPinInput")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                        "Failed to process PIN: ${e.message}"
                    )
                )
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface StorageServiceEntryPoint {
    fun storageService(): StorageService
}