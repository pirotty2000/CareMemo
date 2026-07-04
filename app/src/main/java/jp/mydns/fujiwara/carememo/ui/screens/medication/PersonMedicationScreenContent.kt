package jp.mydns.fujiwara.carememo.ui.screens.medication

/**
 * Screen : PersonMedicationScreenContent
 *
 * 【画面名】：
 * 利用者服薬記録画面（共通コンテンツレイアウト）
 *
 * 【役割】：
 * 服薬記録（カテゴリC）において、Phone版とTablet版で共通して使用される表示・入力ロジックの基盤を提供する。
 *
 * 【主な機能】：
 * ・マルチレイアウト制御（Phone版のカレンダー/履歴切り替えと、Tablet版の同時表示を管理）
 * ・月間ナビゲーション：年月選択の共通UI提供。
 * ・表示コンポーネントの統合：[CalendarGrid] と [MedicationHistoryTable] の出し分け。
 *
 * 【遷移】：
 * なし（親画面である PersonMedicationScreenPhone/Tablet が制御）
 *
 * 【使用するViewModel】：
 * なし（Stateless化済み。親からラムダ経由で操作を実行）
 *
 * 【使用するComponents】：
 * ・detail/medication/CalendarGrid (PersonMedicationComponents.kt)
 * ・detail/medication/MedicationHistoryTable (PersonMedicationComponents.kt)
 * ・base/LoadingScreen.kt
 * ・base/VerticalScrollIndicator.kt
 *
 * 【備考】：
 * このコンポーネントをStatelessに保つことで、Phone/Tabletの両レイアウトでのプレビュー表示とロジックの共通化を両立している。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.MedicationRecord
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
    if (isLoading) {
        LoadingScreen()
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
                    IconButton(onClick = onPreviousMonth) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "前月")
                    }
                    Text(
                        text = formatYearMonthHeader(selectedMonth),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "前月")
                }
                Text(
                    text = formatYearMonthHeader(selectedMonth),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "次月")
                }
            }

            // 表示切り替え
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                    Text("カレンダー")
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
                    Text("履歴")
                }
            }

            if (isHistoryMode) {
                Text(
                    text = "※ ここでは記録の編集はできません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "※ 日付のセルをタップして記録を追加／編集しましょう",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // コンテンツ
            Box(modifier = Modifier.weight(1f)) {
                if (isHistoryMode) {
                    val historyTableState = rememberLazyListState()
                    MedicationHistoryTable(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate,
                        lazyListState = historyTableState
                    )
                    VerticalScrollIndicator(lazyListState = historyTableState)
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
