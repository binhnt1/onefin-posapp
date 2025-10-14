package com.onefin.posapp.ui.transaction

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import com.onefin.posapp.ui.base.BaseScreen
import javax.inject.Inject

@AndroidEntryPoint
class TransactionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                TransactionScreen(
                    storageService = storageService
                )
            }
        }
    }
}

@Composable
fun TransactionScreen(
    storageService: StorageService
) {
    BaseScreen(
        storageService = storageService
    ) { paddingValues, account ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "HELLO Transaction",
                fontSize = 24.sp,
                color = Color.Black
            )
        }
    }
}