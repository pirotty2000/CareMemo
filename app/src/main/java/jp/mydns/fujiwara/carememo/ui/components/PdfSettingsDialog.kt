package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportRange(val displayNameRes: Int) {
    ALL(R.string.export_range_all),
    LATEST(R.string.export_range_latest),
    ONE_MONTH(R.string.export_range_one_month),
    THREE_MONTHS(R.string.export_range_three_months),
    SIX_MONTHS(R.string.export_range_six_months),
    CUSTOM(R.string.export_range_custom)
}

enum class ExportOrder(val displayNameRes: Int) {
    NEWEST_FIRST(R.string.export_order_newest),
    OLDEST_FIRST(R.string.export_order_oldest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsDialog(
    category: Category,
    onDismiss: () -> Unit,
    onExport: (ExportRange, ExportOrder, Instant?, Instant?, Boolean, String?) -> Unit,
) {
    var selectedRange by remember { mutableStateOf(ExportRange.ALL) }
    var selectedOrder by remember { mutableStateOf(ExportOrder.NEWEST_FIRST) }
    var includePhotos by remember { mutableStateOf(true) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // パスワード設定用
    var protectWithPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = password.length >= 6

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate ?: endDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDate = datePickerState.selectedDateMillis
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.decision))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDate = datePickerState.selectedDateMillis
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.decision))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_settings_title, stringResource(category.displayNameRes))) },
        text = {
            val scrollState = rememberScrollState()
            val showScrollIndicator by remember {
                derivedStateOf { scrollState.value < scrollState.maxValue }
            }
            Box {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- セキュリティ（最上位に移動） ---
                    Text(stringResource(R.string.security), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { protectWithPassword = !protectWithPassword }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.protect_pdf_with_password))
                        Switch(checked = protectWithPassword, onCheckedChange = { protectWithPassword = it })
                    }

                    if (protectWithPassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.pdf_password_label)) },
                            placeholder = { Text(stringResource(R.string.pdf_password_placeholder)) },
                            supportingText = {
                                if (!isPasswordValid && password.isNotEmpty()) {
                                    Text(stringResource(R.string.pdf_password_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.pdf_password_hint))
                                }
                            },
                            isError = !isPasswordValid && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 抽出範囲 ---
                    Text(stringResource(R.string.extract_range), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportRange.entries.forEach { range ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (range == selectedRange),
                                    onClick = {
                                        selectedRange = range
                                    },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (range == selectedRange), onClick = { selectedRange = range })
                            Text(text = stringResource(range.displayNameRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (selectedRange == ExportRange.CUSTOM) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(startDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: stringResource(R.string.start_date))
                            }
                            Text("〜")
                            OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: stringResource(R.string.end_date))
                            }
                        }
                    }

                    if (category.hasOption) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { includePhotos = !includePhotos }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.include_photos))
                            Switch(checked = includePhotos, onCheckedChange = { includePhotos = it })
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 並び順 ---
                    Text(stringResource(R.string.export_order), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportOrder.entries.forEach { order ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (order == selectedOrder),
                                    onClick = {
                                        selectedOrder = order
                                    },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (order == selectedOrder), onClick = { selectedOrder = order })
                            Text(text = stringResource(order.displayNameRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // スクロールが必要なことを示すインジケーター（下端に到達していない場合のみ表示）
                if (showScrollIndicator) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.more_items),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(
                        selectedRange,
                        selectedOrder,
                        startDate?.let { Instant.ofEpochMilli(it) },
                        endDate?.let { Instant.ofEpochMilli(it) },
                        includePhotos,
                        if (protectWithPassword) password else null
                    )
                },
                enabled = run {
                    val isCustomRangeValid = if (selectedRange == ExportRange.CUSTOM) (startDate != null || endDate != null) else true
                    val isPasswordSetupValid = if (protectWithPassword) isPasswordValid else true
                    isCustomRangeValid && isPasswordSetupValid
                }
            ) {
                Text(stringResource(R.string.create_pdf))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
