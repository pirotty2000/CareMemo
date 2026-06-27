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
import jp.mydns.fujiwara.carememo.data.Category
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportRange(val displayName: String) {
    ALL("全ての記録"),
    LATEST("最新の1件のみ"),
    ONE_MONTH("直近 1ヶ月分"),
    THREE_MONTHS("直近 3ヶ月分"),
    SIX_MONTHS("直近 半年分"),
    CUSTOM("期間を指定する")
}

enum class ExportOrder(val displayName: String) {
    NEWEST_FIRST("新しい順"),
    OLDEST_FIRST("古い順")
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
                    Text("決定")
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
                    Text("決定")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF出力設定 (${category.displayName})") },
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
                    Text("セキュリティ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { protectWithPassword = !protectWithPassword }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("PDFをパスワードで保護する")
                        Switch(checked = protectWithPassword, onCheckedChange = { protectWithPassword = it })
                    }

                    if (protectWithPassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("PDF閲覧用パスワード") },
                            placeholder = { Text("6桁以上の数字を推奨") },
                            supportingText = {
                                if (!isPasswordValid && password.isNotEmpty()) {
                                    Text("パスワードは6文字以上で入力してください", color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("PDFを開く際に必要になります")
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
                                        contentDescription = if (isPasswordVisible) "非表示" else "表示"
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 抽出範囲 ---
                    Text("抽出範囲", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                            Text(text = range.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
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
                                Text(startDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "開始日")
                            }
                            Text("〜")
                            OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: "終了日")
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
                            Text("写真を印刷に含める")
                            Switch(checked = includePhotos, onCheckedChange = { includePhotos = it })
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 並び順 ---
                    Text("並び順", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                            Text(text = order.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // スクロールが必要なことを示すインジケーター（下端に到達していない場合のみ表示）
                if (showScrollIndicator) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "さらに下に項目があります",
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
                Text("PDFを作成")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("キャンセル")
            }
        }
    )
}
