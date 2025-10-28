package com.onefin.posapp.core.config

import android.annotation.SuppressLint

object StorageKeys {
    const val TOKEN = "auth_token"
    const val ACCOUNT = "account_data"
    const val SERIAL = "device_serial_key"
    const val LANGUAGE = "selected_language"
}

object ApiConstants {
    const val HEADER_PADDING = "X-Padding"
    const val HEADER_AUTH = "Authorization"
    const val HEADER_SERIAL = "X-Header-Serial"
    const val CONTENT_TYPE = "application/json; charset=UTF-8"

    const val TIMEOUT_READ = 15L
    const val TIMEOUT_WRITE = 15L
    const val TIMEOUT_CONNECT = 15L
}

object PrefsName {
    const val APP_PREFS = "onefin_prefs"
    const val APP_PREFS_EX = "onefin_prefs_ex"
    const val PREF_EXTERNAL_PAYMENT_CONTEXT = "external_payment_context"
    const val PREF_PENDING_PAYMENT_REQUEST = "pending_payment_request"
}

object RequestConstants {
    const val LAT_LNG_DEFAULT = "10.762622,106.660172"
    const val REQUEST_ID_LENGTH = 16
}

object LanguageConstants {
    const val ENGLISH = "en"
    const val VIETNAMESE = "vi"
    const val DEFAULT_LANGUAGE = VIETNAMESE
}

object RabbitConstants {
    @SuppressLint("AuthLeak")
    const val DEFAULT_RABBIT_URL = "amqp://sit-posapp:ARGcn5nnmkTE@14.241.228.156:5672/posapp"
    const val EXCHANGE_NAME = "Pos_App_Service"
    const val RECONNECT_DELAY = 5000L
}

object CardConstants {
    // Card detection types (cho ReadCardOptV2.checkCard)
    const val CARD_TYPE_MAGNETIC = 0x01
    const val CARD_TYPE_IC = 0x02
    const val CARD_TYPE_NFC = 0x04
    const val CARD_TYPE_ALL = 0x07

    // EMV Kernel OpCodes (theo Sunmi PaySDK AIDL v2)
    const val OP_NORMAL = 0        // Global terminal parameters
    const val OP_PAYPASS = 1       // Mastercard contactless (M/Chip)
    const val OP_PAYWAVE = 2       // Visa contactless (payWave)
    const val OP_QPBOC = 3         // UnionPay contactless (QuickPass)
    const val OP_EXPRESSPAY = 4    // Amex contactless
    const val OP_DPAS = 5          // Discover contactless (D-PAS)
    const val OP_JSPEEDY = 6       // JCB contactless (J/Speedy)
    const val OP_PURE = 7          // Pure contactless (nếu có)
    const val PIN_KEY_INDEX = 11
}

object ResultConstants {
    const val RESULT_TYPE = "type"
    const val RESULT_ERROR = "error"
    const val RESULT_ACTION = "action"
    const val REQUEST_CODE_PAYMENT = 1000
    const val EXTRA_MERCHANT_REQUEST_DATA = "merchant_request_data"
    const val RESULT_PAYMENT_RESPONSE_DATA = "payment_response_data"
}

object MifareConstants {
    const val KEY_TYPE_A = 0
    const val BLOCK_SIZE = 16
    const val BLOCKS_PER_SECTOR = 4
    const val NFC_CONFIG = "nfc_config"
    const val PKEY_CONFIG = "pkey_config"
    const val NFC_CONFIG_TIMESTAMP = "nfc_config_timestamp"
    const val PKEY_CONFIG_TIMESTAMP = "pkey_config_timestamp"
    const val NFC_CONFIG_CACHE_DURATION = 24 * 60 * 60 * 1000L
    const val PKEY_CONFIG_CACHE_DURATION = 12 * 60 * 60 * 1000L
}
