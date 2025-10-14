package com.onefin.posapp.ui.home

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.core.utils.VietQRHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.Serializable

@AndroidEntryPoint
class QRCodeDisplayActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawObject: java.io.Serializable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("REQUEST_DATA", Serializable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("REQUEST_DATA")
        }

        @Suppress("UNCHECKED_CAST")
        val requestData = rawObject as? PaymentAppRequest
        if (requestData != null) {
            val account = storageService.getAccount()
            if (account != null) {
                setContent {
                    PosAppTheme {
                        QRCodeDisplayScreen(
                            accountName = account.terminal.accountName,
                            bankNapasId = account.terminal.bankNapasId,
                            accountNumber = account.terminal.accountNumber,
                            amount = requestData.merchantRequestData.amount,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeDisplayScreen(
    amount: Long,
    accountName: String,
    accountNumber: String,
    bankNapasId: String
) {
    val context = LocalContext.current as Activity

    val vietQRString = remember {
        VietQRHelper.buildVietQRString(
            bankNapasId = bankNapasId,
            accountNumber = accountNumber,
            amount = amount,
            info = context.getString(R.string.qr_payment_description) // Sử dụng string resource
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { context.finish() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.qr_display_close_button), // Sử dụng string resource
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD)) // Thêm viền
                // shadowElevation = 4.dp // Bỏ đổ bóng
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ---- THAY ĐỔI 2: LOGO TO HƠN ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_small),
                            contentDescription = "Logo",
                            modifier = Modifier.height(40.dp), // Tăng chiều cao
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Image(
                            painter = painterResource(id = R.drawable.vietqr_logo),
                            contentDescription = "VietQR Logo",
                            modifier = Modifier.height(40.dp) // Tăng chiều cao
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val qrSize = screenWidth - (24.dp * 2) - (24.dp * 2) // Trừ đi padding của cha

                    QRCodeImage(
                        data = vietQRString,
                        size = qrSize // Sử dụng kích thước động
                    )



                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color(0xFFEAECF0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = accountName.uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF101828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = accountNumber,
                        fontSize = 16.sp,
                        color = Color(0xFF475467)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color(0xFFEAECF0)
                    )

                    // Instructions and Amount
                    Text(
                        text = stringResource(R.string.qr_display_scan_instruction),
                        fontSize = 14.sp,
                        color = Color(0xFF475467),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.qr_display_single_use_note),
                        fontSize = 14.sp,
                        color = Color(0xFF475467),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${UtilHelper.formatCurrency(amount.toString())}đ",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF101828)
                    )
                }
            }

            // Nút Hủy
            Button(
                onClick = { context.finish() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEF3F2),
                    contentColor = Color(0xFFD92D20)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.qr_display_cancel_transaction),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}