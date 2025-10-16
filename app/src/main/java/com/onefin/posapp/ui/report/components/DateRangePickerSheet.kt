package com.onefin.posapp.ui.report.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefin.posapp.core.utils.ValidationHelper
import com.onefin.posapp.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerSheet(
    validationHelper: ValidationHelper,
    currentFromDate: String,
    currentToDate: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    val context = LocalContext.current
    var fromDate by remember { mutableStateOf(currentFromDate) }
    var toDate by remember { mutableStateOf(currentToDate) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val customGreenColor = Color(0xFF16A34A)

    val utcSimpleDateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun dateToMillis(dateString: String): Long? {
        return try {
            utcSimpleDateFormat.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }

    val fromDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateToMillis(fromDate)
    )
    val toDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateToMillis(toDate)
    )


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.report_date_range_label),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = context.getString(R.string.report_close_icon),
                        tint = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                context.getString(R.string.report_from_date),
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = fromDate,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFromDatePicker = true },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = customGreenColor,
                    unfocusedBorderColor = Color(0xFFD1D5DB),
                    disabledBorderColor = Color(0xFFD1D5DB),
                    disabledTextColor = Color(0xFF111827)
                ),
                enabled = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                context.getString(R.string.report_to_date),
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = toDate,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showToDatePicker = true },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = customGreenColor,
                    unfocusedBorderColor = Color(0xFFD1D5DB),
                    disabledBorderColor = Color(0xFFD1D5DB),
                    disabledTextColor = Color(0xFF111827)
                ),
                enabled = false
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B7280))
                ) {
                    Text(
                        context.getString(R.string.report_cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        if (validationHelper.isValidDateRange(fromDate, toDate)) {
                            onApply(fromDate, toDate)
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = customGreenColor),
                    enabled = validationHelper.isValidDateRange(fromDate, toDate)
                ) {
                    Text(
                        context.getString(R.string.report_apply),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // From Date Picker Dialog
    if (showFromDatePicker) {
        Dialog(
            onDismissRequest = { showFromDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DatePicker(
                        state = fromDatePickerState,
                        showModeToggle = false,
                        title = null,
                        headline = null,
                        colors = DatePickerDefaults.colors(
                            containerColor = Color.White,
                            selectedDayContentColor = Color.White,
                            selectedDayContainerColor = customGreenColor,
                            todayContentColor = customGreenColor,
                            todayDateBorderColor = customGreenColor
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-32).dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        OutlinedButton(
                            onClick = { showFromDatePicker = false },
                            modifier = Modifier
                                .width(120.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                context.getString(R.string.report_cancel),
                                color = Color(0xFF6B7280)
                            )
                        }
                        Button(
                            onClick = {
                                fromDatePickerState.selectedDateMillis?.let { millis ->
                                    fromDate = utcSimpleDateFormat.format(Date(millis))
                                }
                                showFromDatePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = customGreenColor),
                            modifier = Modifier
                                .width(120.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(context.getString(R.string.report_ok))
                        }
                    }
                }
            }
        }
    }

    // To Date Picker Dialog
    if (showToDatePicker) {
        Dialog(
            onDismissRequest = { showToDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DatePicker(
                        state = toDatePickerState,
                        showModeToggle = false,
                        title = null,
                        headline = null,
                        colors = DatePickerDefaults.colors(
                            containerColor = Color.White,
                            selectedDayContentColor = Color.White,
                            selectedDayContainerColor = customGreenColor,
                            todayContentColor = customGreenColor,
                            todayDateBorderColor = customGreenColor
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-32).dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        OutlinedButton(
                            onClick = { showToDatePicker = false },
                            modifier = Modifier
                                .width(120.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                context.getString(R.string.report_cancel),
                                color = Color(0xFF6B7280)
                            )
                        }
                        Button(
                            onClick = {
                                toDatePickerState.selectedDateMillis?.let { millis ->
                                    toDate = utcSimpleDateFormat.format(Date(millis))
                                }
                                showToDatePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = customGreenColor),
                            modifier = Modifier
                                .width(120.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(context.getString(R.string.report_ok))
                        }
                    }
                }
            }
        }
    }
}