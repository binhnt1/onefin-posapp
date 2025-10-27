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
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadListenerV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MifareCardProcessor(
    context: Context,
    emvOpt: EMVOptV2,
    terminal: Terminal?,
    pinPadOpt: PinPadOptV2,
    readCardOpt: ReadCardOptV2
) : BaseCardProcessor(context, emvOpt, terminal, pinPadOpt, readCardOpt, AidlConstants.CardType.MIFARE) {

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
            val pinListener = object : PinPadListenerV2.Stub() {
                override fun onPinLength(len: Int) {
                    Timber.d("PIN length: $len")
                }

                override fun onConfirm(data: Int, pinBlock: ByteArray?) {
                    Timber.d("✅ PIN confirmed, data=$data")
                    handlePinConfirm(data, pinBlock)
                }

                override fun onCancel() {
                    Timber.w("⚠️ PIN entry cancelled by user")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.USER_CANCELLED,
                            "PIN entry cancelled"
                        )
                    )
                }

                override fun onError(code: Int) {
                    Timber.e("❌ PIN entry error: code=$code")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "PIN entry failed with code: $code",
                            code.toString()
                        )
                    )
                }
            }

            // Use startInputPin with special config to get clear PIN
            val pinBundle = Bundle().apply {
                putInt("pinPadType", 0) // Normal PIN pad
                putInt("pinType", 0) // Online PIN
                putBoolean("isOrderNumKey", false)
                putInt("minInput", 4)
                putInt("maxInput", 12)
                putInt("timeout", 60 * 1000) // 60 seconds
                putBoolean("isSupportbypass", false)
                putInt("pinKeyIndex", -1) // -1 = no encryption (clear text)
                putString("pan", pan)
                putInt("keySystem", 0)
                putInt("algorithmType", 0)
                putInt("pinblockFormat", 0)
            }

            Timber.d("   Starting PIN input (startInputPin)...")
            val result = pinPadOpt.startInputPin(pinBundle, pinListener)

            if (result != 0) {
                Timber.e("❌ Failed to start PIN input: result=$result")
                handleError(
                    PaymentResult.Error.from(
                        PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                        "Failed to start PIN input",
                        result.toString()
                    )
                )
            } else {
                Timber.d("   ✅ startInputPin OK")
            }

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

    private fun getClearPin(data: Int): String? {
        return try {
            // Use getPinBlock to retrieve clear PIN
            val pinBundle = Bundle().apply {
                putInt("pinKeyIndex", -1) // -1 for clear text
                putInt("keySystem", 0)
                putInt("algorithmType", 0)
                putInt("pinblockFormat", 0)
            }

            val pinBlockBytes = ByteArray(16)
            val result = pinPadOpt.getPinBlock(pinBundle, pinBlockBytes)

            if (result <= 0) {
                Timber.e("❌ getPinBlock failed: result=$result")
                return null
            }

            // Clear PIN is returned as ASCII bytes
            val clearPin = String(pinBlockBytes, 0, result, Charsets.UTF_8).trim()
            Timber.d("🔐 Retrieved clear PIN (length: ${clearPin.length})")
            clearPin

        } catch (e: Exception) {
            Timber.e(e, "❌ Exception getting clear PIN")
            null
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

        mifareData = withContext(Dispatchers.IO) {
            MifareUtil.readMifareCard(readCardOpt, nfcKey)
        }
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
            val serialNumber = data.getSerialNumber()

            Timber.d("🎉 ====== MIFARE TRANSACTION COMPLETE ======")
            Timber.d("   PAN: ${pan.take(6)}****${pan.takeLast(4)}")
            Timber.d("   Expiry: $expiry")
            Timber.d("   Serial: $serialNumber")
            Timber.d("   PIN Block: ${pinBlock ?: "N/A"}")

            val requestSale = CardHelper.buildRequestSale(
                request,
                RequestSale.Data.Card(
                    ksn = "",
                    track1 = "",
                    clearPan = pan,
                    pin = pinBlock,
                    track2 = track2,
                    emvData = icData,
                    holderName = null,
                    expiryDate = expiry,
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

    private fun handlePinConfirm(data: Int, pinBlock: ByteArray?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clearPin = getClearPin(data)

                if (clearPin == null) {
                    Timber.e("❌ Failed to get clear PIN")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "Failed to retrieve clear PIN"
                        )
                    )
                    return@launch
                }

                Timber.d("🔐 Clear PIN received (length: ${clearPin.length})")

                // Get PAN and pkey for encryption
                val pan = mifareData?.getPanFromTrack2() ?: run {
                    Timber.e("❌ PAN not available for PIN encryption")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.CARD_READ_FAILED,
                            "PAN not available"
                        )
                    )
                    return@launch
                }

                val pkeyConfig = storageService.getPkeyConfig()
                val pkeyHex = pkeyConfig?.pkey ?: run {
                    Timber.e("❌ Pkey not available for PIN encryption")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.SDK_INIT_FAILED_MIFARE,
                            "Pkey not available"
                        )
                    )
                    return@launch
                }

                // Build encrypted PIN block using MifareUtil
                val encryptedPinBlock = MifareUtil.buildPinBlock(clearPin, pan, pkeyHex)
                if (encryptedPinBlock == null) {
                    Timber.e("❌ Failed to build encrypted PIN block")
                    handleError(
                        PaymentResult.Error.from(
                            PaymentErrorHandler.ErrorType.PIN_INPUT_FAILED,
                            "Failed to encrypt PIN"
                        )
                    )
                    return@launch
                }

                Timber.d("🔐 Encrypted PIN block: $encryptedPinBlock")

                // Complete transaction with encrypted PIN block
                completeTransaction(encryptedPinBlock)

            } catch (e: Exception) {
                Timber.e(e, "❌ Exception handling PIN confirmation")
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