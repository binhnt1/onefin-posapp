package com.onefin.posapp.core.models.data

import java.io.Serializable

data class PaymentSuccessData(
    val amount: Long,
    val transactionTime: String,
    val transactionId: String
) : Serializable