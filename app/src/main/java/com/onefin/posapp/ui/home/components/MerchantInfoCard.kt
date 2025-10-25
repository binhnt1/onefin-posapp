package com.onefin.posapp.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R

/**
 * Component hiển thị thông tin merchant từ merchantConfig
 *
 * @param merchantConfig Map chứa config data từ Terminal
 * @param fieldMapping Map<key, label> để map key với label hiển thị
 * @param modifier Modifier cho component
 */
@Composable
fun MerchantInfoCard(
    merchantConfig: Map<String, Any?>?,
    fieldMapping: Map<String, String>,
    modifier: Modifier = Modifier
) {
    // Chỉ hiển thị nếu merchantConfig có data
    if (merchantConfig.isNullOrEmpty()) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            val context = LocalContext.current
            Text(
                text = context.getString(R.string.merchant_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101828)
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFFEAECF0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Render các field theo fieldMapping
            fieldMapping.entries.forEachIndexed { index, (key, label) ->
                val value = merchantConfig[key]

                // Chỉ hiển thị nếu có value
                if (value != null) {
                    MerchantInfoRow(
                        label = label,
                        value = value.toString()
                    )

                    // Thêm spacer giữa các row (trừ row cuối)
                    if (index < fieldMapping.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Component row hiển thị một cặp label-value
 */
@Composable
private fun MerchantInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475467),
            modifier = Modifier.weight(0.4f)
        )

        // Value
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF101828),
            modifier = Modifier.weight(0.6f)
        )
    }
}