package com.onefin.posapp.core.config

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