package com.onefin.posapp.ui.billpayment

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.base.BaseScreen
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BillPaymentActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                BillPaymentScreen(
                    storageService = storageService
                )
            }
        }
    }
}

@Composable
fun BillPaymentScreen(
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
                text = "HELLO BillPayment",
                fontSize = 24.sp,
                color = Color.Black
            )
        }
    }
}