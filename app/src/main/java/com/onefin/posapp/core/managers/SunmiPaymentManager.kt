package com.onefin.posapp.core.managers

import android.content.Context
import android.os.Bundle
import com.onefin.posapp.core.config.CardConstants
import com.onefin.posapp.core.models.CardType
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVListenerV2
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Singleton
import sunmi.paylib.SunmiPayKernel
import timber.log.Timber

@Singleton
class SunmiPaymentManager(
    @param:ApplicationContext private val context: Context,
    private val storageService: StorageService,
) {
    private val TAG = "SunmiPayment"
    private var payKernel: SunmiPayKernel? = null
    private var readCardOpt: ReadCardOptV2? = null
    private var emvOpt: EMVOptV2? = null
    private var isConnected = false
    private var currentAmount: String = "0"
    private var currentPaymentAppRequest: PaymentAppRequest? = null

    fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        try {
            Timber.tag(TAG).d("Initializing PaySDK...")
            if (isConnected && readCardOpt != null) {
                Timber.tag(TAG).d("PaySDK ready")
                isConnected = true
                onReady()

                return
            }

            payKernel = SunmiPayKernel.getInstance()
            payKernel?.initPaySDK(context, object : SunmiPayKernel.ConnectCallback {
                override fun onConnectPaySDK() {
                    Timber.tag(TAG).d("PaySDK connected")
                    try {
                        readCardOpt = payKernel?.mReadCardOptV2
                        emvOpt = payKernel?.mEMVOptV2

                        if (readCardOpt == null) {
                            Timber.tag(TAG).e("ReadCardOptV2 is null")
                            onError("ReadCardOptV2 not available")
                            return
                        }

                        if (emvOpt == null) {
                            Timber.tag(TAG).w("EMV service not available - magnetic card only")
                        } else {
                            Timber.tag(TAG).d("Setting up EMV...")
                            setupEmvOnce()
                        }

                        isConnected = true
                        Timber.tag(TAG).d("PaySDK ready")
                        onReady()
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error in onConnectPaySDK")
                        onError("Error onConnectPaySDK: ${e.message}")
                    }
                }

                override fun onDisconnectPaySDK() {
                    Timber.tag(TAG).w("PaySDK disconnected")
                    isConnected = false
                    readCardOpt = null
                    emvOpt = null
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to init PaySDK")
            onError("Failed to init PaySDK: ${e.message}")
        }
    }

    fun startReadCard(
        paymentAppRequest: PaymentAppRequest,
        onCardRead: (RequestSale) -> Unit,
        onError: (String) -> Unit
    ) {
        Timber.tag(TAG).d("startReadCard called")

        if (!isConnected || readCardOpt == null) {
            Timber.tag(TAG).e("Service not connected")
            onError("Service not connected")
            return
        }

        currentPaymentAppRequest = paymentAppRequest
        currentAmount = paymentAppRequest.merchantRequestData?.amount?.toString() ?: "0"

        Timber.tag(TAG).d("Reading card... amount=$currentAmount")

        try {
            readCardOpt?.checkCard(CardConstants.CARD_TYPE_ALL, object : CheckCardCallbackV2.Stub() {

                override fun findMagCard(info: Bundle?) {
                    Timber.tag(TAG).d("Magnetic card detected")
                    // ... rest of code
                }

                override fun findICCard(atr: String?) {
                    Timber.tag(TAG).d("IC card detected")
                    readCardOpt?.cancelCheckCard()
                    processEmvContact(onCardRead, onError)
                }

                override fun findRFCard(uuid: String?) {
                    Timber.tag(TAG).d("RF card detected: $uuid")
                }

                override fun findICCardEx(bundle: Bundle?) {
                    Timber.tag(TAG).d("IC card Ex detected")
                    readCardOpt?.cancelCheckCard()
                    processEmvContact(onCardRead, onError)
                }

                override fun findRFCardEx(bundle: Bundle?) {
                    Timber.tag(TAG).d("RF card Ex detected")
                    val ats = bundle?.getString("ats") ?: bundle?.getString("ATS") ?: ""
                    Timber.tag(TAG).d("ATS: $ats")

                    if (ats.isNotEmpty()) {
                        readCardOpt?.cancelCheckCard()
                        processEmvContactless(onCardRead, onError)
                    }
                }

                override fun onError(code: Int, message: String?) {
                    Timber.tag(TAG).e("Card read error: code=$code, msg=$message")
                    val msg = when (code) {
                        -1 -> "Card read timeout"
                        -2 -> "Operation cancelled"
                        -3 -> "Card removed"
                        -4 -> "Card read failed"
                        else -> message ?: "Unknown error ($code)"
                    }
                    onError(msg)
                }

                override fun onErrorEx(bundle: Bundle?) {
                    val code = bundle?.getInt("code") ?: -999
                    val msg = bundle?.getString("message") ?: "Unknown"
                    Timber.tag(TAG).e("Card read error Ex: code=$code, msg=$msg")
                    onError("$msg (code $code)")
                }
            }, 60)

            Timber.tag(TAG).d("checkCard started")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "startReadCard exception")
            onError("startReadCard error: ${e.message}")
        }
    }

    private fun processEmvContact(
        onCardRead: (RequestSale) -> Unit,
        onError: (String) -> Unit
    ) {
        val emv = emvOpt
        if (emv == null) {
            onError("EMV service not available")
            return
        }

        try {
            emv.abortTransactProcess()
            emv.initEmvProcess()
            setEmvTlvs(emv)

            val bundle = Bundle().apply {
                putString("amount", currentAmount.padStart(12, '0'))
                putString("transType", "00")
                putInt("flowType", 1)
                putInt("cardType", 2)
            }

            emv.transactProcessEx(bundle, createEmvListener(CardType.CHIP, onCardRead, onError))
        } catch (e: Exception) {
            onError("EMV contact error: ${e.message}")
        }
    }

    private fun processEmvContactless(
        onCardRead: (RequestSale) -> Unit,
        onError: (String) -> Unit
    ) {
        val emv = emvOpt
        if (emv == null) {
            onError("EMV service not available")
            return
        }

        try {
            Timber.tag(TAG).d("🔄 Starting EMV contactless process...")
            Timber.tag(TAG).d("Amount: $currentAmount")

            emv.abortTransactProcess()
            emv.initEmvProcess()
            setEmvTlvs(emv)

            val bundle = Bundle().apply {
                putString("amount", currentAmount.padStart(12, '0'))
                putString("transType", "00")
                putInt("flowType", 2)
                putInt("cardType", 4)
            }

            Timber.tag(TAG).d("Bundle: amount=${bundle.getString("amount")}, flowType=2, cardType=4")

            emv.transactProcessEx(bundle, createEmvListener(CardType.CONTACTLESS, onCardRead, onError))
            Timber.tag(TAG).d("✅ transactProcessEx called")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ processEmvContactless exception")
            onError("EMV contactless error: ${e.message}")
        }
    }

    private fun createEmvListener(
        cardType: CardType,
        onCardRead: (RequestSale) -> Unit,
        onError: (String) -> Unit
    ): EMVListenerV2 {
        return object : EMVListenerV2.Stub() {

            override fun onWaitAppSelect(candidates: MutableList<EMVCandidateV2>?, isFirstSelect: Boolean) {
                try {
                    emvOpt?.importAppSelect(0)
                } catch (e: Exception) {
                    onError("importAppSelect error")
                }
            }

            override fun onAppFinalSelect(tag9F06Value: String?) {
                try {
                    emvOpt?.importAppFinalSelectStatus(0)
                } catch (e: Exception) {
                    onError("importAppFinalSelectStatus error")
                }
            }

            override fun onConfirmCardNo(cardNo: String?) {
                try {
                    emvOpt?.importCardNoStatus(0)
                } catch (e: Exception) {
                    onError("importCardNoStatus error")
                }
            }

            override fun onRequestShowPinPad(pinType: Int, remainTime: Int) {
                try {
                    emvOpt?.importPinInputStatus(pinType, 2)
                } catch (e: Exception) {
                    onError("importPinInputStatus error")
                }
            }

            override fun onRequestSignature() {
                try {
                    emvOpt?.importSignatureStatus(0)
                } catch (e: Exception) {
                    onError("importSignatureStatus error")
                }
            }

            override fun onCertVerify(certType: Int, certInfo: String?) {
                try {
                    emvOpt?.importCertStatus(0)
                } catch (e: Exception) {
                    onError("importCertStatus error")
                }
            }

            override fun onOnlineProc() {
                try {
                    emvOpt?.importOnlineProcStatus(0, null, null, null)
                } catch (e: Exception) {
                    onError("importOnlineProcStatus error")
                }
            }

            override fun onCardDataExchangeComplete() {
            }

            override fun onRequestDataExchange(data: String?) {
                try {
                    emvOpt?.importDataExchangeStatus(0)
                } catch (e: Exception) {
                    onError("importDataExchangeStatus error")
                }
            }

            override fun onTransResult(resultCode: Int, msg: String?) {
                if (resultCode == 0) {
                    try {
                        val tagsToRead = arrayOf(
                            "5A", "57", "5F24", "5F34", "9F06", "9F26", "9F27",
                            "9F10", "9F37", "9F36", "95", "9A", "9C", "9F02",
                            "5F2A", "82", "9F1A", "9F33", "9F34", "9F35", "9F09"
                        )

                        val outBuf = ByteArray(8192)
                        val ret = emvOpt?.getTlvList(0, tagsToRead, outBuf) ?: -1

                        if (ret > 0) {
                            val tlvData = outBuf.copyOf(ret)
                            val tlvHex = tlvData.joinToString("") { "%02X".format(it) }
                            val tags = CardHelper.parseEmvTlv(tlvHex)
                            val request = currentPaymentAppRequest
                            if (request == null) {
                                onError("Payment request not initialized")
                                return
                            }
                            val requestSale = CardHelper.buildRequestSale(request,
                                RequestSale.Data.Card(
                                    emvData = tlvHex,
                                    track2 = tags["57"] ?: "",
                                    type = cardType.toString(),
                                    clearPan = tags["5A"] ?: "",
                                    expiryDate = tags["5F24"] ?: "",
                                )
                            )
                            onCardRead(requestSale)
                        } else {
                            onError("Cannot read card data")
                        }
                    } catch (e: Exception) {
                        onError("Error reading EMV data")
                    }
                } else {
                    onError("EMV failed: $msg")
                }
            }

            override fun onPreFirstGenAC() {}
            override fun onTermRiskManagement() {}
            override fun onConfirmationCodeVerified() {}
            override fun onDataStorageProc(tags: Array<out String?>?, values: Array<out String?>?) {}
        }
    }

    private fun setEmvTlvs(emv: EMVOptV2) {
        val terminal = storageService.getAccount()?.terminal
        CardHelper.setEmvTlvs(emv, terminal)
    }

    private fun setupEmvOnce() {
        val emv = emvOpt ?: return
        try {
            emv.deleteAid(null)
            emv.deleteCapk(null, null)

            val evmConfigs = storageService.getAccount()?.terminal?.evmConfigs
            if (!evmConfigs.isNullOrEmpty()) {
                evmConfigs.forEach { config ->
                    emv.addAid(UtilHelper.evmConfigToAidV2(config))
                }
            }

            val termParam = CardHelper.createTerminalParam()
            emv.setTerminalParam(termParam)
        } catch (e: Exception) {
        }
    }

    fun cancelReadCard() {
        try {
            readCardOpt?.cancelCheckCard()
        } catch (e: Exception) {
        }
    }

    fun cleanup() {
        try {
            emvOpt = null
            payKernel = null
            readCardOpt = null
            isConnected = false
        } catch (e: Exception) {
        }
    }
}