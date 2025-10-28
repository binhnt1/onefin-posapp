package com.onefin.posapp.ui.settlement

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.TransactionList
import com.onefin.posapp.ui.settlement.components.SettlementBottomBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.gson.Gson
import com.onefin.posapp.core.models.ResultApi
import androidx.compose.ui.platform.LocalContext

@AndroidEntryPoint
class SettlementActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                SettlementScreen(
                    apiService = apiService,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    apiService: ApiService,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var totalAmount by remember { mutableLongStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var isSettling by remember { mutableStateOf(false) }
    var pagingSize by remember { mutableIntStateOf(0) }
    var pagingIndex by remember { mutableIntStateOf(0) }
    var pagingTotal by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Function to load transactions
    fun loadTransactions(page: Int, isLoadMore: Boolean = false) {
        scope.launch {
            if (isLoadMore) {
                isLoadingMore = true
            } else {
                isLoading = true
                transactions = emptyList()
                totalAmount = 0
                currentPage = 1
                hasMore = true
            }
            errorMessage = null

            try {
                val endpoint = when (selectedTab) {
                    0 -> "/api/transaction/items?status=1"
                    1 -> "/api/transaction/items?status=2"
                    else -> "/api/transaction/items?status=-1"
                }

                val queryParams = mapOf(
                    "page" to page,
                    "limit" to 20
                )

                val resultApi = apiService.get(endpoint, queryParams) as ResultApi<*>
                val gson = Gson()

                // Extract total
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

                // Parse transactions
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
                errorMessage = e.message ?: context.getString(com.onefin.posapp.R.string.error_unknown)
                if (!isLoadMore) {
                    transactions = emptyList()
                    totalAmount = 0
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    // Function to handle settlement
    fun handleSettlement() {
        scope.launch {
            isSettling = true
            try {
                val endpoint = when (selectedTab) {
                    0 -> "/api/transaction/items?status=1"
                    1 -> "/api/transaction/items?status=2"
                    else -> "/api/transaction/items?status=-1"
                }

                val body = mapOf(
                    "transactionIds" to transactions.map { it.transactionId }
                )

                apiService.post(endpoint, body) as ResultApi<*>

                // Success - reload data
                loadTransactions(1, false)

                // TODO: Show success message

            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(com.onefin.posapp.R.string.error_unknown)
            } finally {
                isSettling = false
            }
        }
    }

    // Load transactions when tab changes
    LaunchedEffect(selectedTab) {
        loadTransactions(1, false)
    }

    // Detect scroll to bottom for load more
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(id = com.onefin.posapp.R.string.menu_settlement),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = com.onefin.posapp.R.string.content_desc_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    )
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFE5E7EB)
                )
            }
        },
        bottomBar = {
            if (selectedTab == 0 && !isLoading && transactions.isNotEmpty()) {
                SettlementBottomBar(
                    isSettling = isSettling,
                    totalAmount = totalAmount,
                    onSettleClick = { handleSettlement() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF16A34A),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF16A34A)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.height(56.dp),
                    text = {
                        Text(
                            stringResource(id = com.onefin.posapp.R.string.menu_settlement),
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color(0xFF16A34A) else Color(0xFF6B7280)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.height(56.dp),
                    text = {
                        Text(
                            stringResource(id = com.onefin.posapp.R.string.status_settled),
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color(0xFF16A34A) else Color(0xFF6B7280)
                        )
                    }
                )
            }

            // Transaction count
            if (!isLoading && transactions.isNotEmpty()) {
                var loadedCount = pagingSize * pagingIndex
                if (loadedCount > pagingTotal)
                    loadedCount = pagingTotal
                Text(
                    text = context.getString(
                        com.onefin.posapp.R.string.transaction_count_format,
                        loadedCount,
                        pagingTotal
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9FAFB))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
            }

            // Content
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = com.onefin.posapp.R.string.no_transactions_found),
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

@Composable
fun stringResource(id: Int): String {
    return LocalContext.current.getString(id)
}