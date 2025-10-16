package com.onefin.posapp.ui.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.google.gson.Gson
import com.onefin.posapp.core.models.PaymentHistory
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.history.components.PaymentHistoryDetailContent
import com.onefin.posapp.ui.theme.PosAppTheme
import com.onefin.posapp.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentHistoryDetailActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var gson: Gson

    companion object {
        private const val EXTRA_PAYMENT_ID = "PAYMENT_ID"

        fun start(context: Context, paymentId: Long) {
            val intent = Intent(context, PaymentHistoryDetailActivity::class.java)
            intent.putExtra(EXTRA_PAYMENT_ID, paymentId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, 0L)

        setContent {
            PosAppTheme {
                PaymentHistoryDetailScreen(
                    paymentId = paymentId,
                    apiService = apiService,
                    gson = gson,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryDetailScreen(
    paymentId: Long,
    apiService: ApiService,
    gson: Gson,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var paymentHistory by remember { mutableStateOf<PaymentHistory?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Load payment history detail
    LaunchedEffect(paymentId) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val resultApi = apiService.get("/api/payment/$paymentId", emptyMap()) as ResultApi<*>
                val jsonString = gson.toJson(resultApi.data)
                val history: PaymentHistory = gson.fromJson(jsonString, PaymentHistory::class.java)
                paymentHistory = history

            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.error_loading_data)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            context.getString(R.string.payment_history_detail_title),
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
                .background(Color(0xFFF9FAFB))
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
                paymentHistory != null -> {
                    PaymentHistoryDetailContent(paymentHistory = paymentHistory!!)
                }
            }
        }
    }
}