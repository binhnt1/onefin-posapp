package com.onefin.posapp.core.models.data

import java.io.Serializable

data class PaymentSuccessData(
    val amount: Long,
    val transactionId: String,
    val transactionTime: String,
    val timeCountDown: Int? = 10
) : Serializable