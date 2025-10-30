package com.onefin.posapp.core.managers.helpers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.models.data.MifareData
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.models.enums.CardType
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
                    Timber.d("✅ No PIN required, completing transaction...")
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
            val pan = mifareData?.getPanFromTrack2() ?: run {
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                        "PAN not available for PIN entry"
                    )
                )
                return
            }

            // ⭐ Sử dụng custom PIN input thay vì hardware pinpad
            if (pinInputCallback == null) {
                Timber.e("❌ PinInputCallback not provided")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                        "PIN input not configured"
                    )
                )
                return
            }

            Timber.d("🔐 Requesting custom PIN input...")

            pinInputCallback.requestPinInput(
                onPinEntered = { clearPin ->
                    handleCustomPinInput(clearPin)
                },
                onCancelled = {
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.USER_CANCELLED,
                            "PIN entry cancelled"
                        )
                    )
                }
            )

        } catch (e: Exception) {
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
        Timber.d("🔑 NFC Key: $nfcKey")
        Timber.d("🔑 Key length: ${nfcKey?.length}")

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
            val holderName = data.getCardHolderName(icData)
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
                    mode = CardType.MIFARE.displayName,
                    type = CardHelper.detectBrand(pan),
                    issuerName = storageService.getAccount()?.name,
                )
            )
            processingComplete(PaymentResult.Success(requestSale))
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception completing transaction")
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
                // ⭐ Validate: PIN must be exactly 6 digits
                if (clearPin.length != 6) {
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "PIN phải có đúng 6 số"
                        )
                    )
                    return@launch
                }

                // ⭐ Validate: PIN must be all digits
                if (!clearPin.all { it.isDigit() }) {
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "PIN chỉ được chứa số"
                        )
                    )
                    return@launch
                }
                completeTransaction(clearPin)

            } catch (e: Exception) {
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