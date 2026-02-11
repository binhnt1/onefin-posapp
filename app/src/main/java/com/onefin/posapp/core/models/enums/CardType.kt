package com.onefin.posapp.core.models.enums

enum class CardProviderType(val value: String) {
    ATM(""),
    JCB("A000000065"),

    AE("A00000002501"),
    VISA("A000000003"),
    UNION("A000000333"),
    NAPAS("A000000727"),
    MASTER("A000000004");

    companion object {
        fun fromAid(aid: String?): CardProviderType {
            return when {
                aid == null -> ATM
                aid.startsWith(AE.value) -> AE
                aid.startsWith(JCB.value) -> JCB
                aid.startsWith(VISA.value) -> VISA
                aid.startsWith(UNION.value) -> UNION
                aid.startsWith(NAPAS.value) -> NAPAS
                aid.startsWith(MASTER.value) -> MASTER
                else -> ATM
            }
        }

        fun getCardTypeName(type: CardProviderType): String {
            return when (type) {
                AE -> "AE"
                JCB -> "JCB"
                VISA -> "VISA"
                UNION -> "UNION"
                NAPAS -> "NAPAS"
                MASTER -> "MASTERCARD"
                else -> "ATM"
            }
        }

        fun fromPaymentSystemBinName(binName: String?): CardProviderType {
            return when (binName) {
                "JCB" -> JCB
                "VISA" -> VISA
                "UPI" -> UNION
                "NAPAS" -> NAPAS
                "AE", "AMEX" -> AE
                "MASTER", "MASTERCARD" -> MASTER
                else -> ATM
            }
        }
    }
}