package com.onefin.posapp.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.BuildConfig
import com.onefin.posapp.core.models.Account
import com.onefin.posapp.R
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.services.StorageService
import com.onefin.posapp.core.utils.DeviceHelper
import com.onefin.posapp.core.utils.LocaleHelper
import com.onefin.posapp.core.utils.ValidationHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.home.HomeActivity
import com.onefin.posapp.ui.modals.AlertDialog
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var deviceHelper: DeviceHelper

    @Inject
    lateinit var validationHelper: ValidationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rabbitMQManager.stop()
        setContent {
            PosAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    LoginScreen(
                        apiService = apiService,
                        deviceHelper = deviceHelper,
                        storageService = storageService,
                        validationHelper = validationHelper,
                        localeHelper = localeHelper,
                        onLoginSuccess = {
                            val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        onLanguageChange = { language ->
                            localeHelper.setLocale(this, language)
                            recreate()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    apiService: ApiService,
    deviceHelper: DeviceHelper,
    storageService: StorageService,
    validationHelper: ValidationHelper,
    localeHelper: LocaleHelper,
    onLoginSuccess: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    var currentLanguage by remember { mutableStateOf(localeHelper.getLanguage()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f)
        ) {
            StaticHeader(
                currentLanguage = currentLanguage,
                onLanguageChange = { language ->
                    currentLanguage = language
                    onLanguageChange(language)
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3.5f)
                .padding(horizontal = 24.dp)
        ) {
            LoginForm(
                apiService = apiService,
                deviceHelper = deviceHelper,
                storageService = storageService,
                validationHelper = validationHelper,
                onLoginSuccess = onLoginSuccess
            )
        }

        // Footer
        StaticFooter()
    }
}

@Composable
fun StaticHeader(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (currentLanguage == com.onefin.posapp.core.config.LanguageConstants.VIETNAMESE) "VI" else "EN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )

            Switch(
                checked = currentLanguage == com.onefin.posapp.core.config.LanguageConstants.ENGLISH,
                onCheckedChange = { isEnglish ->
                    val newLanguage = if (isEnglish)
                        com.onefin.posapp.core.config.LanguageConstants.ENGLISH
                    else
                        com.onefin.posapp.core.config.LanguageConstants.VIETNAMESE
                    onLanguageChange(newLanguage)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                )
            )
        }

        Image(
            painter = painterResource(id = R.drawable.mask),
            contentDescription = "Mask",
            modifier = Modifier
                .width(180.dp)
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.logo_small),
            contentDescription = "Logo",
            modifier = Modifier
                .padding(top = 10.dp)
                .width(200.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun StaticFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contact info
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("cskh@onefin.vn")
            Spacer(modifier = Modifier.width(12.dp))
            Text("|")
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Phone",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("1900 996 688")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // PCI DSS logo
        Image(
            painter = painterResource(id = R.drawable.pci_dss),
            contentDescription = "PCI DSS",
            modifier = Modifier.height(36.dp)
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LoginForm(
    apiService: ApiService,
    deviceHelper: DeviceHelper,
    storageService: StorageService,
    validationHelper: ValidationHelper,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf(BuildConfig.USERNAME) }
    var password by remember { mutableStateOf("A1a@a#a$") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var deviceModel by remember { mutableStateOf("") }
    var deviceSerial by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val isP2 = remember { deviceHelper.getDeviceType() == DeviceType.SUNMI_P2 }

    // Load device info
    LaunchedEffect(Unit) {
        deviceModel = deviceHelper.getDeviceModel()
        deviceSerial = storageService.getSerial() ?: ""
        if (deviceSerial.isEmpty()) {
            deviceSerial = deviceHelper.getDeviceSerial()
            if (!deviceSerial.isEmpty())
                storageService.saveSerial(deviceSerial)
        }

        // hard-code
        // deviceSerial = "P365257WJ0280"
        // storageService.saveSerial(deviceSerial)
    }

    // Clear error when typing
    LaunchedEffect(username) {
        if (emailError != null) emailError = null
    }
    LaunchedEffect(password) {
        if (passwordError != null) passwordError = null
    }

    // Show error dialog
    if (showErrorDialog) {
        AlertDialog(
            content = errorMessage,
            onDismiss = { showErrorDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isP2) {
            // Device Model
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = "Device",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.device_label, deviceModel),
                    color = Color(0xFF616161),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Serial
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = "Serial",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.serial_label, deviceSerial),
                    color = Color(0xFF616161),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Username Field
        Text(
            text = stringResource(R.string.label_username),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.hint_username)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email",
                    tint = Color.Gray
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Password Field
        Text(
            text = stringResource(R.string.label_password),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.hint_password)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = Color.Gray
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Login Button
        Button(
            onClick = {
                val emailValidation = validationHelper.validateEmail(username.trim())
                val passwordValidation = validationHelper.validatePassword(password.trim())

                if (emailValidation == null && passwordValidation == null) {
                    isLoading = true
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        try {
                            val body = mapOf(
                                "UserName" to username.trim(),
                                "Password" to password.trim()
                            )
                            val resultApi = apiService.post("/api/security/signIn", body) as ResultApi<*>

                            val account = com.google.gson.Gson().fromJson(
                                com.google.gson.Gson().toJson(resultApi.data),
                                Account::class.java
                            )

                            storageService.saveToken(account.token.accessToken)
                            storageService.saveAccount(account)

                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: context.getString(R.string.login_failed)
                            showErrorDialog = true
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    emailError = emailValidation
                    passwordError = passwordValidation
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.6f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.btn_login),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}