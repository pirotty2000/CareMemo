package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import java.time.LocalDate
import java.time.YearMonth

/**
 * 全体像：服薬管理（Medication）
 *
 * ■ ui/screens/medication/PersonMedicationScreenContent.kt の PersonMedicationScreenContent (画面全体の器)
 * ├── [レイアウト制御：Phone版 (カレンダー/履歴 切り替え) ・ Tablet版 (2カラム固定)]
 * ├──【カレンダー表示】
 * │  └─ [1] CalendarGrid (月間グリッド：PersonMedicationComponents.kt)
 * │       └─ [1-1] DayCell (1日分のセル：タップで入力ダイアログ起動)
 * │            └─ [1-1-1] MedicationStatusIcon (朝/昼/夕/寝る前 4スロットの状況アイコン)
 * ├──【履歴テーブル表示】
 * │  └─ [2] MedicationHistoryTable (月間一覧テーブル：PersonMedicationComponents.kt)
 * │       └─ <テーブル行> 日付ごとの服薬状況を記号（○/△/×/－）で一覧表示
 * └──【入力・編集セクション】
 *      └─ [3] MedicationInputDialog (登録・編集用ダイアログ：PersonMedicationComponents.kt)
 *           ├─ [3-1] MedicationRow (時間枠ごとの入力行：朝・昼・夕・寝る前)
 *           │    └─ StatusChip (服薬状況選択：未・介助・服用)
 *           └─ [3-2] DateTimeInputFields (特定の時間枠の「記録時刻」を詳細編集)
 *                └─ <アクション> キャンセル、保存ボタン
 */

@Composable
fun PersonMedicationScreenContent(
    isExpanded: Boolean,
    selectedMonth: YearMonth,
    isLoading: Boolean,
    recordsByDate: ImmutableMap<String, ImmutableList<MedicationRecord>>,
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
                val calendarScrollState = rememberScrollState()
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(calendarScrollState)
                    ) {
                        CalendarGrid(
                            yearMonth = selectedMonth,
                            recordsByDate = recordsByDate,
                            onDayClick = onDayClick
                        )
                    }
                    VerticalScrollIndicator(scrollState = calendarScrollState)
                }
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
                    val calendarScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(calendarScrollState)
                    ) {
                        CalendarGrid(
                            yearMonth = selectedMonth,
                            recordsByDate = recordsByDate,
                            onDayClick = onDayClick
                        )
                    }
                    VerticalScrollIndicator(scrollState = calendarScrollState)
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
            recordsByDate = persistentMapOf(),
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
            recordsByDate = persistentMapOf(),
            isHistoryMode = false,
            onHistoryModeChange = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onDayClick = {}
        )
    }
}
