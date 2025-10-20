package com.onefin.posapp.core.models.enums

enum class CardType(val displayName: String) {
    MAGNETIC("Thẻ từ"),         // Thẻ từ
    CHIP("Thẻ chíp"),           // Thẻ chip (IC)
    CONTACTLESS("Thẻ NFC")      // Thẻ không tiếp xúc (NFC)
}