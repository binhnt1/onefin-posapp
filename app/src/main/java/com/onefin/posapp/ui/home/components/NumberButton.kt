package com.onefin.posapp.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R

@Composable
fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clearText = stringResource(R.string.numpad_clear)
    val backspaceText = stringResource(R.string.numpad_backspace)
    val isSpecialKey = text == clearText || text == backspaceText

    val isP2 = remember {
        android.os.Build.MODEL.lowercase().contains("p2")
    }

    val cornerRadius = if (isP2) 6.dp else 12.dp
    val fontSize = if (isP2) 18.sp else 24.sp
    // Tăng tỷ lệ aspectRatio để giảm chiều cao tương đối
    val aspectRatio = if (isP2) 2.2f else 2.0f

    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(aspectRatio), // Áp dụng tỷ lệ mới
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSpecialKey) Color(0xFFF2F4F7) else Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFD0D5DD)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = if (isSpecialKey) FontWeight.Bold else FontWeight.Normal,
                color = if (isSpecialKey) Color(0xFF667085) else Color(0xFF101828)
            )
        }
    }
}