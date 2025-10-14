package com.onefin.posapp.core.utils

import android.content.Context
import com.onefin.posapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun validateEmail(email: String): String? {
        return when {
            email.isEmpty() -> context.getString(R.string.error_email_empty)
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> 
                context.getString(R.string.error_email_invalid)
            else -> null
        }
    }

    fun validatePassword(password: String, minLength: Int = 6): String? {
        return when {
            password.isEmpty() -> context.getString(R.string.error_password_empty)
            password.length < minLength -> 
                context.getString(R.string.error_password_min_length, minLength)
            else -> null
        }
    }

    fun validatePhone(phone: String): String? {
        return when {
            phone.isEmpty() -> context.getString(R.string.error_phone_empty)
            !phone.matches(Regex("^[0-9]{10,11}$")) -> 
                context.getString(R.string.error_phone_invalid)
            else -> null
        }
    }

    fun validateRequired(value: String, fieldName: String = ""): String? {
        val field = fieldName.ifEmpty { context.getString(R.string.field_default) }
        return if (value.isEmpty()) {
            context.getString(R.string.error_field_required, field)
        } else null
    }

    fun validateMinLength(value: String, minLength: Int, fieldName: String = ""): String? {
        val field = fieldName.ifEmpty { context.getString(R.string.field_default) }
        return if (value.length < minLength) {
            context.getString(R.string.error_field_min_length, field, minLength)
        } else null
    }

    fun validateMaxLength(value: String, maxLength: Int, fieldName: String = ""): String? {
        val field = fieldName.ifEmpty { context.getString(R.string.field_default) }
        return if (value.length > maxLength) {
            context.getString(R.string.error_field_max_length, field, maxLength)
        } else null
    }

    fun validateNumber(value: String): String? {
        return when {
            value.isEmpty() -> context.getString(R.string.error_number_empty)
            !value.matches(Regex("^[0-9]+$")) -> 
                context.getString(R.string.error_number_only)
            else -> null
        }
    }

    fun validateAmount(
        value: String,
        minAmount: Long = 0,
        maxAmount: Long = Long.MAX_VALUE
    ): String? {
        return when {
            value.isEmpty() -> context.getString(R.string.error_amount_empty)
            value.toLongOrNull() == null -> context.getString(R.string.error_amount_invalid)
            value.toLong() < minAmount -> 
                context.getString(R.string.error_amount_min, minAmount)
            value.toLong() > maxAmount -> 
                context.getString(R.string.error_amount_max, maxAmount)
            else -> null
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isEmpty() -> 
                context.getString(R.string.error_confirm_password_empty)
            password != confirmPassword -> 
                context.getString(R.string.error_confirm_password_not_match)
            else -> null
        }
    }
}