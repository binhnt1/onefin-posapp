package com.onefin.posapp.ui.transaction

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.R
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.Transaction
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.ui.transaction.components.TransactionList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransactionActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                TransactionScreen(
                    apiService = apiService,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    apiService: ApiService,
    onBackPressed: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var pagingSize by remember { mutableIntStateOf(0) }
    var pagingIndex by remember { mutableIntStateOf(0) }
    var pagingTotal by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
                val endpoint = when (selectedTab) {
                    0 -> "/api/transaction/items/1"
                    1 -> "/api/transaction/items/2"
                    2 -> "/api/transaction/items?status=-1"
                    else -> "/api/transaction/items?status=-1"
                }

                val queryParams = mapOf(
                    "page" to page,
                    "limit" to 20
                )

                val resultApi = apiService.get(endpoint, queryParams) as ResultApi<*>
                val gson = com.google.gson.Gson()

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

    LaunchedEffect(selectedTab) {
        loadTransactions(1, false)
    }

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
                            stringResource(id = R.string.menu_transactions),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.content_desc_back)
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
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
                            stringResource(id = R.string.tab_card),
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
                            stringResource(id = R.string.tab_vietqr),
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color(0xFF16A34A) else Color(0xFF6B7280)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.height(56.dp),
                    text = {
                        Text(
                            stringResource(id = R.string.tab_failed_transactions),
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 2) Color(0xFF16A34A) else Color(0xFF6B7280)
                        )
                    }
                )
            }

            if (!isLoading && transactions.isNotEmpty()) {
                var loadedCount = pagingSize * pagingIndex
                if (loadedCount > pagingTotal)
                    loadedCount = pagingTotal
                Text(
                    text = stringResource(id = R.string.transaction_count_format, loadedCount, pagingTotal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9FAFB))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
            }

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
                            text = errorMessage ?: stringResource(id = R.string.error_loading_data),
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
                            text = stringResource(id = R.string.no_transactions_found),
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