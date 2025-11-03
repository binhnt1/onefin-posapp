package com.onefin.posapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.R
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.BalanceData
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.UtilHelper
import kotlinx.coroutines.launch

@Composable
fun BalanceDisplay(
    apiService: ApiService,
    modifier: Modifier = Modifier
) {
    var balances by remember { mutableStateOf<List<BalanceData>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val gson = Gson()

    // Load balance function
    fun refresh() {
        scope.launch {
            isLoading = true
            errorMessage = null
            loadBalance(apiService, gson) { result, error ->
                balances = result
                errorMessage = error
                isLoading = false
            }
        }
    }

    // ✅ Reload mỗi khi component được hiển thị
    DisposableEffect(Unit) {
        refresh()
        onDispose { }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFAFAFA),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Balance",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.balance_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                IconButton(
                    onClick = { refresh() },
                    enabled = !isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isLoading) 360f else 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )

                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isLoading) Color(0xFF9CA3AF) else Color(0xFF16A34A),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (isLoading) rotation else 0f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            AnimatedContent(
                targetState = when {
                    errorMessage != null -> "error"
                    balances == null -> "loading"
                    else -> "content"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "balance_content"
            ) { state ->
                when (state) {
                    "loading" -> LoadingState()
                    "error" -> ErrorState(errorMessage ?: "Unknown error")
                    "content" -> BalanceContent(balances!!)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color(0xFF16A34A),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Đang tải...",
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = Color(0xFFEF4444),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BalanceContent(balances: List<BalanceData>) {
    val pendingBalance = balances.find { it.status == 1 }
    val settledBalance = balances.find { it.status == 2 }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Số dư tạm có (Pending)
        BalanceItem(
            label = stringResource(R.string.balance_pending),
            amount = pendingBalance?.amount ?: 0L,
            color = Color(0xFFF59E0B),
            backgroundColor = Color(0xFFFEF3C7)
        )

        // Số dư báo có (Settled)
        BalanceItem(
            label = stringResource(R.string.balance_settled),
            amount = settledBalance?.amount ?: 0L,
            color = Color(0xFF16A34A),
            backgroundColor = Color(0xFFDCFCE7)
        )
    }
}

@Composable
private fun BalanceItem(
    label: String,
    amount: Long,
    color: Color,
    backgroundColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151)
        )

        Text(
            text = UtilHelper.formatCurrency(amount),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// Helper function to load balance
private suspend fun loadBalance(
    apiService: ApiService,
    gson: Gson,
    onResult: (List<BalanceData>?, String?) -> Unit
) {
    try {
        val resultApi = apiService.post("/api/transaction/balance", emptyMap()) as ResultApi<*>

        if (resultApi.isSuccess()) {
            val balanceJson = gson.toJson(resultApi.data)
            val type = object : TypeToken<List<BalanceData>>() {}.type
            val balances = gson.fromJson<List<BalanceData>>(balanceJson, type)
            onResult(balances, null)
        } else {
            onResult(null, resultApi.description)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(null, "Lỗi: ${e.message}")
    }
}