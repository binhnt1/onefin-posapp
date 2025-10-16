package com.onefin.posapp.ui.history

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefin.posapp.core.models.PaymentHistory
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.history.components.PaymentHistoryItem
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentHistoryActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PosAppTheme {
                PaymentHistoryScreen(
                    apiService = apiService,
                    onBackPressed = { finish() },
                    onItemClick = { paymentHistory ->

                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    apiService: ApiService,
    onBackPressed: () -> Unit,
    onItemClick: (PaymentHistory) -> Unit
) {
    val context = LocalContext.current
    var paymentHistories by remember { mutableStateOf<List<PaymentHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun loadPaymentHistories(page: Int, isLoadMore: Boolean = false) {
        scope.launch {
            if (isLoadMore) {
                isLoadingMore = true
            } else {
                isLoading = true
                paymentHistories = emptyList()
                currentPage = 1
                hasMore = true
            }
            errorMessage = null

            try {
                val queryParams = mapOf(
                    "page" to page,
                    "limit" to 20
                )

                val resultApi = apiService.get("/api/payment/items", queryParams) as ResultApi<*>
                val gson = com.google.gson.Gson()
                val jsonString = gson.toJson(resultApi.data)
                val type = object : com.google.gson.reflect.TypeToken<List<PaymentHistory>>() {}.type
                val newHistories: List<PaymentHistory> = gson.fromJson(jsonString, type) ?: emptyList()

                paymentHistories = if (isLoadMore) {
                    paymentHistories + newHistories
                } else {
                    newHistories
                }

                hasMore = newHistories.size >= 20
                currentPage = page

            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.error_loading_data)
                if (!isLoadMore) {
                    paymentHistories = emptyList()
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPaymentHistories(1, false)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= paymentHistories.size - 5 &&
                    !isLoading &&
                    !isLoadingMore &&
                    hasMore
                ) {
                    loadPaymentHistories(currentPage + 1, true)
                }
            }
    }

    val groupedHistories = remember(paymentHistories) {
        paymentHistories.groupBy { it.getFormattedDate() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            context.getString(R.string.payment_history_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = context.getString(R.string.content_desc_back)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFFFFFFF))
        ) {
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
                paymentHistories.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.payment_history_empty_list),
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        groupedHistories.forEach { (date, historiesForDate) ->
                            item(key = "header_$date") {
                                Text(
                                    text = date,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0F9FF))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                            }

                            items(
                                count = historiesForDate.size,
                                key = { index -> "${date}_${historiesForDate[index].id}_$index" }
                            ) { index ->
                                val history = historiesForDate[index]

                                if (index == 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                PaymentHistoryItem(
                                    paymentHistory = history,
                                    onClick = { onItemClick(history) }
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF16A34A),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}