package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onExport: (ExportRange, ExportOrder, Instant?, Instant?, Boolean) -> Unit,
) {
    var selectedRange by remember { mutableStateOf(ExportRange.ALL) }
    var selectedOrder by remember { mutableStateOf(ExportOrder.NEWEST_FIRST) }
    var includePhotos by remember { mutableStateOf(true) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(selectedRange, selectedOrder, startDate?.let { Instant.ofEpochMilli(it) }, endDate?.let { Instant.ofEpochMilli(it) }, includePhotos)
                },
                enabled = if (selectedRange == ExportRange.CUSTOM) (startDate != null || endDate != null) else true
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
