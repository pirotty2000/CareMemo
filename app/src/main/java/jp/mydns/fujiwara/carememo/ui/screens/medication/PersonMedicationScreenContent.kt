package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.medication.CalendarGrid
import jp.mydns.fujiwara.carememo.ui.components.medication.MedicationHistoryTable
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatYearMonthHeader
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun PersonMedicationScreenContent(
    isExpanded: Boolean,
    selectedMonth: YearMonth,
    isLoading: Boolean,
    recordsByDate: Map<String, List<MedicationRecord>>,
    isHistoryMode: Boolean,
    onHistoryModeChange: (Boolean) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    // 記録が1件でもあるか判定
    val hasAnyRecord = recordsByDate.values.any { it.isNotEmpty() }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().testTag("Medication_Loading")) {
            LoadingScreen()
        }
    } else if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: カレンダー
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CalendarGrid(
                    yearMonth = selectedMonth,
                    recordsByDate = recordsByDate,
                    onDayClick = onDayClick
                )
            }
            // 右側: 履歴（テーブル）
            Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 年月セレクタを履歴の表の上に配置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier.testTag("Medication_MonthPrev_Tablet")
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "前月")
                    }
                    Text(
                        text = formatYearMonthHeader(selectedMonth),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("Medication_MonthText_Tablet")
                    )
                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.testTag("Medication_MonthNext_Tablet")
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (!hasAnyRecord) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        val historyTableState = rememberLazyListState()
                        MedicationHistoryTable(
                            yearMonth = selectedMonth,
                            recordsByDate = recordsByDate,
                            lazyListState = historyTableState
                        )
                        VerticalScrollIndicator(lazyListState = historyTableState)
                    }
                }
            }
        }
    } else {
        // --- スマホ: 1カラム・切り替えレイアウト ---
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 月の選択
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.testTag("Medication_MonthPrev_Phone")
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "前月")
                }
                Text(
                    text = formatYearMonthHeader(selectedMonth),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("Medication_MonthText_Phone")
                )
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag("Medication_MonthNext_Phone")
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
                }
            }

            // 表示切り替え
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Medication_ModeSegment")
            ) {
                SegmentedButton(
                    selected = !isHistoryMode,
                    onClick = { onHistoryModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(stringResource(R.string.common_tab_calendar))
                }
                SegmentedButton(
                    selected = isHistoryMode,
                    onClick = { onHistoryModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(stringResource(R.string.common_tab_history))
                }
            }

            if (isHistoryMode) {
                Text(
                    text = stringResource(R.string.p_med_msg_no_edit_in_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.p_med_msg_tap_to_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // コンテンツ
            Box(modifier = Modifier.weight(1f)) {
                if (isHistoryMode) {
                    if (!hasAnyRecord) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        val historyTableState = rememberLazyListState()
                        MedicationHistoryTable(
                            yearMonth = selectedMonth,
                            recordsByDate = recordsByDate,
                            lazyListState = historyTableState
                        )
                        VerticalScrollIndicator(lazyListState = historyTableState)
                    }
                } else {
                    CalendarGrid(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate,
                        onDayClick = onDayClick
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun PersonMedicationScreenContentPhonePreview() {
    CareMemoTheme {
        PersonMedicationScreenContent(
            isExpanded = false,
            selectedMonth = YearMonth.now(),
            isLoading = false,
            recordsByDate = emptyMap(),
            isHistoryMode = false,
            onHistoryModeChange = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDayClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800)
@Composable
fun PersonMedicationScreenContentTabletPreview() {
    CareMemoTheme {
        PersonMedicationScreenContent(
            isExpanded = true,
            selectedMonth = YearMonth.now(),
            isLoading = false,
            recordsByDate = emptyMap(),
            isHistoryMode = false,
            onHistoryModeChange = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDayClick = {}
        )
    }
}
