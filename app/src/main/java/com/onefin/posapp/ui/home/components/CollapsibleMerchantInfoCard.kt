package com.onefin.posapp.ui.home.components

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.DeviceHelper

@Composable
fun CollapsibleMerchantInfoCard(
    merchantConfig: Map<String, String>,
    fieldMapping: Map<String, String>,
    deviceHelper: DeviceHelper,
    storageService: StorageService,
    merchantCompany: String? = null,
    accountName: String? = null,
    accountNumber: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isP2 = remember { Build.MODEL.lowercase().contains("p2") }
    var deviceModel by remember { mutableStateOf("") }
    var deviceSerial by remember { mutableStateOf("") }

    // Load device info
    LaunchedEffect(Unit) {
        deviceModel = deviceHelper.getDeviceModel()
        deviceSerial = storageService.getSerial() ?: ""
        if (deviceSerial.isEmpty()) {
            deviceSerial = deviceHelper.getDeviceSerial()
            if (deviceSerial.isNotEmpty()) {
                storageService.saveSerial(deviceSerial)
            }
        }
    }

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
        shape = RoundedCornerShape(12.dp),
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
                        text = "Thông tin thiết bị",
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
                        .padding(if (isP2) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Device Info
                    if (deviceModel.isNotEmpty() || deviceSerial.isNotEmpty()) {
                        SectionLabel(text = "THIẾT BỊ", isP2 = isP2)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Device Model
                            if (deviceModel.isNotEmpty()) {
                                DeviceInfoCard(
                                    label = "Tên thiết bị",
                                    value = deviceModel,
                                    backgroundColor = Color(0xFFF0F9FF),
                                    borderColor = Color(0xFFBFDBFE),
                                    iconBackgroundColor = Color(0xFFDBEAFE),
                                    iconColor = Color(0xFF3B82F6),
                                    isP2 = isP2
                                )
                            }

                            // Device Serial
                            if (deviceSerial.isNotEmpty()) {
                                DeviceInfoCard(
                                    label = "Serial Number",
                                    value = deviceSerial,
                                    backgroundColor = Color(0xFFF0FDF4),
                                    borderColor = Color(0xFFBBF7D0),
                                    iconBackgroundColor = Color(0xFFDCFCE7),
                                    iconColor = Color(0xFF10B981),
                                    isP2 = isP2,
                                    useHashIcon = true
                                )
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFE5E7EB),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Section 2: Terminal Info
                    val hasTerminalInfo = merchantConfig.containsKey("tid") ||
                            merchantConfig.containsKey("mid") ||
                            merchantConfig.containsKey("provider")

                    if (hasTerminalInfo) {
                        SectionLabel(text = "THÔNG TIN GIAO DỊCH", isP2 = isP2)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // TID
                            merchantConfig["tid"]?.let { value ->
                                CompactInfoCard(
                                    label = fieldMapping["tid"] ?: "TID",
                                    value = value,
                                    sublabel = "Terminal ID",
                                    icon = iconMapping["tid"] ?: R.drawable.ic_terminal,
                                    iconColor = iconColorMapping["tid"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFECFDF5),
                                    borderColor = Color(0xFFA7F3D0),
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }

                            // MID
                            merchantConfig["mid"]?.let { value ->
                                CompactInfoCard(
                                    label = fieldMapping["mid"] ?: "MID",
                                    value = value,
                                    sublabel = "Merchant ID",
                                    icon = iconMapping["mid"] ?: R.drawable.ic_merchant,
                                    iconColor = iconColorMapping["mid"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFFEF3C7),
                                    borderColor = Color(0xFFFDE68A),
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }

                            // Provider (Bank)
                            merchantConfig["provider"]?.let { value ->
                                CompactInfoCard(
                                    label = fieldMapping["provider"] ?: "Ngân hàng",
                                    value = value,
                                    sublabel = "Provider",
                                    icon = iconMapping["provider"] ?: R.drawable.ic_bank,
                                    iconColor = iconColorMapping["provider"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFFEE2E2),
                                    borderColor = Color(0xFFFECACA),
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2,
                                    imageUrl = merchantConfig["bankLogo"]
                                )
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFE5E7EB),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Section 3: Driver & Employee
                    val hasDriverInfo = !merchantConfig["driver"].isNullOrEmpty() &&
                            !merchantConfig["employee"].isNullOrEmpty()

                    if (hasDriverInfo) {
                        SectionLabel(text = "NHÂN VIÊN VẬN HÀNH", isP2 = isP2)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Driver Plate
                            merchantConfig["driver"]?.let { value ->
                                HorizontalInfoCard(
                                    label = "Biển số xe",
                                    value = value,
                                    icon = iconMapping["driver"] ?: R.drawable.ic_car,
                                    iconColor = iconColorMapping["driver"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFEEF2FF),
                                    borderColor = Color(0xFFC7D2FE),
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }

                            // Employee Info
                            merchantConfig["employee"]?.let { value ->
                                HorizontalInfoCard(
                                    label = "Nhân viên",
                                    value = value,
                                    icon = iconMapping["employee"] ?: R.drawable.ic_person,
                                    iconColor = iconColorMapping["employee"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFF3E8FF),
                                    borderColor = Color(0xFFE9D5FF),
                                    modifier = Modifier.weight(1f),
                                    isP2 = isP2
                                )
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFE5E7EB),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Section 4: Merchant Company & Account Info
                    if (!merchantCompany.isNullOrEmpty() ||
                        (!accountName.isNullOrEmpty() && !accountNumber.isNullOrEmpty())) {

                        SectionLabel(text = "THÔNG TIN CÔNG TY", isP2 = isP2)

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
                                    backgroundColor = Color(0xFFECFEFF),
                                    borderColor = Color(0xFFA5F3FC),
                                    isP2 = isP2
                                )
                            }

                            // Account Name & Number
                            if (!accountName.isNullOrEmpty() && !accountNumber.isNullOrEmpty()) {
                                FullWidthInfoCard(
                                    label = "Tài khoản nhận tiền",
                                    value = "${accountName.uppercase()}\n$accountNumber",
                                    icon = iconMapping["account"] ?: R.drawable.ic_bank,
                                    iconColor = iconColorMapping["account"] ?: Color.Gray,
                                    backgroundColor = Color(0xFFFCE7F3),
                                    borderColor = Color(0xFFFBCFE8),
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
private fun SectionLabel(
    text: String,
    isP2: Boolean
) {
    Text(
        text = text,
        fontSize = if (isP2) 11.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF6B7280),
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun DeviceInfoCard(
    label: String,
    value: String,
    backgroundColor: Color,
    borderColor: Color,
    iconBackgroundColor: Color,
    iconColor: Color,
    isP2: Boolean,
    useHashIcon: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with rounded background
            Box(
                modifier = Modifier
                    .size(if (isP2) 32.dp else 36.dp)
                    .background(
                        color = iconBackgroundColor,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (useHashIcon) {
                    Text(
                        text = "#",
                        fontSize = if (isP2) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = label,
                        modifier = Modifier.size(if (isP2) 16.dp else 18.dp),
                        tint = iconColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                    color = Color(0xFF111827)
                )
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
    backgroundColor: Color,
    borderColor: Color,
    isP2: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with rounded background
            Box(
                modifier = Modifier
                    .size(if (isP2) 44.dp else 50.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = label,
                    modifier = Modifier.size(if (isP2) 24.dp else 28.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

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

@Composable
private fun HorizontalInfoCard(
    label: String,
    value: String,
    icon: Int,
    iconColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    isP2: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 10.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(if (isP2) 28.dp else 32.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = value,
                    modifier = Modifier.size(if (isP2) 14.dp else 16.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                fontSize = if (isP2) 9.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                fontSize = if (isP2) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactInfoCard(
    label: String,
    value: String,
    sublabel: String,
    icon: Int,
    iconColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    isP2: Boolean,
    imageUrl: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isP2) 8.dp else 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isP2) 24.dp else 28.dp)
                        .background(
                            color = if (imageUrl.isNullOrEmpty()) iconColor.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = imageUrl,
                            contentDescription = label,
                            modifier = Modifier.size(if (isP2) 24.dp else 28.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = label,
                            modifier = Modifier.size(if (isP2) 12.dp else 14.dp),
                            tint = iconColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = label,
                    fontSize = if (isP2) 9.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = if (isP2) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = sublabel,
                fontSize = if (isP2) 8.sp else 9.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}