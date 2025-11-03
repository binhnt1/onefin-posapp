package com.onefin.posapp.ui.home.components

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R

@Composable
fun CollapsibleMerchantInfoCard(
    merchantConfig: Map<String, String>,
    fieldMapping: Map<String, String>,
    merchantCompany: String? = null,
    accountName: String? = null,
    accountNumber: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isP2 = remember { Build.MODEL.lowercase().contains("p2") }

    // Icons mapping
    val iconMapping = remember {
        mapOf(
            "driver" to R.drawable.ic_car,
            "employee" to R.drawable.ic_person,
            "tid" to R.drawable.ic_terminal,
            "mid" to R.drawable.ic_merchant,
            "provider" to R.drawable.ic_bank,
            "company" to R.drawable.ic_merchant,
            "account" to R.drawable.ic_bank
        )
    }

    // Color mapping for icons
    val iconColorMapping = remember {
        mapOf(
            "driver" to Color(0xFF3B82F6),    // Blue
            "employee" to Color(0xFF8B5CF6),  // Purple
            "tid" to Color(0xFF10B981),       // Green
            "mid" to Color(0xFFF59E0B),       // Amber
            "provider" to Color(0xFFEF4444),  // Red
            "company" to Color(0xFF06B6D4),   // Cyan
            "account" to Color(0xFFEC4899)    // Pink
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Bar - Always visible, clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = if (isP2) 12.dp else 16.dp, vertical = if (isP2) 10.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_terminal),
                        contentDescription = "Device Info",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(if (isP2) 20.dp else 24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.device_info_title),
                        fontSize = if (isP2) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                // Arrow icon with rotation animation
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "arrow_rotation"
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier
                        .size(if (isP2) 20.dp else 24.dp)
                        .rotate(rotation)
                )
            }

            // Divider
            if (isExpanded) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(300),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(
                    animationSpec = tween(300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isP2) 12.dp else 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Row 1: Driver & Employee Info
                    val hasDriverInfo = merchantConfig.containsKey("driver") || merchantConfig.containsKey("employee")
                    if (hasDriverInfo && !merchantConfig["driver"].isNullOrEmpty() && !merchantConfig["employee"].isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Driver Plate
                            merchantConfig["driver"]?.let { value ->
                                HorizontalInfoCard(
                                    value = value,
                                    icon = iconMapping["driver"] ?: R.drawable.ic_car,
                                    iconColor = iconColorMapping["driver"] ?: Color.Gray,
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }

                            // Employee Info
                            merchantConfig["employee"]?.let { value ->
                                HorizontalInfoCard(
                                    value = value,
                                    icon = iconMapping["employee"] ?: R.drawable.ic_person,
                                    iconColor = iconColorMapping["employee"] ?: Color.Gray,
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Row 2: Terminal Info in 3 columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // TID
                        merchantConfig["tid"]?.let { value ->
                            CompactInfoCard(
                                label = fieldMapping["tid"] ?: "TID",
                                value = value,
                                icon = iconMapping["tid"] ?: R.drawable.ic_terminal,
                                iconColor = iconColorMapping["tid"] ?: Color.Gray,
                                modifier = Modifier.weight(1f),
                                isP2 = isP2
                            )
                        }

                        // MID
                        merchantConfig["mid"]?.let { value ->
                            CompactInfoCard(
                                label = fieldMapping["mid"] ?: "MID",
                                value = value,
                                icon = iconMapping["mid"] ?: R.drawable.ic_merchant,
                                iconColor = iconColorMapping["mid"] ?: Color.Gray,
                                modifier = Modifier.weight(1f),
                                isP2 = isP2
                            )
                        }

                        // Provider (Bank)
                        merchantConfig["provider"]?.let { value ->
                            CompactInfoCard(
                                label = fieldMapping["provider"] ?: "Ngân hàng",
                                value = value,
                                icon = iconMapping["provider"] ?: R.drawable.ic_bank,
                                iconColor = iconColorMapping["provider"] ?: Color.Gray,
                                modifier = Modifier.weight(1f),
                                isP2 = isP2
                            )
                        }
                    }

                    // Row 3: Merchant Company & Account Info (if provided)
                    if (!merchantCompany.isNullOrEmpty() || (!accountName.isNullOrEmpty() && !accountNumber.isNullOrEmpty())) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Merchant Company
                            if (!merchantCompany.isNullOrEmpty()) {
                                FullWidthInfoCard(
                                    label = "Công ty",
                                    value = merchantCompany,
                                    icon = iconMapping["company"] ?: R.drawable.ic_merchant,
                                    iconColor = iconColorMapping["company"] ?: Color.Gray,
                                    isP2 = isP2
                                )
                            }

                            // Account Name & Number
                            if (!accountName.isNullOrEmpty() && !accountNumber.isNullOrEmpty()) {
                                FullWidthInfoCard(
                                    label = "Tài khoản",
                                    value = "${accountName.uppercase()}\n$accountNumber",
                                    icon = iconMapping["account"] ?: R.drawable.ic_bank,
                                    iconColor = iconColorMapping["account"] ?: Color.Gray,
                                    isP2 = isP2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullWidthInfoCard(
    label: String,
    value: String,
    icon: Int,
    iconColor: Color,
    isP2: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with rounded background
            Box(
                modifier = Modifier
                    .size(if (isP2) 32.dp else 36.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = label,
                    modifier = Modifier.size(if (isP2) 16.dp else 18.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Label & Value
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = if (isP2) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = if (isP2) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    lineHeight = if (isP2) 14.sp else 16.sp
                )
            }
        }
    }
}

// Horizontal card for driver & employee
@Composable
private fun HorizontalInfoCard(
    value: String,
    icon: Int,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isP2: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isP2) 24.dp else 28.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = value,
                    modifier = Modifier.size(if (isP2) 12.dp else 14.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = value,
                fontSize = if (isP2) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Compact card (same as before)
@Composable
private fun CompactInfoCard(
    label: String,
    value: String,
    icon: Int,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isP2: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp, 8.dp, 4.dp, 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(if (isP2) 24.dp else 24.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = label,
                    modifier = Modifier.size(if (isP2) 12.dp else 14.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.height(if (isP2) 3.dp else 4.dp))

            Text(
                text = value,
                fontSize = if (isP2) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}