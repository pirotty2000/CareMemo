package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonHealthScreenContent
 *
 * 【画面名】
 * 利用者健康記録画面（共通コンテンツ）
 *
 * 【役割】
 * 健康記録（カテゴリA/C/D）で共通して使用されるUI部品や、カテゴリごとの入力・表示ロジックを管理する。
 *
 * 【主な機能】
 * ・カテゴリ別表示：バイタル、血糖値、身体計測の各記録項目に応じた入力フォームの動的生成。
 * ・グラフ統合：HealthGraphViewを用いた統計データの可視化。
 * ・履歴統合：PersonHistoryListによる時系列データの表示と編集・削除アクションの提供。
 *
 * 【備考】
 * Phone版とTablet版のレイアウトの差異を吸収し、記録データのハンドリングと表示ロジックを共通化している。
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.HealthGraphView
import jp.mydns.fujiwara.carememo.ui.components.HealthHistoryItemBody
import jp.mydns.fujiwara.carememo.ui.components.HealthRecordDetailPane
import jp.mydns.fujiwara.carememo.ui.components.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

@Composable
fun PersonHealthScreenContent(
    isExpanded: Boolean,
    personId: Int,
    records: List<Any>,
    currentCategory: Category,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: Int,
    onSelectedRecordIdChange: (Int) -> Unit,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    onExpandGraph: (Int) -> Unit,
    healthViewModel: PersonHealthViewModel,
    isAnyDialogOpen: Boolean
) {
    // HistoryRecord のリストを安定化（再コンポーズごとに新しいリストが生成されるのを防ぐ）
    val historyRecords = remember(records) {
        records.filterIsInstance<HistoryRecord>()
    }

    if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: 履歴リスト (比率 1)
            Box(modifier = Modifier.weight(1f)) {
                PersonHistoryList(
                    records = historyRecords,
                    selectedRecordId = selectedRecordId,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = onDeleteSwipe,
                    isAnyDialogOpen = isAnyDialogOpen
                ) { record ->
                    HealthHistoryItemBody(category = currentCategory, record = record)
                }
            }
            // 右側: グラフ または 詳細入力 (比率 1.5)
            Box(modifier = Modifier.weight(1.5f)) {
                if (selectedRecordId != -1) {
                    HealthRecordDetailPane(
                        healthViewModel = healthViewModel,
                        personId = personId,
                        category = currentCategory,
                        recordId = selectedRecordId,
                        records = historyRecords,
                        onCancel = { onSelectedRecordIdChange(-1) },
                        onSaveSuccess = { onSelectedRecordIdChange(-1) }
                    )
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp)
                        ) {
                            HealthGraphView(
                                records = records,
                                categoryType = currentCategory,
                                onExpandGraph = onExpandGraph
                            )
                        }
                        if (scrollState.maxValue > 0) {
                            VerticalScrollIndicator(scrollState)
                        }
                    }
                }
            }
        }
    } else {
        // --- スマホ: 1カラム・切り替えレイアウト ---
        if (selectedRecordId != -1) {
            BackHandler { onSelectedRecordIdChange(-1) }
            HealthRecordDetailPane(
                healthViewModel = healthViewModel,
                personId = personId,
                category = currentCategory,
                recordId = selectedRecordId,
                records = historyRecords,
                onCancel = { onSelectedRecordIdChange(-1) },
                onSaveSuccess = { onSelectedRecordIdChange(-1) }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 表示切り替えボタン
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_history)) }
                    SegmentedButton(
                        selected = !preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_graph)) }
                }

                // コンテンツ表示
                Box(modifier = Modifier.weight(1f)) {
                    if (preferredShowHistory) {
                        PersonHistoryList(
                            records = historyRecords,
                            onItemClick = onItemClick,
                            onDeleteSwipe = onDeleteSwipe,
                            isAnyDialogOpen = isAnyDialogOpen
                        ) { record ->
                            HealthHistoryItemBody(category = currentCategory, record = record)
                        }
                    } else {
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 16.dp)
                            ) {
                                HealthGraphView(
                                    records = records,
                                    categoryType = currentCategory,
                                    onExpandGraph = onExpandGraph
                                )
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                            if (scrollState.maxValue > 0) {
                                VerticalScrollIndicator(scrollState)
                            }
                        }
                    }
                }
            }
        }
    }
}
