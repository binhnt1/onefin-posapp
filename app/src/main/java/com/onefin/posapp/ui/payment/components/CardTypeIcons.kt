package com.onefin.posapp.ui.payment.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.onefin.posapp.R

@Composable
fun CardTypeIcons(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visa
        Image(
            painter = painterResource(id = R.drawable.icon_visa),
            contentDescription = "Visa",
            modifier = Modifier
                .height(24.dp)
                .width(40.dp)
        )

        // Mastercard
        Image(
            painter = painterResource(id = R.drawable.icon_master),
            contentDescription = "Mastercard",
            modifier = Modifier
                .height(24.dp)
                .width(40.dp)
        )

        // JCB
        Image(
            painter = painterResource(id = R.drawable.icon_jcb),
            contentDescription = "JCB",
            modifier = Modifier
                .height(24.dp)
                .width(40.dp)
        )

        // AMEX
        Image(
            painter = painterResource(id = R.drawable.icon_card),
            contentDescription = "American Express",
            modifier = Modifier
                .height(24.dp)
                .width(40.dp)
        )
    }
}