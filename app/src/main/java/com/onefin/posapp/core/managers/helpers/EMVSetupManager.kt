package com.onefin.posapp.core.managers.helpers

import com.onefin.posapp.core.models.Terminal
import com.onefin.posapp.core.utils.CardHelper
import com.onefin.posapp.core.utils.UtilHelper
import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import timber.log.Timber

class EMVSetupManager(
    private val emvOpt: EMVOptV2
) {

    private var isSetupCompleted = false

    fun setupOnce(terminal: Terminal?): Result<Unit> {
        if (isSetupCompleted) {
            return Result.success(Unit)
        }

        return try {
            clearExistingData()
            injectCapks()
            addAids(terminal)
            setTerminalParameters(terminal)

            isSetupCompleted = true
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "EMV setup failed")
            Result.failure(e)
        }
    }

    fun reset() {
        isSetupCompleted = false
    }

    private fun clearExistingData() {
        try {
            emvOpt.deleteAid(null)
            emvOpt.deleteCapk(null, null)
        } catch (e: Exception) {
            Timber.w(e, "Error clearing EMV data")
        }
    }

    private fun injectCapks() {
        try {
            CardHelper.injectCapks(emvOpt)
        } catch (e: Exception) {
            throw Exception("Failed to inject CAPKs: ${e.message}", e)
        }
    }

    private fun addAids(terminal: Terminal?) {
        val evmConfigs = terminal?.evmConfigs

        if (evmConfigs.isNullOrEmpty()) {
            Timber.w("No EMV configs found")
            return
        }

        evmConfigs.forEach { config ->
            try {
                val aid = UtilHelper.evmConfigToAidV2(config)
                val result = emvOpt.addAid(aid)

                if (result != 0) {
                    Timber.w("Failed to add AID for ${config.vendorName}: $result")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error adding AID for ${config.vendorName}")
            }
        }
    }

    private fun setTerminalParameters(terminal: Terminal?) {
        try {
            val evmConfigs = terminal?.evmConfigs
            val firstConfig = evmConfigs?.firstOrNull()
            val termParam = CardHelper.createTerminalParam(firstConfig)

            emvOpt.setTerminalParam(termParam)
        } catch (e: Exception) {
            throw Exception("Failed to set terminal parameters: ${e.message}", e)
        }
    }

    fun isSetupComplete(): Boolean = isSetupCompleted
}