package com.onefin.posapp.ui.payment.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onefin.posapp.core.models.data.PaymentState
import com.onefin.posapp.core.models.data.RequestSale

@Composable
@OptIn(ExperimentalAnimationApi::class)
fun PaymentStatusCard(
    paymentState: PaymentState,
    statusMessage: String,
    cardInfo: String,
    currentRequestSale: RequestSale?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = paymentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "PaymentStateTransition"
            ) { state ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        PaymentState.INITIALIZING,
                        PaymentState.WAITING_CARD -> {
                            WaitingCardContent(statusMessage)
                        }

                        PaymentState.CARD_DETECTED -> {
                            CardDetectedContent(
                                statusMessage = statusMessage,
                                cardInfo = cardInfo,
                                currentRequestSale = currentRequestSale
                            )
                        }

                        PaymentState.ENTERING_PIN -> {
                            EnteringPinContent(statusMessage)
                        }

                        PaymentState.PROCESSING -> {
                            ProcessingContent(statusMessage)
                        }

                        PaymentState.ERROR -> {
                            ErrorContent(statusMessage)
                        }

                        PaymentState.SUCCESS -> {

                        }
                    }
                }
            }
        }
    }
}