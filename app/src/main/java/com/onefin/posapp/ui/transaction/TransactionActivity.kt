package com.onefin.posapp.ui.transaction

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onefin.posapp.R
import com.onefin.posapp.core.models.DateRangeType
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.report.components.DateRangeMenuItem
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.TransactionList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransactionActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    companion object {
        var shouldRefresh = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                TransactionDashboardScreen(
                    apiService = apiService,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

data class TransactionStatistic(
    val cardCount: Int = 0,
    val cardAmount: Long = 0L,
    val memberCount: Int = 0,
    val memberAmount: Long = 0L,
    val qrCount: Int = 0,
    val qrAmount: Long = 0L,
    val cancelledCount: Int = 0,
    val cancelledAmount: Long = 0L
)

data class WeekDayStats(
    val label: String,
    val count: Int,
    val amount: Long,
    val isToday: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDashboardScreen(
    apiService: ApiService,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var todayTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var todayStats by remember { mutableStateOf(TransactionStatistic()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resumeCount by remember { mutableIntStateOf(0) }

    var selectedDateRange by remember { mutableStateOf(DateRangeType.TODAY) }
    var showDropdown by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf(UtilHelper.getTodayDate().replace("/", "")) }
    var toDate by remember { mutableStateOf(UtilHelper.getTodayDate().replace("/", "")) }

    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val customGreenColor = Color(0xFF10B981)

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val gson = com.google.gson.Gson()

                // Load today's transactions
                val todayEndpoint = "/api/transaction/items/0"
                val toDateValue = toDate.replace("/", "")
                val fromDateValue = fromDate.replace("/", "")
                val todayParams = mapOf("page" to 1, "limit" to 20, "fromDate" to fromDateValue, "toDate" to toDateValue)
                val todayResult = apiService.get(todayEndpoint, todayParams) as ResultApi<*>
                val todayJsonString = gson.toJson(todayResult.data)
                val type = object : com.google.gson.reflect.TypeToken<List<Transaction>>() {}.type
                todayTransactions = gson.fromJson(todayJsonString, type) ?: emptyList()

                // Load stats for today
                val qrResult = apiService.get("/api/transaction/items/2", mapOf("page" to 1, "limit" to 1, "fromDate" to fromDateValue, "toDate" to toDateValue)) as ResultApi<*>
                val cardResult = apiService.get("/api/transaction/items/1", mapOf("page" to 1, "limit" to 1, "fromDate" to fromDateValue, "toDate" to toDateValue)) as ResultApi<*>
                val memberResult = apiService.get("/api/transaction/items/3", mapOf("page" to 1, "limit" to 1, "fromDate" to fromDateValue, "toDate" to toDateValue)) as ResultApi<*>
                val cancelledResult = apiService.get("/api/transaction/items?status=-1", mapOf("page" to 1, "limit" to 1, "fromDate" to fromDateValue, "toDate" to toDateValue)) as ResultApi<*>

                fun getPagingTotal(result: ResultApi<*>): Int {
                    val objectExtra = result.objectExtra as? Map<*, *>
                    val paging = objectExtra?.get("Paging") as? Map<*, *>
                    return (paging?.get("Total") as? Number)?.toInt() ?: 0
                }

                val qrTotal = qrResult.total?.toString()?.toLongOrNull() ?: 0L
                val cardTotal = cardResult.total?.toString()?.toLongOrNull() ?: 0L
                val memberTotal = memberResult.total?.toString()?.toLongOrNull() ?: 0L
                val cancelledTotal = cancelledResult.total?.toString()?.toLongOrNull() ?: 0L
                todayStats = TransactionStatistic(
                    qrAmount = qrTotal,
                    cardAmount = cardTotal,
                    memberAmount = memberTotal,
                    cancelledAmount = cancelledTotal,
                    qrCount = getPagingTotal(qrResult),
                    cardCount = getPagingTotal(cardResult),
                    memberCount = getPagingTotal(memberResult),
                    cancelledCount = getPagingTotal(cancelledResult)
                )

                // Load week stats (last 7 days)
                val weekData = mutableListOf<WeekDayStats>()
                for (i in 6 downTo 0) {
                    val date = UtilHelper.getDaysAgo(i).replace("/", "")
                    val dayResult = apiService.get(
                        "/api/transaction/items/0",
                        mapOf("page" to 1, "limit" to 1, "fromDate" to date, "toDate" to date)
                    ) as ResultApi<*>

                    val dayCount = getPagingTotal(dayResult)
                    val dayAmount = dayResult.total?.toString()?.toLongOrNull() ?: 0L

                    weekData.add(
                        WeekDayStats(
                            label = when (i) {
                                0 -> "Hôm nay"
                                else -> {
                                    val dayOfWeek = UtilHelper.getDayOfWeek(date)
                                    when (dayOfWeek) {
                                        1 -> "CN"
                                        2 -> "T2"
                                        3 -> "T3"
                                        4 -> "T4"
                                        5 -> "T5"
                                        6 -> "T6"
                                        7 -> "T7"
                                        else -> ""
                                    }
                                }
                            },
                            count = dayCount,
                            amount = dayAmount,
                            isToday = i == 0
                        )
                    )
                }

            } catch (e: Exception) {
                errorMessage = e.message
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
                if (TransactionActivity.shouldRefresh) {
                    TransactionActivity.shouldRefresh = false
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

    LaunchedEffect(resumeCount) {
        if (resumeCount > 1 && !TransactionActivity.shouldRefresh) {
            loadData()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                context.getString(R.string.menu_transactions),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = context.getString(R.string.content_desc_back),
                                    tint = Color(0xFF111827)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { loadData() },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = if (isLoading) Color(0xFF9CA3AF) else customGreenColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color(0xFF111827)
                        )
                    )
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))
                }
            }
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9FAFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = customGreenColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Đang tải...",
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF9FAFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(text = "⚠️", fontSize = 48.sp)
                        Text(
                            text = errorMessage ?: "Đã có lỗi xảy ra",
                            color = Color(0xFFDC2626),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { loadData() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customGreenColor
                            )
                        ) {
                            Text("Thử lại")
                        }
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
                    // TODAY SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Date Range Dropdown
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clickable { showDropdown = true }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = customGreenColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (selectedDateRange) {
                                            DateRangeType.TODAY -> stringResource(R.string.date_range_today)
                                            DateRangeType.YESTERDAY -> stringResource(R.string.date_range_yesterday)
                                            DateRangeType.LAST_7_DAYS -> stringResource(R.string.date_range_last_7_days)
                                            DateRangeType.LAST_30_DAYS -> stringResource(R.string.date_range_last_30_days)
                                            else -> "$fromDate - $toDate"
                                        },
                                        fontSize = 16.sp,
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
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
                                    modifier = Modifier
                                        .width(240.dp)
                                        .background(Color.White),
                                    offset = DpOffset(x = 0.dp, y = 8.dp),
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    DateRangeMenuItem(
                                        text = stringResource(R.string.date_range_today),
                                        isSelected = selectedDateRange == DateRangeType.TODAY,
                                        onClick = {
                                            selectedDateRange = DateRangeType.TODAY
                                            fromDate = UtilHelper.getTodayDate().replace("/", "")
                                            toDate = UtilHelper.getTodayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = stringResource(R.string.date_range_yesterday),
                                        isSelected = selectedDateRange == DateRangeType.YESTERDAY,
                                        onClick = {
                                            selectedDateRange = DateRangeType.YESTERDAY
                                            fromDate = UtilHelper.getYesterdayDate().replace("/", "")
                                            toDate = UtilHelper.getYesterdayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = stringResource(R.string.date_range_last_7_days),
                                        isSelected = selectedDateRange == DateRangeType.LAST_7_DAYS,
                                        onClick = {
                                            selectedDateRange = DateRangeType.LAST_7_DAYS
                                            fromDate = UtilHelper.getDaysAgo(7).replace("/", "")
                                            toDate = UtilHelper.getTodayDate().replace("/", "")
                                            showDropdown = false
                                        }
                                    )
                                    DateRangeMenuItem(
                                        text = stringResource(R.string.date_range_last_30_days),
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hero Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(customGreenColor, Color(0xFF059669))
                                        )
                                    )
                                    .padding(24.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Tổng doanh thu",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = UtilHelper.formatCurrency(
                                            todayStats.cardAmount + todayStats.memberAmount +
                                                    todayStats.qrAmount + todayStats.cancelledAmount, "đ"
                                        ),
                                        fontSize = 32.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${todayStats.cardCount + todayStats.memberCount + todayStats.qrCount + todayStats.cancelledCount} giao dịch",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Type Cards Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TodayTypeCard(
                                icon = "💳",
                                title = "Giao dịch thẻ",
                                count = todayStats.cardCount,
                                amount = todayStats.cardAmount,
                                borderColor = Color(0xFF3B82F6),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TransactionTypeDetailActivity.start(context, TransactionType.CARD)
                                }
                            )
                            TodayTypeCard(
                                icon = "📱",
                                title = "Thành viên",
                                count = todayStats.memberCount,
                                amount = todayStats.memberAmount,
                                borderColor = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TransactionTypeDetailActivity.start(context, TransactionType.MEMBER)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TodayTypeCard(
                                icon = "🏦",
                                title = "VietQR",
                                count = todayStats.qrCount,
                                amount = todayStats.qrAmount,
                                borderColor = customGreenColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TransactionTypeDetailActivity.start(context, TransactionType.QR)
                                }
                            )
                            TodayTypeCard(
                                icon = "❌",
                                title = "Đã hủy",
                                count = todayStats.cancelledCount,
                                amount = todayStats.cancelledAmount,
                                borderColor = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TransactionTypeDetailActivity.start(context, TransactionType.CANCELLED)
                                }
                            )
                        }
                    }

                    // Recent Transactions
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

                    if (todayTransactions.isEmpty()) {
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
                                transactions = todayTransactions,
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
fun TodayTypeCard(
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
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
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

@Composable
fun WeekDayItem(
    label: String,
    count: Int,
    amount: Long,
    isToday: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (isToday) Color(0xFF10B981) else Color(0xFF6B7280),
            modifier = Modifier.width(80.dp)
        )

        Text(
            text = "$count GD",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827),
            modifier = Modifier.width(60.dp)
        )

        Text(
            text = UtilHelper.formatCurrency(amount, "đ"),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isToday) Color(0xFF10B981) else Color(0xFF374151),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}