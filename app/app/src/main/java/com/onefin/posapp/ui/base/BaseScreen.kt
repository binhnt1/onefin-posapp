package com.onefin.posapp.ui.base

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.LocaleHelper
import com.onefin.posapp.ui.components.AppDrawer
import com.onefin.posapp.ui.components.AppTopBar
import kotlinx.coroutines.launch

@Composable
fun BaseScreen(
    localeHelper: LocaleHelper,
    storageService: StorageService,
    content: @Composable (PaddingValues, Account?) -> Unit
) {
    var account by remember { mutableStateOf<Account?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        account = storageService.getAccount()
        isLoading = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                },
                localeHelper = localeHelper,
                storageService = storageService,
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = account?.terminal?.name ?: "",
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            },
            containerColor = Color.White
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                content(paddingValues, account)
            }
        }
    }
}