package com.onefin.posapp.ui.settlement

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onefin.posapp.R
import com.onefin.posapp.core.models.DateRangeType
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.models.data.SettleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.modals.ProcessingDialog
import com.onefin.posapp.ui.report.components.DateRangeMenuItem
import com.onefin.posapp.ui.settlement.components.SettlementBottomBar
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.TransactionType
import com.onefin.posapp.ui.transaction.TransactionTypeDetailActivity
import com.onefin.posapp.ui.transaction.components.TransactionList
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettlementActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var printerHelper: PrinterHelper

    @Inject
    lateinit var receiptPrinter: ReceiptPrinter

    companion object {
        var shouldRefresh = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                SettlementScreen(
                    apiService = apiService,
                    printerHelper = printerHelper,
                    receiptPrinter = receiptPrinter,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

data class SettlementStatistic(
    val unsettledCount: Int = 0,
    val unsettledAmount: Long = 0L,
    val settledCount: Int = 0,
    val settledAmount: Long = 0L
)

@SuppressLint("TimberArgCount")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    apiService: ApiService,
    printerHelper: PrinterHelper,
    receiptPrinter: ReceiptPrinter,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var stats by remember { mutableStateOf(SettlementStatistic()) }
    var totalAmount by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSettling by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var showSettleDialog by remember { mutableStateOf(false) }

    var selectedDateRange by remember { mutableStateOf(DateRangeType.TODAY) }
    var showDropdown by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf(UtilHelper.getTodayDate().replace("/", "")) }
    var toDate by remember { mutableStateOf(UtilHelper.getTodayDate().replace("/", "")) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val customGreenColor = Color(0xFF10B981)
    val customRedColor = Color(0xFFEF4444)
    val customOrangeColor = Color(0xFFF59E0B)

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val gson = Gson()

                // Load transactions with status=0 (ALL transactions - both settled and unsettled)
                val transactionsEndpoint = "/api/transaction/items?status=0"
                val transactionsParams = mapOf("page" to 1, "limit" to 20, "fromDate" to fromDate, "toDate" to toDate)
                val transactionsResult = apiService.get(transactionsEndpoint, transactionsParams) as ResultApi<*>
                val jsonString = gson.toJson(transactionsResult.data)
                val type = object : com.google.gson.reflect.TypeToken<List<Transaction>>() {}.type
                transactions = gson.fromJson(jsonString, type) ?: emptyList()

                totalAmount = (transactionsResult.total as? Number)?.toLong() ?: 0L

                // Load unsettled stats (status=1 - Chưa kết toán)
                val unsettledResult = apiService.get(
                    "/api/transaction/items?status=1",
                    mapOf("page" to 1, "limit" to 1, "fromDate" to fromDate, "toDate" to toDate)
                ) as ResultApi<*>

                val unsettledAmount = (unsettledResult.total as? Number)?.toLong() ?: 0L
                val unsettledObjectExtra = unsettledResult.objectExtra as? Map<*, *>
                val unsettledPaging = unsettledObjectExtra?.get("Paging") as? Map<*, *>
                val unsettledCount = (unsettledPaging?.get("Total") as? Number)?.toInt() ?: 0

                // Load settled stats (status=2 - Đã kết toán)
                val settledResult = apiService.get(
                    "/api/transaction/items?status=2",
                    mapOf("page" to 1, "limit" to 1, "fromDate" to fromDate, "toDate" to toDate)
                ) as ResultApi<*>

                val settledAmount = (settledResult.total as? Number)?.toLong() ?: 0L
                val settledObjectExtra = settledResult.objectExtra as? Map<*, *>
                val settledPaging = settledObjectExtra?.get("Paging") as? Map<*, *>
                val settledCount = (settledPaging?.get("Total") as? Number)?.toInt() ?: 0

                stats = SettlementStatistic(
                    unsettledCount = unsettledCount,
                    unsettledAmount = unsettledAmount,
                    settledCount = settledCount,
                    settledAmount = settledAmount
                )

            } catch (e: Exception) {
                Timber.tag("SettlementActivity").e(e, "Error loading data: ${e.message}")
                errorMessage = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(fromDate, toDate) {
        loadData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount++
                if (SettlementActivity.shouldRefresh) {
                    SettlementActivity.shouldRefresh = false
                    scope.launch {
                        loadData()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ========== SETTLEMENT DIALOG ==========
    if (showSettleDialog) {
        val successMessage = context.getString(R.string.success_settlement)
        val unknownError = context.getString(R.string.error_unknown)
        val failedMessage = context.getString(R.string.error_settlement_failed)

        AlertDialog(
            onDismissRequest = {
                if (!isSettling && !isPrinting) {
                    showSettleDialog = false
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = customGreenColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        context.getString(R.string.dialog_settlement_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    context.getString(R.string.dialog_settlement_message),
                    fontSize = 15.sp,
                    color = Color(0xFF374151)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettleDialog = false
                        scope.launch {
                            val gson = Gson()
                            isSettling = true
                            var settleData: SettleResultData? = null

                            try {
                                val requestId = UtilHelper.generateRequestId()
                                val endpoint = "/api/card/settle"
                                val body = mapOf(
                                    "requestId" to requestId,
                                    "data" to mapOf("autoBatchUpload" to true)
                                )

                                val resultApi = apiService.post(endpoint, body) as ResultApi<*>
                                val jsonString = gson.toJson(resultApi.data)
                                settleData = gson.fromJson(jsonString, SettleResultData::class.java)
                                if (settleData?.isSuccess() == true) {
                                    isSettling = false
                                    snackbarHostState.showSnackbar(successMessage)
                                    loadData()
                                } else {
                                    isSettling = false
                                    snackbarHostState.showSnackbar(
                                        failedMessage.format(settleData?.status?.message ?: "Unknown")
                                    )
                                }
                            } catch (e: Exception) {
                                isSettling = false
                                snackbarHostState.showSnackbar(
                                    failedMessage.format(e.message ?: unknownError)
                                )
                            }

                            if (settleData?.isSuccess() == true) {
                                try {
                                    isPrinting = true
                                    val printResult = receiptPrinter.printSettlementReceipt(settleData)
                                    if (printResult.isSuccess) {
                                        snackbarHostState.showSnackbar("✅ Đã in đối soát")
                                    } else {0
                                        snackbarHostState.showSnackbar(
                                            "⚠️ Đối soát thành công nhưng không thể in"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Print error")
                                    snackbarHostState.showSnackbar("❌ Lỗi in: ${e.message}")
                                } finally {
                                    isPrinting = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customGreenColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp),
                    enabled = !isSettling && !isPrinting
                ) {
                    Text(
                        context.getString(R.string.btn_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSettleDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp),
                    enabled = !isSettling && !isPrinting
                ) {
                    Text(
                        context.getString(R.string.btn_dismiss),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (isSettling || isPrinting) {
        ProcessingDialog()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .shadow(8.dp, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (data.visuals.message.contains("thành công") ||
                                data.visuals.message.contains("✅"))
                                customGreenColor
                            else
                                customRedColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (data.visuals.message.contains("thành công") ||
                                    data.visuals.message.contains("✅"))
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = data.visuals.message,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            )
        },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            context.getString(R.string.menu_settlement),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF111827)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF111827)
                    )
                )
            }
        },
        bottomBar = {
            // Chỉ hiện bottom bar khi có transactions (status=0)
            if (!isLoading && transactions.isNotEmpty()) {
                SettlementBottomBar(
                    isSettling = isSettling || isPrinting,
                    totalAmount = totalAmount,
                    onSettleClick = { showSettleDialog = true }
                )
            }
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = customGreenColor)
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 48.sp)
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 14.sp,
                            color = customRedColor
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9FAFB))
                ) {
                    // Date Range Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clickable { showDropdown = true }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = customGreenColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = when (selectedDateRange) {
                                            DateRangeType.TODAY -> context.getString(R.string.date_range_today)
                                            DateRangeType.YESTERDAY -> context.getString(R.string.date_range_yesterday)
                                            DateRangeType.LAST_7_DAYS -> context.getString(R.string.date_range_last_7_days)
                                            DateRangeType.LAST_30_DAYS -> context.getString(R.string.date_range_last_30_days)
                                            else -> "$fromDate - $toDate"
                                        },
                                        fontSize = 16.sp,
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF6B7280),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false },
                                    offset = DpOffset(0.dp, 8.dp)
                                ) {
                                    DateRangeMenuItem(
                                        text = context.getString(R.string.date_range_today),
                                        isSelected = selectedDateRange == DateRangeType.TODAY,
                                        onClick = {
                                            selectedDateRange = DateRangeType.TODAY
                                            fromDate = UtilHelper.getTodayDate().replace("/", "")
                                            toDate = UtilHelper.getTodayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = context.getString(R.string.date_range_yesterday),
                                        isSelected = selectedDateRange == DateRangeType.YESTERDAY,
                                        onClick = {
                                            selectedDateRange = DateRangeType.YESTERDAY
                                            fromDate = UtilHelper.getYesterdayDate().replace("/", "")
                                            toDate = UtilHelper.getYesterdayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = context.getString(R.string.date_range_last_7_days),
                                        isSelected = selectedDateRange == DateRangeType.LAST_7_DAYS,
                                        onClick = {
                                            selectedDateRange = DateRangeType.LAST_7_DAYS
                                            fromDate = UtilHelper.getDaysAgo(7).replace("/", "")
                                            toDate = UtilHelper.getTodayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = context.getString(R.string.date_range_last_30_days),
                                        isSelected = selectedDateRange == DateRangeType.LAST_30_DAYS,
                                        onClick = {
                                            selectedDateRange = DateRangeType.LAST_30_DAYS
                                            fromDate = UtilHelper.getDaysAgo(30).replace("/", "")
                                            toDate = UtilHelper.getTodayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Settlement Stats Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettlementTypeCard(
                                icon = "⏳",
                                title = "Chưa kết toán",
                                count = stats.unsettledCount,
                                amount = stats.unsettledAmount,
                                borderColor = customOrangeColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Mở TransactionTypeDetailActivity với status=1
                                    TransactionTypeDetailActivity.start(context, TransactionType.UNSETTLED)
                                }
                            )
                            SettlementTypeCard(
                                icon = "✅",
                                title = "Đã kết toán",
                                count = stats.settledCount,
                                amount = stats.settledAmount,
                                borderColor = customGreenColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Mở TransactionTypeDetailActivity với status=2
                                    TransactionTypeDetailActivity.start(context, TransactionType.SETTLED)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Recent Transactions Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Giao dịch gần đây",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827)
                        )
                    }

                    // Transactions List
                    if (transactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "📋", fontSize = 48.sp)
                                Text(
                                    text = "Chưa có giao dịch nào",
                                    fontSize = 14.sp,
                                    color = Color(0xFF6B7280),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.White)
                        ) {
                            TransactionList(
                                transactions = transactions,
                                listState = listState,
                                isLoadingMore = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun SettlementTypeCard(
    icon: String,
    title: String,
    count: Int,
    amount: Long,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header với background màu khác
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = borderColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = icon, fontSize = 13.sp)
                    }
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Chi tiết",
                    tint = borderColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$count GD",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = UtilHelper.formatCurrency(amount, "đ"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = borderColor,
                    maxLines = 1
                )
            }
        }
    }
}