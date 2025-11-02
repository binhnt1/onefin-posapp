package com.onefin.posapp.ui.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.onefin.posapp.R
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.models.data.SettleResultData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.PrinterHelper
import com.onefin.posapp.core.utils.ReceiptPrinter
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.modals.ProcessingDialog
import com.onefin.posapp.ui.settlement.SettlementActivity
import com.onefin.posapp.ui.settlement.components.SettlementBottomBar
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.TransactionList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class TransactionType(val value: Int, val endpoint: String, val titleRes: Int, val icon: String) {
    CARD(1, "/api/transaction/items/1", R.string.transaction_type_card, "💳"),
    QR(2, "/api/transaction/items/2", R.string.transaction_type_qr, "🏦"),
    MEMBER(3, "/api/transaction/items/3", R.string.transaction_type_member, "📱"),
    CANCELLED(-1, "/api/transaction/items?status=-1", R.string.transaction_type_cancelled, "❌"),
    UNSETTLED(100, "/api/transaction/items?status=1", R.string.settlement_pending_tab, "⏳"),
    SETTLED(101, "/api/transaction/items?status=2", R.string.settlement_completed_tab, "✅");

    companion object {
        fun fromValue(value: Int): TransactionType? {
            return entries.find { it.value == value }
        }
    }
}

@AndroidEntryPoint
class TransactionTypeDetailActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var printerHelper: PrinterHelper

    @Inject
    lateinit var receiptPrinter: ReceiptPrinter

    companion object {
        private const val EXTRA_TRANSACTION_TYPE = "EXTRA_TRANSACTION_TYPE"

        fun start(context: Context, type: TransactionType) {
            val intent = Intent(context, TransactionTypeDetailActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_TYPE, type.value)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeValue = intent.getIntExtra(EXTRA_TRANSACTION_TYPE, 1)
        val transactionType = TransactionType.fromValue(typeValue) ?: TransactionType.CARD

        setContent {
            PosAppTheme {
                TransactionTypeDetailScreen(
                    apiService = apiService,
                    printerHelper = printerHelper,
                    receiptPrinter = receiptPrinter,
                    transactionType = transactionType,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTypeDetailScreen(
    apiService: ApiService,
    printerHelper: PrinterHelper,
    receiptPrinter: ReceiptPrinter,
    transactionType: TransactionType,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var totalAmount by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var pagingSize by remember { mutableIntStateOf(0) }
    var pagingIndex by remember { mutableIntStateOf(0) }
    var pagingTotal by remember { mutableIntStateOf(0) }
    var resumeCount by remember { mutableIntStateOf(0) }

    // Settlement states (chỉ dùng khi type = UNSETTLED)
    var isSettling by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val customGreenColor = Color(0xFF10B981)
    val customRedColor = Color(0xFFEF4444)

    fun loadTransactions(page: Int, isLoadMore: Boolean = false) {
        scope.launch {
            if (isLoadMore) {
                isLoadingMore = true
            } else {
                isLoading = true
                transactions = emptyList()
                currentPage = 1
                hasMore = true
            }
            errorMessage = null

            try {
                val queryParams = mapOf(
                    "page" to page,
                    "limit" to 20
                )

                val resultApi = apiService.get(transactionType.endpoint, queryParams) as ResultApi<*>
                val gson = Gson()

                // Get total amount
                if (!isLoadMore) {
                    totalAmount = (resultApi.total as? Number)?.toLong() ?: 0L
                }

                val objectExtra = resultApi.objectExtra as? Map<*, *>
                val paging = objectExtra?.get("Paging") as? Map<*, *>
                if (paging != null) {
                    pagingSize = (paging["Size"] as? Number)?.toInt() ?: 0
                    pagingIndex = (paging["Index"] as? Number)?.toInt() ?: 0
                    pagingTotal = (paging["Total"] as? Number)?.toInt() ?: 0
                }

                val jsonString = gson.toJson(resultApi.data)
                val type = object : com.google.gson.reflect.TypeToken<List<Transaction>>() {}.type
                val newTransactions: List<Transaction> = gson.fromJson(jsonString, type) ?: emptyList()

                transactions = if (isLoadMore) {
                    transactions + newTransactions
                } else {
                    newTransactions
                }

                hasMore = newTransactions.size >= 20
                currentPage = page

            } catch (e: Exception) {
                errorMessage = e.message
                if (!isLoadMore) {
                    transactions = emptyList()
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    fun handleRefresh() {
        loadTransactions(1)
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadTransactions(1)
    }

    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount++
                if (TransactionActivity.shouldRefresh) {
                    TransactionActivity.shouldRefresh = false
                    scope.launch {
                        handleRefresh()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Trigger refresh on resume (skip first time)
    LaunchedEffect(resumeCount) {
        if (resumeCount > 1 && !TransactionActivity.shouldRefresh) {
            handleRefresh()
        }
    }

    // Load more when scrolling
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= transactions.size - 5 &&
                    !isLoading &&
                    !isLoadingMore &&
                    hasMore
                ) {
                    loadTransactions(currentPage + 1, true)
                }
            }
    }

    // ========== SETTLEMENT DIALOG (chỉ cho UNSETTLED) ==========
    if (showSettleDialog && transactionType == TransactionType.UNSETTLED) {
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
                        modifier = Modifier.size(20.dp)
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
                                    // Set flag để SettlementActivity refresh
                                    SettlementActivity.shouldRefresh = true
                                    handleRefresh()
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
                                    } else {
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
                                    .size(20.dp)
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
            Surface(
                shadowElevation = 2.dp,
                color = Color.White
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = transactionType.icon,
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = context.getString(transactionType.titleRes),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                            }
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color(0xFF111827)
                        )
                    )

                    if (!isLoading && transactions.isNotEmpty()) {
                        var loadedCount = pagingSize * pagingIndex
                        if (loadedCount > pagingTotal)
                            loadedCount = pagingTotal

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF9FAFB))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(
                                    R.string.transaction_count_format,
                                    loadedCount,
                                    pagingTotal
                                ),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )

                            IconButton(
                                onClick = { handleRefresh() },
                                modifier = Modifier.size(40.dp),
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = if (isLoading) Color(0xFF9CA3AF) else Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // CHỈ hiện bottom bar khi type = UNSETTLED và có transactions
            if (transactionType == TransactionType.UNSETTLED && !isLoading && transactions.isNotEmpty()) {
                SettlementBottomBar(
                    isSettling = isSettling || isPrinting,
                    totalAmount = totalAmount,
                    onSettleClick = { showSettleDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF16A34A))
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 14.sp
                        )
                    }
                }
                transactions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.no_transactions_found),
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    TransactionList(
                        transactions = transactions,
                        listState = listState,
                        isLoadingMore = isLoadingMore
                    )
                }
            }
        }
    }
}