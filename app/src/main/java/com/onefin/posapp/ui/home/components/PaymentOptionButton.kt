package com.onefin.posapp.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaymentOptionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isPrimary: Boolean = false
) {
    val primaryColor = Color(0xFF12B76A)
    val secondaryBackgroundColor = Color(0xFFF3FEE7)
    val secondaryContentAndBorderColor = Color(0xFF12B76A)

    val isP2 = remember {
        android.os.Build.MODEL.lowercase().contains("p2")
    }

    val buttonHeight = if (isP2) 56.dp else 80.dp // Giảm ~30%
    val cornerRadius = if (isP2) 8.dp else 12.dp
    val iconSize = if (isP2) 20.dp else 24.dp // Kích thước icon mặc định là 24.dp
    val spacerHeight = if (isP2) 4.dp else 8.dp
    val fontSize = if (isP2) 10.sp else 12.sp
    val contentPadding = if (isP2) PaddingValues(8.dp) else PaddingValues(horizontal = 8.dp, vertical = 12.dp)


    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) primaryColor else secondaryBackgroundColor,
            contentColor = if (isPrimary) Color.White else secondaryContentAndBorderColor,
            disabledContainerColor = Color(0xFFD0D5DD),
            disabledContentColor = Color.White
        ),
        shape = RoundedCornerShape(cornerRadius),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = contentPadding,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) secondaryContentAndBorderColor else Color.Transparent
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(spacerHeight))
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = (fontSize.value + 2).sp
            )
        }
    }
}