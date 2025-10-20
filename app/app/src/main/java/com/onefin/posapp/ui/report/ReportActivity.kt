package com.onefin.posapp.ui.report

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.onefin.posapp.R
import com.onefin.posapp.core.models.DateRangeType
import com.onefin.posapp.core.models.ReportItem
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.services.ApiService
import com.onefin.posapp.core.utils.UtilHelper
import com.onefin.posapp.core.utils.ValidationHelper
import com.onefin.posapp.ui.base.BaseActivity
import com.onefin.posapp.ui.report.components.DateRangeMenuItem
import com.onefin.posapp.ui.report.components.DateRangePickerSheet
import com.onefin.posapp.ui.report.components.PaymentMethodCard
import com.onefin.posapp.ui.report.components.ReportSummary
import com.onefin.posapp.ui.theme.PosAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReportActivity : BaseActivity() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var validationHelper: ValidationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PosAppTheme {
                ReportScreen(
                    apiService = apiService,
                    onBackPressed = { finish() },
                    validationHelper = validationHelper
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    apiService: ApiService,
    onBackPressed: () -> Unit,
    validationHelper: ValidationHelper
) {
    val context = LocalContext.current
    var reportItems by remember { mutableStateOf<List<ReportItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDateRange by remember { mutableStateOf(DateRangeType.TODAY) }
    var showDatePicker by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf(UtilHelper.getTodayDate()) }
    var toDate by remember { mutableStateOf(UtilHelper.getTodayDate()) }
    var showDropdown by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val customGreenColor = Color(0xFF16A34A)

    // Load data when date range changes
    LaunchedEffect(fromDate, toDate) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val endpoint = "/api/transaction/report"
                val body = mapOf(
                    "ToDate" to toDate,
                    "FromDate" to fromDate
                )
                val resultApi = apiService.post(endpoint, body) as ResultApi<*>

                val gson = com.google.gson.Gson()
                val jsonString = gson.toJson(resultApi.data)
                val itemsArray = gson.fromJson(jsonString, Array<ReportItem>::class.java)
                reportItems = itemsArray.toList()
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
                            stringResource(R.string.report_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.content_desc_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    )
                )
                HorizontalDivider(thickness = 1.dp, color = Color(0xFFE5E7EB))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF9FAFB))
        ) {
            // Display main content or error (when not loading)
            if (!isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Date Range Picker Header
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showDropdown = true }
                                    .padding(vertical = 20.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$fromDate - $toDate",
                                    fontSize = 15.sp,
                                    color = Color(0xFF111827),
                                    fontWeight = FontWeight.Medium
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
                                    .width(280.dp)
                                    .background(Color.White),
                                offset = DpOffset(x = 0.dp, y = 12.dp),
                                properties = PopupProperties(focusable = true)
                            ) {
                                DateRangeMenuItem(
                                    text = stringResource(R.string.date_range_today),
                                    isSelected = selectedDateRange == DateRangeType.TODAY,
                                    onClick = {
                                        selectedDateRange = DateRangeType.TODAY
                                        fromDate = UtilHelper.getTodayDate()
                                        toDate = UtilHelper.getTodayDate()
                                        showDropdown = false
                                    }
                                )
                                DateRangeMenuItem(
                                    text = stringResource(R.string.date_range_yesterday),
                                    isSelected = selectedDateRange == DateRangeType.YESTERDAY,
                                    onClick = {
                                        selectedDateRange = DateRangeType.YESTERDAY
                                        fromDate = UtilHelper.getYesterdayDate()
                                        toDate = UtilHelper.getYesterdayDate()
                                        showDropdown = false
                                    }
                                )
                                DateRangeMenuItem(
                                    text = stringResource(R.string.date_range_last_7_days),
                                    isSelected = selectedDateRange == DateRangeType.LAST_7_DAYS,
                                    onClick = {
                                        selectedDateRange = DateRangeType.LAST_7_DAYS
                                        fromDate = UtilHelper.getDaysAgo(7)
                                        toDate = UtilHelper.getTodayDate()
                                        showDropdown = false
                                    }
                                )
                                DateRangeMenuItem(
                                    text = stringResource(R.string.date_range_last_30_days),
                                    isSelected = selectedDateRange == DateRangeType.LAST_30_DAYS,
                                    onClick = {
                                        selectedDateRange = DateRangeType.LAST_30_DAYS
                                        fromDate = UtilHelper.getDaysAgo(30)
                                        toDate = UtilHelper.getTodayDate()
                                        showDropdown = false
                                    }
                                )

                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = Color(0xFFE5E7EB),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                DateRangeMenuItem(
                                    text = stringResource(R.string.date_range_custom),
                                    isSelected = selectedDateRange == DateRangeType.CUSTOM,
                                    onClick = {
                                        selectedDateRange = DateRangeType.CUSTOM
                                        showDropdown = false
                                        showDatePicker = true
                                    }
                                )
                            }
                        }
                    }

                    // Report Content or Error Message
                    when {
                        errorMessage != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = Color(0xFFDC2626),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        else -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = Color.White,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Column {
                                    ReportSummary(
                                        totalTransactions = reportItems.sumOf { it.count },
                                        totalAmount = reportItems.sumOf { it.amount }
                                    )
                                    if (reportItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    reportItems.forEach { item ->
                                        PaymentMethodCard(
                                            item = item,
                                            modifier = Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 6.dp
                                            )
                                        )
                                    }
                                    if (reportItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = customGreenColor)
                }
            }
        }
    }

    // Date Picker Bottom Sheet
    if (showDatePicker) {
        DateRangePickerSheet(
            currentToDate = toDate,
            currentFromDate = fromDate,
            validationHelper = validationHelper,
            onDismiss = { showDatePicker = false },
            onApply = { newFrom, newTo ->
                toDate = newTo
                fromDate = newFrom
                showDatePicker = false
            }
        )
    }
}