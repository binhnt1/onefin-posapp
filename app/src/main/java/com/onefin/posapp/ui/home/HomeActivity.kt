package com.onefin.posapp.ui.home

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefin.posapp.R
import com.onefin.posapp.BuildConfig
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.VietQRHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.base.BaseScreen
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.home.components.AmountEntrySheet
import com.onefin.posapp.core.utils.PaymentHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.onefin.posapp.core.managers.SnackbarManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.utils.LocaleHelper
import com.onefin.posapp.ui.components.GlobalSnackbarHost
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.NoNetworkDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : BaseActivity() {

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var apiService: ApiService

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rabbitMQManager.startAfterLogin()
        setContent {
            PosAppTheme {
                HomeScreen(
                    ttsManager = ttsManager,
                    localeHelper = localeHelper,
                    paymentHelper = paymentHelper,
                    storageService = storageService,
                    apiService = apiService,
                )

                GlobalSnackbarHost(
                    snackbarManager = snackbarManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
    }
}

@Composable
fun HomeScreen(
    ttsManager: TTSManager,
    localeHelper: LocaleHelper,
    paymentHelper: PaymentHelper,
    storageService: StorageService,
    apiService: ApiService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isNetworkAvailable by remember { mutableStateOf(true) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(15) }
    var isAutoLoggingIn by remember { mutableStateOf(false) }
    var autoLoginAttempted by remember { mutableStateOf(false) }

    val networkErrorMessage = stringResource(R.string.network_dialog_message)
    val networkRestoredMessage = stringResource(R.string.network_dialog_title)

    LaunchedEffect(Unit) {
        isNetworkAvailable = checkNetworkConnection(context)
        showNetworkDialog = !isNetworkAvailable

        if (!isNetworkAvailable) {
            ttsManager.speak(networkErrorMessage)
        }
    }

    LaunchedEffect(showNetworkDialog) {
        while (showNetworkDialog && isActive) {
            countdown = 10
            repeat(10) {
                if (!isActive || !showNetworkDialog) return@LaunchedEffect
                delay(1000)
                countdown--
            }

            isNetworkAvailable = checkNetworkConnection(context)
            if (isNetworkAvailable) {
                showNetworkDialog = false
                ttsManager.speak(networkRestoredMessage)
            } else {
                ttsManager.speak(networkErrorMessage)
            }
        }
    }

    if (showNetworkDialog) {
        NoNetworkDialog(
            countdown = countdown,
            onDismiss = { }
        )
    }

    // Loading dialog cho auto login
    if (isAutoLoggingIn) {
        AutoLoginDialog()
    }

    // Kiểm tra login trước khi render BaseScreen
    LaunchedEffect(Unit) {
        if (!autoLoginAttempted) {
            autoLoginAttempted = true

            // Kiểm tra xem đã có account chưa
            val currentAccount = storageService.getAccount()

            if (currentAccount == null) {
                // Chưa đăng nhập, kiểm tra APP_KEY
                val appKey = BuildConfig.APP_KEY

                if (appKey.isNotEmpty()) {
                    // Có APP_KEY, thực hiện auto login
                    isAutoLoggingIn = true

                    scope.launch {
                        try {
                            val success = performAppKeyLogin(
                                appKey = appKey,
                                apiService = apiService,
                                storageService = storageService
                            )

                            if (success) {
                                // Đợi 5 giây trước khi đóng popup và reload
                                delay(5000)
                                isAutoLoggingIn = false

                                // Reload HomeActivity
                                val intent = Intent(context, HomeActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                context.startActivity(intent)
                            } else {
                                // Login thất bại, chuyển sang màn hình login
                                isAutoLoggingIn = false
                                navigateToLogin(context)
                            }
                        } catch (e: Exception) {
                            // Có lỗi xảy ra, chuyển sang màn hình login
                            isAutoLoggingIn = false
                            navigateToLogin(context)
                        }
                    }
                } else {
                    // Không có APP_KEY, chuyển sang màn hình login
                    navigateToLogin(context)
                }
            }
        }
    }

    // Chỉ hiển thị BaseScreen khi đã đăng nhập
    if (!isAutoLoggingIn && autoLoginAttempted) {
        BaseScreen(
            localeHelper = localeHelper,
            storageService = storageService
        ) { paddingValues: PaddingValues, account: Account? ->
            if (account != null) {
                HomeContent(
                    account = account,
                    paymentHelper = paymentHelper,
                    storageService = storageService,
                    isNetworkAvailable = isNetworkAvailable,
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                // Trường hợp account null sau khi đã login
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun AutoLoginDialog() {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.logo_small),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Loading indicator với background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color(0xFF12B76A).copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(50.dp),
                        color = Color(0xFF12B76A),
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = "Đang đăng nhập hệ thống...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101828),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Vui lòng đợi trong giây lát",
                    fontSize = 14.sp,
                    color = Color(0xFF667085),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

fun navigateToLogin(context: Context) {
    val intent = Intent(context, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
}

// Hàm thực hiện login bằng APP_KEY
suspend fun performAppKeyLogin(
    appKey: String,
    apiService: ApiService,
    storageService: StorageService
): Boolean {
    return try {
        // Gọi API AppSignIn
        val body = mapOf(
            "AppKey" to appKey
        )

        val resultApi = apiService.post("/api/security/AppSignIn", body) as ResultApi<*>

        // Parse account từ response
        val account = com.google.gson.Gson().fromJson(
            com.google.gson.Gson().toJson(resultApi.data),
            Account::class.java
        )

        // Lưu token và account
        storageService.saveToken(account.token.accessToken)
        storageService.saveAccount(account)

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@Composable
fun HomeContent(
    account: Account,
    paymentHelper: PaymentHelper,
    storageService: StorageService,
    isNetworkAvailable: Boolean,
    modifier: Modifier = Modifier
) {

    val isP2 = remember {
        Build.MODEL.lowercase().contains("p2")
    }
    val logoHeight = if (isP2) 24.dp else 40.dp
    val cancelButtonHeight = if (isP2) 48.dp else 56.dp
    val vietQRString = remember(account) {
        VietQRHelper.buildVietQRString(
            bankNapasId = account.terminal.bankNapasId,
            accountNumber = account.terminal.accountNumber
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF9FAFB),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0D5DD))
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                                    .height(logoHeight),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.width(if (isP2) 10.dp else 20.dp))

                            Image(
                                painter = painterResource(id = R.drawable.vietqr_logo),
                                contentDescription = "VietQR Logo",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(logoHeight),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isP2) 12.dp else 24.dp))

                        var qrSize = LocalConfiguration.current.screenWidthDp.dp - 64.dp
                        if (isP2) {
                            qrSize *= 0.9f
                        }

                        QRCodeImage(
                            data = vietQRString,
                            size = qrSize
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF9FAFB)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFEAECF0)
                            )
                            Spacer(modifier = Modifier.height(if (isP2) 6.dp else 12.dp))

                            Text(
                                text = account.terminal.accountName.uppercase(),
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF101828)
                            )
                            Spacer(modifier = Modifier.height(if (isP2) 4.dp else 8.dp))
                            Text(
                                text = account.terminal.accountNumber,
                                fontSize = 16.sp,
                                color = Color(0xFF475467),
                                letterSpacing = 1.2.sp
                            )

                            Spacer(modifier = Modifier.height(if (isP2) 6.dp else 12.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFEAECF0)
                            )
                            Spacer(modifier = Modifier.height(if (isP2) 6.dp else 12.dp))

                            Button(
                                onClick = {
                                    if (isNetworkAvailable) {
                                        showAmountSheet = true
                                    }
                                },
                                enabled = isNetworkAvailable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cancelButtonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF12B76A),
                                    disabledContainerColor = Color(0xFFD1D5DB)
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
                                    tint = if (isNetworkAvailable) Color.White else Color(0xFF9CA3AF)
                                )
                                Spacer(modifier = Modifier.width(if (isP2) 4.dp else 8.dp))
                                Text(
                                    text = stringResource(R.string.home_enter_amount),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNetworkAvailable) Color.White else Color(0xFF9CA3AF)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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

fun checkNetworkConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}