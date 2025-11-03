package com.onefin.posapp.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import com.google.gson.Gson
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.R
import com.onefin.posapp.core.config.LanguageConstants
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.LocaleHelper
import com.onefin.posapp.core.utils.NavigationHelper
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    apiService: ApiService,
    onCloseDrawer: () -> Unit,
    localeHelper: LocaleHelper,
    storageService: StorageService,
) {
    val context = LocalContext.current
    val activity = context as? Activity  // ✅ Lấy Activity instance
    var showLogoutDialog by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf<Account?>(null) }
    var currentLanguage by remember { mutableStateOf(localeHelper.getLanguage()) }
    val scope = rememberCoroutineScope()

    // Load account from storage
    LaunchedEffect(Unit) {
        account = storageService.getAccount()
        currentLanguage = localeHelper.getLanguage()
    }

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
                    account = account!!,
                    onLogoutClick = { showLogoutDialog = true }
                )
                BalanceDisplay(
                    apiService = apiService
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

            // Language Toggle Section
            LanguageToggleSection(
                currentLanguage = currentLanguage,
                onLanguageChange = { newLanguage ->
                    currentLanguage = newLanguage
                    localeHelper.setLocale(context, newLanguage)
                    activity?.recreate()
                }
            )

            HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))
            Spacer(modifier = Modifier.height(12.dp))

            // Menu Items
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
                    // NavigationHelper.navigateToBillPayment(context)
                    // onCloseDrawer()
                },
                enabled = false
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
                            NavigationHelper.navigateToLogin(context)
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
private fun LanguageToggleSection(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (currentLanguage == LanguageConstants.VIETNAMESE)
                        stringResource(R.string.language_vietnamese)
                    else
                        stringResource(R.string.language_english),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF166534)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "VI",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentLanguage == LanguageConstants.VIETNAMESE)
                        Color(0xFF16A34A) else Color(0xFF3B82F6)
                )

                Switch(
                    checked = currentLanguage == LanguageConstants.ENGLISH,
                    onCheckedChange = { isEnglish ->
                        val newLanguage = if (isEnglish)
                            LanguageConstants.ENGLISH
                        else
                            LanguageConstants.VIETNAMESE
                        onLanguageChange(newLanguage)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF16A34A),
                        checkedTrackColor = Color(0xFF16A34A).copy(alpha = 0.5f),
                        uncheckedThumbColor = Color(0xFF16A34A),
                        uncheckedTrackColor = Color(0xFF16A34A).copy(alpha = 0.5f)
                    )
                )

                Text(
                    text = "EN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentLanguage == LanguageConstants.ENGLISH)
                        Color(0xFF16A34A) else Color(0xFF3B82F6)
                )
            }
        }
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

        // Name and email
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

        // Logout icon
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