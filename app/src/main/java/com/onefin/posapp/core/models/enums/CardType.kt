package com.onefin.posapp.core.models.enums

enum class CardProviderType(val value: String) {
    ATM(""),
    VISA("A000000003"),
    MASTER("A000000004"),
    UNION("A000000333"),
    JCB("A000000065"),
    NAPAS("A000000727");

    companion object {
        fun fromAid(aid: String?): CardProviderType {
            return when {
                aid == null -> ATM
                aid.startsWith(VISA.value) -> VISA
                aid.startsWith(MASTER.value) -> MASTER
                aid.startsWith(UNION.value) -> UNION
                aid.startsWith(JCB.value) -> JCB
                aid.startsWith(NAPAS.value) -> NAPAS
                else -> ATM
            }
        }

        fun getCardTypeName(type: CardProviderType): String {
            return when (type) {
                VISA -> "VISA"
                MASTER -> "MASTERCARD"
                UNION -> "UNION"
                JCB -> "JCB"
                NAPAS -> "NAPAS"
                ATM -> "ATM"
            }
        }

        fun fromPaymentSystemBinName(binName: String?): CardProviderType {
            return when (binName) {
                "VISA" -> VISA
                "MASTER", "MASTERCARD" -> MASTER
                "UPI" -> UNION
                "JCB" -> JCB
                "NAPAS" -> NAPAS
                else -> ATM
            }
        }
    }
}