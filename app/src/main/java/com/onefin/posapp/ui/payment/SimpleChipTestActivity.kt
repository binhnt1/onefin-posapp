package com.onefin.posapp.ui.payment

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.onefin.posapp.R
import com.onefin.posapp.core.managers.CardProcessorManager
import com.onefin.posapp.core.managers.NfcPhoneReaderManager
import com.onefin.posapp.core.models.data.DeviceType
import com.onefin.posapp.core.models.data.PaymentAppRequest
import com.onefin.posapp.core.models.data.PaymentResult
import com.onefin.posapp.core.models.data.RequestSale
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.sunmi.pay.hardware.aidl.AidlConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SimpleChipTestActivity : BaseActivity() {

    @Inject
    lateinit var cardProcessorManager: CardProcessorManager

    @Inject
    lateinit var nfcPhoneReaderManager: NfcPhoneReaderManager

    private var paymentAppRequest: PaymentAppRequest? = null
    private val gson = Gson()

    private val statusLog = mutableStateListOf("Đang khởi tạo...")
    private var isReady by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private var successResult by mutableStateOf<RequestSale?>(null)

    private var icEnabled by mutableStateOf(true)
    private var nfcEnabled by mutableStateOf(true)
    private var magEnabled by mutableStateOf(true)

    // 🔥 Device type detection
    private var deviceType by mutableStateOf(DeviceType.ANDROID_PHONE)

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 Detect device type
        deviceType = detectDeviceType()
        Timber.tag("DeviceDetect").d("📱 Device type: $deviceType")

        paymentAppRequest = getPaymentAppRequest()

        // 🔥 Nếu là Phone, chỉ enable NFC
        if (deviceType == DeviceType.ANDROID_PHONE) {
            icEnabled = false
            nfcEnabled = true
            magEnabled = false
            addLog("📱 Thiết bị: Android Phone - Chỉ hỗ trợ NFC")
        } else {
            addLog("🖥️ Thiết bị: Sunmi POS - Hỗ trợ đầy đủ")
        }

        initializeManager()

        setContent {
            SimpleChipTestScreen(
                deviceType = deviceType,
                statusLog = statusLog,
                isReady = isReady,
                isProcessing = isProcessing,
                successResult = successResult,
                amount = paymentAppRequest?.merchantRequestData?.amount ?: 0,
                icEnabled = icEnabled,
                nfcEnabled = nfcEnabled,
                magEnabled = magEnabled,
                onIcChange = { icEnabled = it },
                onNfcChange = { nfcEnabled = it },
                onMagChange = { magEnabled = it },
                onStartClick = { startCardProcessing() },
                onCancelClick = { cancelCardProcessing() },
                onClearResult = { successResult = null }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.tag("PaymentActivity").d("🔥 onResume called")
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.enableForegroundDispatch(this)
            Timber.tag("PaymentActivity").d("📱 Foreground dispatch enabled")
        }
    }

    override fun onPause() {
        super.onPause()
        if (deviceType == DeviceType.ANDROID_PHONE) {
            nfcPhoneReaderManager.disableForegroundDispatch(this)
            Timber.tag("NfcPhone").d("📱 Foreground dispatch disabled")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.tag("NfcDebug").d("🔥 onNewIntent called")
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                nfcPhoneReaderManager.handleNfcIntent(tag)
            }
            else -> Timber.tag("NfcDebug").w("⚠️ Unknown action: ${intent.action}")
        }
    }

    /**
     * Detect device type: Sunmi POS hoặc Android Phone
     */
    private fun detectDeviceType(): DeviceType {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()

            val isSunmi = manufacturer.contains("sunmi") ||
                    model.contains("p2") ||
                    model.contains("v2") ||
                    model.contains("p1")

            if (isSunmi) {
                Timber.tag("DeviceDetect").d("✅ Detected Sunmi POS device")
                DeviceType.SUNMI_P3
            } else {
                Timber.tag("DeviceDetect").d("✅ Detected Android Phone")
                DeviceType.ANDROID_PHONE
            }
        } catch (e: Exception) {
            Timber.tag("DeviceDetect").e(e, "❌ Error detecting device, defaulting to ANDROID_PHONE")
            DeviceType.ANDROID_PHONE
        }
    }

    private fun initializeManager() {
        activityScope.launch {
            try {
                addLog("Đang khởi tạo Payment System...")

                when (deviceType) {
                    DeviceType.SUNMI_P2,
                    DeviceType.SUNMI_P3 -> {
                        cardProcessorManager.initialize { success, error ->
                            if (success) {
                                addLog("✅ Sunmi POS Payment System đã sẵn sàng")
                                isReady = true
                            } else {
                                addLog("❌ Khởi tạo Payment System thất bại")
                                addLog(error ?: "Vui lòng khởi động lại ứng dụng")
                            }
                        }
                    }
                    DeviceType.ANDROID_PHONE -> {
                        nfcPhoneReaderManager.initialize { success, error ->
                            if (success) {
                                addLog("✅ Android NFC Payment System đã sẵn sàng")
                                isReady = true
                            } else {
                                addLog("❌ Khởi tạo NFC thất bại")
                                addLog(error ?: "Vui lòng bật NFC và khởi động lại")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                addLog("LỖI: Không thể khởi tạo hệ thống: ${e.message}")
            }
        }
    }

    private fun startCardProcessing() {
        if (!isReady) {
            addLog("LỖI: Hệ thống chưa sẵn sàng.")
            return
        }
        if (paymentAppRequest == null) {
            addLog("LỖI: PaymentAppRequest không hợp lệ.")
            return
        }
        if (isProcessing) {
            addLog("INFO: Đang xử lý, vui lòng chờ...")
            return
        }

        successResult = null
        isProcessing = true
        statusLog.clear()
        addLog("--- BẮT ĐẦU QUY TRÌNH THANH TOÁN ---")

        when (deviceType) {
            DeviceType.SUNMI_P2,
            DeviceType.SUNMI_P3 -> {
                val selectedCardTypes = buildList {
                    if (icEnabled) add(AidlConstants.CardType.IC)
                    if (nfcEnabled) {
                        add(AidlConstants.CardType.NFC)
                        add(AidlConstants.CardType.MIFARE)
                    }
                    if (magEnabled) add(AidlConstants.CardType.MAGNETIC)
                }

                if (selectedCardTypes.isEmpty()) {
                    addLog("LỖI: Vui lòng chọn ít nhất một loại thẻ")
                    isProcessing = false
                    return
                }

                val cardTypesText = buildList {
                    if (icEnabled) add("CHIP")
                    if (nfcEnabled) add("NFC")
                    if (magEnabled) add("MAG")
                }.joinToString(" / ")

                addLog("Loại thẻ: $cardTypesText")
                addLog("Vui lòng đưa thẻ...")

                try {
                    cardProcessorManager.startPayment(
                        cardTypes = selectedCardTypes,
                        paymentRequest = paymentAppRequest!!,
                        onProcessingComplete = { result ->
                            handlePaymentResult(result)
                        }
                    )
                } catch (e: Exception) {
                    addLog("LỖI NGHIÊM TRỌNG: ${e.message}")
                    isProcessing = false
                }
            }

            DeviceType.ANDROID_PHONE -> {
                addLog("Loại thẻ: NFC (Contactless)")
                addLog("Vui lòng chạm thẻ vào mặt sau điện thoại...")

                try {
                    nfcPhoneReaderManager.startPayment(
                        paymentRequest = paymentAppRequest!!,
                        onProcessingComplete = { result ->
                            handlePaymentResult(result)
                        }
                    )
                } catch (e: Exception) {
                    addLog("LỖI NGHIÊM TRỌNG: ${e.message}")
                    isProcessing = false
                }
            }
        }
    }

    private fun handlePaymentResult(result: PaymentResult) {
        activityScope.launch {
            isProcessing = false

            when (result) {
                is PaymentResult.Success -> {
                    val requestSale = result.requestSale
                    successResult = requestSale
                    val cardMode = requestSale.data.card.mode
                    addLog("✅ GIAO DỊCH THÀNH CÔNG")
                    addLog("   Loại thẻ: $cardMode")
                    addLog("   PAN: ****${requestSale.data.card.clearPan.takeLast(4)}")
                    addLog("   Expiry: ${requestSale.data.card.expiryDate}")
                    addLog("   Brand: ${requestSale.data.card.type}")
                    addLog("   Bank: ${requestSale.data.card.issuerName}")
                    addLog("   Holder Name: ${requestSale.data.card.holderName}")

                    if (cardMode == "CHIP" || cardMode == "NFC") {
                        addLog("   EMV Data Length: ${requestSale.data.card.emvData?.length ?: 0} chars")
                    }

                    // 🔥 Nếu là Phone, tự động hoàn thành
                    if (deviceType == DeviceType.ANDROID_PHONE) {
                        addLog("📱 Đọc thẻ thành công trên điện thoại")
                        addLog("--- KẾT THÚC ---")
                    } else {
                        addLog("--- KẾT THÚC ---")
                    }
                }
                is PaymentResult.Error -> {
                    successResult = null
                    addLog("❌ GIAO DỊCH THẤT BẠI")
                    addLog("   Lỗi: ${result.type}")
                    addLog("   ${result.vietnameseMessage}")
                    addLog("   Chi tiết: ${result.technicalMessage}")
                    if (result.errorCode != null) {
                        addLog("   Mã lỗi SDK: ${result.errorCode}")
                    }
                    addLog("--- KẾT THÚC ---")
                }
            }
        }
    }

    private fun cancelCardProcessing() {
        if (!isProcessing) {
            addLog("INFO: Không có giao dịch nào đang chạy để hủy.")
            return
        }
        addLog("--- YÊU CẦU HỦY GIAO DỊCH ---")
        try {
            when (deviceType) {
                DeviceType.SUNMI_P2,
                DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
                DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
            }
            isProcessing = false
            addLog("✅ Đã hủy giao dịch")
        } catch (e: Exception) {
            addLog("LỖI: Hủy giao dịch thất bại: ${e.message}")
        }
    }

    private fun addLog(message: String) {
        activityScope.launch {
            statusLog.add(0, message)
            if (statusLog.size > 100) {
                statusLog.removeAt(statusLog.lastIndex)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isProcessing) {
            when (deviceType) {
                DeviceType.SUNMI_P2,
                DeviceType.SUNMI_P3 -> cardProcessorManager.cancelPayment()
                DeviceType.ANDROID_PHONE -> nfcPhoneReaderManager.cancelPayment()
            }
        }
    }
}

@Composable
fun SimpleChipTestScreen(
    deviceType: DeviceType,
    statusLog: List<String>,
    isReady: Boolean,
    isProcessing: Boolean,
    successResult: RequestSale?,
    amount: Long,
    icEnabled: Boolean,
    nfcEnabled: Boolean,
    magEnabled: Boolean,
    onIcChange: (Boolean) -> Unit,
    onNfcChange: (Boolean) -> Unit,
    onMagChange: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onClearResult: () -> Unit
) {
    val hasAtLeastOneSelected = icEnabled || nfcEnabled || magEnabled

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Test Giao dịch Thẻ", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Số tiền: ${UtilHelper.formatCurrency(amount, "đ")}")
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Loại thẻ cho phép:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 🔥 Chỉ hiển thị option phù hợp với device type
                    when (deviceType) {
                        DeviceType.ANDROID_PHONE -> {
                            // Phone: Chỉ hiển thị NFC (checked, disabled)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = null,
                                    enabled = false
                                )
                                Text("NFC (Contactless)", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        DeviceType.SUNMI_P2,
                        DeviceType.SUNMI_P3 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = icEnabled,
                                    onCheckedChange = onIcChange,
                                    enabled = !isProcessing
                                )
                                Text("IC (CHIP)", modifier = Modifier.padding(start = 8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = nfcEnabled,
                                    onCheckedChange = onNfcChange,
                                    enabled = !isProcessing
                                )
                                Text("NFC (Contactless)", modifier = Modifier.padding(start = 8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = magEnabled,
                                    onCheckedChange = onMagChange,
                                    enabled = !isProcessing
                                )
                                Text("MAG (Magnetic Stripe)", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onStartClick,
                    enabled = isReady && !isProcessing && hasAtLeastOneSelected
                ) {
                    Text("Bắt đầu Thanh toán")
                }
                Button(onClick = onCancelClick, enabled = isProcessing) {
                    Text("Hủy")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (successResult != null) {
                CreditCardDisplay(
                    requestSale = successResult,
                    onClear = onClearResult
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Trạng thái / Logs:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                statusLog.forEach { logMsg ->
                    Text(logMsg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun CreditCardDisplay(
    requestSale: RequestSale,
    onClear: () -> Unit
) {
    val cardData = requestSale.data.card
    val cardType = cardData.type?.lowercase()
    val cardMode = cardData.mode
    val cardNumber = cardData.clearPan
    val expiryDate = cardData.expiryDate
    val cardHolderName = cardData.holderName ?: "CARDHOLDER"
    val bankName = cardData.issuerName

    val cardIconRes = when {
        cardType?.contains("visa") == true -> R.drawable.icon_visa
        cardType?.contains("master") == true -> R.drawable.icon_master
        cardType?.contains("napas") == true -> R.drawable.icon_napas
        cardType?.contains("jcb") == true -> R.drawable.icon_jcb
        cardType?.contains("amex") == true || cardType?.contains("american") == true -> R.drawable.icon_card
        cardType?.contains("union") == true -> R.drawable.icon_card
        else -> R.drawable.icon_card
    }

    val modeText = when (cardMode?.uppercase()) {
        "CHIP", "IC" -> "CHIP"
        "NFC", "CONTACTLESS" -> "CONTACTLESS"
        "MAG", "MAGNETIC" -> "SWIPE"
        else -> cardMode
    }

    val gradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF1e3c72),
            Color(0xFF2a5298)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Image(
                        painter = painterResource(id = cardIconRes),
                        contentDescription = "Card Logo",
                        modifier = Modifier
                            .height(40.dp)
                            .width(60.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = modeText ?: "",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatCardNumber(cardNumber),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "CARDHOLDER",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = cardHolderName.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        if (!bankName.isNullOrEmpty()) {
                            Text(
                                text = bankName.uppercase(),
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "EXPIRES",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatExpiry(expiryDate),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "Clear",
                    tint = Color.White
                )
            }
        }
    }
}

private fun formatCardNumber(pan: String): String {
    return if (pan.length >= 16) {
        val masked = "**** **** **** ${pan.takeLast(4)}"
        masked
    } else {
        pan.chunked(4).joinToString(" ")
    }
}

private fun formatExpiry(expiry: String): String {
    return if (expiry.length == 4) {
        "${expiry.substring(0, 2)}/${expiry.substring(2)}"
    } else {
        expiry
    }
}