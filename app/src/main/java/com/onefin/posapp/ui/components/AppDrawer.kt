package com.onefin.posapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.R
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.NavigationHelper
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    account: Account?,
    storageService: StorageService,
    onCloseDrawer: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFFFAFAFA),
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DrawerHeader(onCloseClick = onCloseDrawer)
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))

            if (account != null) {
                ProfileSection(
                    account = account,
                    onLogoutClick = { showLogoutDialog = true }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))
            Spacer(modifier = Modifier.height(24.dp))

            // Menu Items - tự xử lý navigation
            DrawerMenuItem(
                icon = Icons.Default.Description,
                title = stringResource(R.string.menu_transactions),
                onClick = {
                    NavigationHelper.navigateToTransactions(context)
                    onCloseDrawer()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Receipt,
                title = stringResource(R.string.menu_settlement),
                onClick = {
                    NavigationHelper.navigateToSettlement(context)
                    onCloseDrawer()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.menu_history),
                onClick = {
                    NavigationHelper.navigateToHistory(context)
                    onCloseDrawer()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Payment,
                title = stringResource(R.string.menu_bill_payment),
                onClick = {
                    NavigationHelper.navigateToBillPayment(context)
                    onCloseDrawer()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.menu_report),
                onClick = {
                    NavigationHelper.navigateToReport(context)
                    onCloseDrawer()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.dialog_logout_title)) },
            text = { Text(stringResource(R.string.dialog_logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            storageService.clearAll()
                            onLogoutSuccess()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_logout), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileSection(
    account: Account,
    onLogoutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = account.terminal.logo,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Tên và email
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.terminal.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.email,
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }

        // Icon logout
        IconButton(onClick = onLogoutClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}