package com.onefin.posapp.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.VietQRHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.base.BaseScreen
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.home.components.AmountEntrySheet
import com.onefin.posapp.core.utils.PaymentHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.onefin.posapp.core.models.Account
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

@AndroidEntryPoint
class HomeActivity : BaseActivity() {

    @Inject
    lateinit var storageService: StorageService

    @Inject
    lateinit var paymentHelper: PaymentHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                HomeScreen(
                    storageService = storageService,
                    paymentHelper = paymentHelper,
                    onLogout = {
                        val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    storageService: StorageService,
    paymentHelper: PaymentHelper,
    onLogout: () -> Unit
) {
    BaseScreen(
        storageService = storageService,
        onLogout = onLogout
    ) { paddingValues, account ->
        if (account != null) {
            HomeContent(
                account = account,
                paymentHelper = paymentHelper,
                storageService = storageService,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.home_error_load_account),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun HomeContent(
    account: Account,
    paymentHelper: PaymentHelper,
    storageService: StorageService,
    modifier: Modifier = Modifier
) {
    val vietQRString = remember(account) {
        VietQRHelper.buildVietQRString(
            bankNapasId = account.terminal.bankNapasId ?: "",
            accountNumber = account.terminal.accountNumber ?: ""
        )
    }

    var showAmountSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF9FAFB),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, top = 24.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_small),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.vietqr_logo),
                            contentDescription = "VietQR Logo",
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // QR Code
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val qrSize = screenWidth - (5.dp * 2) - (5.dp * 2)

                    QRCodeImage(
                        data = vietQRString,
                        size = qrSize
                    )

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = Color(0xFFEAECF0)
                    )

                    // Account name
                    Text(
                        text = account.terminal.accountName?.uppercase() ?: "",
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF101828)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Account number
                    Text(
                        text = account.terminal.accountNumber ?: "",
                        fontSize = 16.sp,
                        color = Color(0xFF475467),
                        letterSpacing = 1.2.sp
                    )

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = Color(0xFFEAECF0)
                    )

                    // Button
                    Button(
                        onClick = { showAmountSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF12B76A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.home_enter_amount),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Amount Entry Sheet
    if (showAmountSheet) {
        AmountEntrySheet(
            paymentHelper = paymentHelper,
            storageService = storageService,
            onDismiss = { showAmountSheet = false }
        )
    }
}

@Composable
fun QRCodeImage(
    data: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(data) {
        generateQRCode(data, size.value.toInt())
    }

    qrBitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "QR Code",
            modifier = modifier.size(size)
        )
    }
}

fun generateQRCode(data: String, sizePx: Int): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            }
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        throw e
    }
}