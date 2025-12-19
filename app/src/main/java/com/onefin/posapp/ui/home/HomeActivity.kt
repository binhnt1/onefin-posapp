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
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.MultiFormatWriter
import com.onefin.posapp.core.managers.SnackbarManager
import com.onefin.posapp.core.managers.TTSManager
import com.onefin.posapp.core.utils.LocaleHelper
import com.onefin.posapp.ui.components.GlobalSnackbarHost
import com.onefin.posapp.ui.login.LoginActivity
import com.onefin.posapp.ui.modals.AutoLoginDialog
import com.onefin.posapp.ui.modals.NoNetworkDialog
import com.onefin.posapp.ui.modals.QRSuccessNotificationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.onefin.posapp.core.models.entity.DriverInfoEntity
import com.onefin.posapp.core.utils.DeviceHelper
import com.onefin.posapp.ui.home.components.CollapsibleMerchantInfoCard

@AndroidEntryPoint
class HomeActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var ttsManager: TTSManager

    @Inject
    lateinit var deviceHelper: DeviceHelper

    @Inject
    lateinit var paymentHelper: PaymentHelper

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var cardProcessorManager: com.onefin.posapp.core.managers.CardProcessorManager

    @Inject
    lateinit var nfcPhoneReaderManager: com.onefin.posapp.core.managers.NfcPhoneReaderManager

    @Inject
    lateinit var qrNotificationManager: com.onefin.posapp.core.managers.QRNotificationManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rabbitMQManager.startAfterLogin()
        setContent {
            PosAppTheme {
                HomeScreen(
                    apiService = apiService,
                    ttsManager = ttsManager,
                    localeHelper = localeHelper,
                    deviceHelper = deviceHelper,
                    paymentHelper = paymentHelper,
                    storageService = storageService,
                    snackbarManager = snackbarManager,
                    cardProcessorManager = cardProcessorManager,
                    nfcPhoneReaderManager = nfcPhoneReaderManager,
                    qrNotificationManager = qrNotificationManager
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
    apiService: ApiService,
    ttsManager: TTSManager,
    localeHelper: LocaleHelper,
    deviceHelper: DeviceHelper,
    paymentHelper: PaymentHelper,
    storageService: StorageService,
    snackbarManager: SnackbarManager,
    cardProcessorManager: com.onefin.posapp.core.managers.CardProcessorManager,
    nfcPhoneReaderManager: com.onefin.posapp.core.managers.NfcPhoneReaderManager,
    qrNotificationManager: com.onefin.posapp.core.managers.QRNotificationManager
) {
    val context = LocalContext.current

    // Detect device type
    val deviceType = remember {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val isSunmi = manufacturer.contains("sunmi") ||
                model.contains("p2") || model.contains("v2") || model.contains("p1")
        if (isSunmi) "sunmi" else "phone"
    }

    // State management
    var isNetworkAvailable by remember { mutableStateOf(true) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(30) }
    var isAutoLoggingIn by remember { mutableStateOf(false) }
    var autoLoginAttempted by remember { mutableStateOf(false) }
    var driverInfo by remember { mutableStateOf<DriverInfoEntity?>(null) }

    var cachedAccount by remember { mutableStateOf<Account?>(null) }
    var screenAlpha by remember { mutableFloatStateOf(0f) }

    // EMV initialization states
    var isInitializingEMV by remember { mutableStateOf(false) }
    var emvInitialized by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val networkErrorMessage = stringResource(R.string.network_dialog_message)
    val networkRestoredMessage = stringResource(R.string.network_dialog_title)

    // Network check
    LaunchedEffect(Unit) {
        isNetworkAvailable = checkNetworkConnection(context)
        showNetworkDialog = !isNetworkAvailable
        if (!isNetworkAvailable) {
            ttsManager.speak(networkErrorMessage)
        }
    }

    // Network reconnect logic with 30s interval
    LaunchedEffect(showNetworkDialog) {
        while (showNetworkDialog && isActive) {
            countdown = 30
            repeat(30) {
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

    // Login logic
    LaunchedEffect(Unit) {
        if (!autoLoginAttempted) {
            autoLoginAttempted = true
            val currentAccount = storageService.getAccount()
            if (currentAccount != null) {
                cachedAccount = currentAccount
                return@LaunchedEffect
            }

            val appKey = BuildConfig.APP_KEY
            if (appKey.isEmpty()) {
                delay(100)
                navigateToLogin(context)
                return@LaunchedEffect
            }

            isAutoLoggingIn = true
            try {
                val success = performAppKeyLogin(
                    appKey = appKey,
                    apiService = apiService,
                    storageService = storageService
                )

                if (success) {
                    cachedAccount = storageService.getAccount()
                    cachedAccount?.let { account ->
                        try {
                            driverInfo = storageService.getDriverInfo()
                        } catch (e: Exception) {
                        }
                    }
                    delay(2000)
                    isAutoLoggingIn = false
                } else {
                    isAutoLoggingIn = false
                    delay(500)
                    navigateToLogin(context)
                }
            } catch (e: Exception) {
                isAutoLoggingIn = false
                delay(500)
                navigateToLogin(context)
            }
        }
    }

    // UI Rendering
    LaunchedEffect(cachedAccount) {
        if (cachedAccount != null) {
            screenAlpha = 0f
            delay(2000)
            screenAlpha = 1f
        }
    }

    // Auto-dismiss success message after 5 seconds
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            delay(5000)
            showSuccessMessage = false
        }
    }

    when {
        isAutoLoggingIn -> {
            AutoLoginDialog()
        }
        showNetworkDialog -> {
            NoNetworkDialog(countdown = countdown, onDismiss = { })
        }
        cachedAccount != null -> {
            Box(modifier = Modifier.graphicsLayer(alpha = screenAlpha)) {
                BaseScreen(
                    apiService = apiService,
                    localeHelper = localeHelper,
                    storageService = storageService
                ) { paddingValues: PaddingValues, _: Account? ->
                    HomeContent(
                        driverInfo = driverInfo,
                        account = cachedAccount!!,
                        deviceHelper = deviceHelper,
                        paymentHelper = paymentHelper,
                        storageService = storageService,
                        isNetworkAvailable = isNetworkAvailable,
                        isInitializingEMV = isInitializingEMV,
                        showSuccessMessage = showSuccessMessage,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                GlobalSnackbarHost(snackbarManager = snackbarManager)
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // QR Success Notification Dialog
    QRSuccessNotificationDialog(qrNotificationManager = qrNotificationManager)
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
    deviceHelper: DeviceHelper,
    isNetworkAvailable: Boolean,
    paymentHelper: PaymentHelper,
    modifier: Modifier = Modifier,
    storageService: StorageService,
    driverInfo: DriverInfoEntity? = null,
    isInitializingEMV: Boolean = false,
    showSuccessMessage: Boolean = false
) {

    val isP2 = remember {
        Build.MODEL.lowercase().contains("p2")
    }
    val logoHeight = if (isP2) 24.dp else 40.dp
    val cancelButtonHeight = if (isP2) 48.dp else 56.dp
    val vietQRString = remember(account) {
        VietQRHelper.buildVietQRString(
            bankId = account.terminal.bankNapasId,
            accountNumber = account.terminal.accountNumber
        )
    }

    // merchant card
    val context = LocalContext.current
    val merchantFieldMapping = remember {
        mapOf(
            "driver" to context.getString(R.string.merchant_config_driver),
            "employee" to context.getString(R.string.merchant_config_employee),
            "tid" to "TID",
            "mid" to "MID",
            "provider" to "Ngân hàng"
        )
    }
    val merchantConfig = remember(driverInfo, account) {
        buildMap {
            // Driver info
            if (driverInfo != null) {
                put("driver", driverInfo.mid)
                put("employee", if (driverInfo.employeeName != null) {
                    "${driverInfo.employeeCode} - ${driverInfo.employeeName}"
                } else {
                    driverInfo.employeeCode
                })
            } else {
                // Fallback to old config for driver/employee
                account.terminal.merchantConfig?.forEach { (key, value) ->
                    if (key == "driver" || key == "employee") {
                        put(key, value.toString())
                    }
                }
            }

            // Terminal info - always add
            put("tid", account.terminal.tid)
            put("mid", account.terminal.mid)
            put("provider", account.terminal.provider)
            put("bankLogo", account.terminal.bankLogo)
        }
    }

    // save driverInfo
    if (driverInfo == null) {
        storageService.clearDriverInfo()
        if (merchantConfig.containsKey("driver")) {
            storageService.saveDriverInfo(
                DriverInfoEntity(
                    tid = merchantConfig["tid"]!!,
                    mid = merchantConfig["mid"]!!,
                    serial = storageService.getSerial()!!,
                    driverNumber = merchantConfig["driver"]!!,
                    employeeCode = merchantConfig["employee"]!!,
                )
            )
        }
    } else {}

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
            // EMV Initialization Progress Bar
            if (isInitializingEMV) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Đang khởi tạo thiết bị...",
                            fontSize = 14.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Success Message
            if (showSuccessMessage) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFD1FAE5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_info),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF059669)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Thiết bị sẵn sàng cho giao dịch",
                            fontSize = 14.sp,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

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

                            if (account.terminal.image.isEmpty()) {
                                Image(
                                    painter = painterResource(id = R.drawable.vietqr_logo),
                                    contentDescription = "VietQR Logo",
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(logoHeight),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(if (isP2) 12.dp else 24.dp))

                        var qrSize = LocalConfiguration.current.screenWidthDp.dp - 64.dp
                        if (isP2) {
                            qrSize *= 0.9f
                        }

                        if (account.terminal.image.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = account.terminal.image,
                                contentDescription = "Custom Image",
                                modifier = Modifier.size(qrSize),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            QRCodeImage(
                                data = vietQRString,
                                size = qrSize
                            )
                        }

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

                            if (account.terminal.image.isNotEmpty()) {
                                Text(
                                    text = account.terminal.merchantCompany,
                                    textAlign = TextAlign.Center,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF101828)
                                )
                            } else {
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
                            }

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
                                shape = RoundedCornerShape(8.dp),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isNetworkAvailable) Color.White else Color(0xFF3B82F6)
                                )
                                Spacer(modifier = Modifier.width(if (isP2) 4.dp else 8.dp))
                                Text(
                                    text = stringResource(R.string.home_enter_amount),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNetworkAvailable) Color.White else Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }

            // Enhanced Merchant Info Card
            if (merchantConfig.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleMerchantInfoCard(
                    merchantConfig = merchantConfig as Map<String, String>,
                    fieldMapping = merchantFieldMapping,
                    merchantCompany = if (account.terminal.image.isNotEmpty())
                        account.terminal.merchantCompany else null,
                    accountName = if (account.terminal.image.isEmpty())
                        account.terminal.accountName else null,
                    accountNumber = if (account.terminal.image.isEmpty())
                        account.terminal.accountNumber else null,
                    modifier = Modifier.fillMaxWidth(),
                    storageService = storageService,
                    deviceHelper = deviceHelper,
                )
                Spacer(modifier = Modifier.height(16.dp))
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
        val bitMatrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
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