package com.onefin.posapp.core.utils

import com.onefin.posapp.core.models.enums.CardProviderType
import com.sunmi.pay.hardware.aidl.AidlConstants

object F55Manager {

    private val VPB_F55_TAGS_VISA_CONTACT = arrayOf(
        EMVTag.CHIP_APP_ID, "5F2A", "5F34", "82", "84", "95", "9A", "9B", "9C",
        "9F02", "9F09", "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33", "9F34",
        "9F35", "9F36", "9F37"
    )

    private val VPB_F55_TAGS_VISA_CONTACTLESS = arrayOf(
        EMVTag.CHIP_APP_ID, "5F2A", "5F34", "82", "84", "95", "9A", "9C",
        "9F02", "9F09", "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33",
        "9F35", "9F36", "9F37"
    )

    private val VPB_F55_TAGS_MASTERCARD_CONTACT = arrayOf(
        EMVTag.CHIP_APP_ID, "5F2A", "5F34", "82", "84", "95", "9A", "9C",
        "9F02", "9F09", "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33",
        "9F34", "9F35", "9F36", "9F37"
    )

    private val VPB_F55_TAGS_MASTERCARD_CONTACTLESS = arrayOf(
        "5F2A", "5F34", "82", "84", "95", "9A", "9C", "9F02", "9F09",
        "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33", "9F34", "9F35",
        "9F36", "9F37", "9F6E"
    )

    private val VPB_F55_TAGS_VCCS_CONTACT = arrayOf(
        "5F2A", "5F34", "82", "84", "95", "9A", "9C", "9F02", "9F10",
        "9F1A", EMVTag.CHIP_TC, "9F27", "9F33", "9F34", "9F36", "9F37", "9F35"
    )

    private val VPB_F55_TAGS_VCCS_CONTACTLESS = arrayOf(
        "5F2A", "5F34", "82", "84", "95", "9A", "9C", "9F02", "9F10",
        "9F1A", EMVTag.CHIP_TC, "9F27", "9F36", "9F37", "9F34"
    )

    private val VPB_F55_TAGS_JCB_CONTACT = arrayOf(
        "5F2A", "5F34", "82", "84", "95", "9A", "9C", "9F02", "9F09",
        "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33", "9F34", "9F35",
        "9F36", "9F37"
    )

    private val VPB_F55_TAGS_JCB_CONTACTLESS = arrayOf(
        "5F2A", "5F34", "82", "84", "95", "9A", "9C", "9F02", "9F09",
        "9F10", "9F1A", EMVTag.CHIP_TC, "9F27", "9F33", "9F34", "9F35",
        "9F36", "9F37", "9F6E", "9F7C", EMVTag.CHIP_APP_ID
    )

    fun getF55TagsRequired(cardType: CardProviderType?, cardInterface: AidlConstants.CardType): Array<String> {
        return when (cardType) {
            CardProviderType.VISA if cardInterface == AidlConstants.CardType.IC ->
                VPB_F55_TAGS_VISA_CONTACT

            CardProviderType.VISA if cardInterface == AidlConstants.CardType.NFC ->
                VPB_F55_TAGS_VISA_CONTACTLESS

            CardProviderType.MASTER if cardInterface == AidlConstants.CardType.IC ->
                VPB_F55_TAGS_MASTERCARD_CONTACT

            CardProviderType.MASTER if cardInterface == AidlConstants.CardType.NFC ->
                VPB_F55_TAGS_MASTERCARD_CONTACTLESS

            CardProviderType.NAPAS if cardInterface == AidlConstants.CardType.IC ->
                VPB_F55_TAGS_VCCS_CONTACT

            CardProviderType.NAPAS if cardInterface == AidlConstants.CardType.NFC ->
                VPB_F55_TAGS_VCCS_CONTACTLESS

            CardProviderType.JCB if cardInterface == AidlConstants.CardType.IC ->
                VPB_F55_TAGS_JCB_CONTACT

            CardProviderType.JCB if cardInterface == AidlConstants.CardType.NFC ->
                VPB_F55_TAGS_JCB_CONTACTLESS

            else -> emptyArray()
        }
    }
}

object EMVTag {
    const val CARD_NO = "5A"
    const val NAME_HOLDER = "5F20"
    const val TRACK_1_DATA = "56"
    const val TRACK_2_DATA = "57"
    const val TRACK_3_DATA = "58"
    const val CHIP_EXPIRY_DATE = "5F24"
    const val CHIP_APP_NAME = "50"
    const val CHIP_APP_ID = "4F"
    const val CHIP_APP_ID_84 = "84"
    const val CHIP_TC = "9F26"
    const val POS_ENTRY_MODE = "9F39"
    const val TAG_9F41_F62 = "9F41"
}

fun Array<String>.toTagString(): String = this.joinToString(", ")