package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonMedicationScreenContent
 *
 * 【画面名】
 * 利用者服薬記録画面（共通コンテンツ）
 *
 * 【役割】
 * Phone版とTablet版で共通して使用される、服薬記録のコアとなるUIコンポーネントを定義する。
 *
 * 【主な機能】
 * ・共通UI部品：日別の服薬状況表示、薬品名入力フィールド、時間帯選択などの再利用可能なコンポーネント。
 * ・ロジックの分離：レイアウトに依存しない表示ロジックの集約。
 * ・一貫した操作感：異なるデバイス間でも共通の入力・閲覧ルールを適用。
 *
 * 【備考】
 * デバイスごとのレイアウト（Phone/Tablet）は上位レベルで切り分け、本ファイルは純粋なコンテンツの構成を担当する。
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.ui.components.CalendarGrid
import jp.mydns.fujiwara.carememo.ui.components.MedicationHistoryTable
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatYearMonthHeader
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                    MedicationHistoryTable(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate
                    )
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
                    MedicationHistoryTable(
                        yearMonth = selectedMonth,
                        recordsByDate = recordsByDate
                    )
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
