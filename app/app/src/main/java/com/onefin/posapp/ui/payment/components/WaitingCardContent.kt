package com.onefin.posapp.ui.payment.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WaitingCardContent(statusMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PulsingCardIcon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = statusMessage,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        CardTypeIcons()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hỗ trợ: Thẻ từ • Chip • Contactless",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center
        )
    }
}