package jp.mydns.fujiwara.carememo.ui.screens.detail.health

/**
 * Screen : PersonHealthScreenContent
 *
 * 【画面名】：
 * 利用者健康記録画面（共通コンテンツレイアウト）
 *
 * 【役割】：
 * 健康記録（身長・体重、バイタル、血糖値・HbA1c）において、Phone版とTablet版で共通して使用される表示・入力ロジックの基盤を提供する。
 * デバイスの形状（1カラム/2カラム）に応じた動的なレイアウト切り替えを担当する。
 *
 * 【主な機能】：
 * ・マルチレイアウト制御（Phone版のタブ切り替え型とTablet版の2カラム固定型を1つのコンポーネントで管理）
 * ・履歴リスト表示（PersonHistoryListを用いた時系列データの描画とスワイプ削除の統合）
 * ・詳細入力・編集（HealthRecordDetailPaneによるカテゴリ別の入力フォーム表示）
 * ・統計グラフ表示（HealthGraphViewによるデータの可視化と拡大表示連携）
 *
 * 【遷移】：
 * なし（親画面である PersonHealthScreenPhone/Tablet が制御）
 *
 * 【使用するViewModel】：
 * なし（Stateless化済み。親からラムダ経由で操作を実行）
 *
 * 【使用するComponents】：
 * ・detail/health/HealthGraphView.kt
 * ・detail/health/HealthRecordDetailPane (PersonHealthComponents.kt)
 * ・detail/health/PersonHistoryList (PersonHealthComponents.kt)
 * ・base/LoadingScreen.kt
 * ・base/VerticalScrollIndicator.kt
 *
 * 【備考】：
 * このコンポーネントをStatelessに保つことで、Phone/Tabletの両レイアウトでのプレビュー表示とロジックの共通化を両立している。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.detail.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.detail.health.HealthGraphView
import jp.mydns.fujiwara.carememo.ui.components.detail.health.HealthHistoryItemBody
import jp.mydns.fujiwara.carememo.ui.components.detail.health.HealthRecordDetailPane
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

@Composable
fun PersonHealthScreenContent(
    isExpanded: Boolean,
    personId: Int,
    records: List<Any>,
    isLoading: Boolean,
    currentCategory: Category,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: Int,
    onSelectedRecordIdChange: (Int) -> Unit,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    onExpandGraph: (Int) -> Unit,
    onSaveRecord: (Any) -> Unit,
    isAnyDialogOpen: Boolean
) {
    val historyListState = rememberLazyListState()

    // HistoryRecord のリストを安定化（再コンポーズごとに新しいリストが生成されるのを防ぐ）
    val historyRecords = remember(records) {
        records.filterIsInstance<HistoryRecord>()
    }

    if (isLoading) {
        LoadingScreen()
    } else if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: 履歴リスト (比率 1)
            Box(modifier = Modifier.weight(1f)) {
                PersonHistoryList(
                    records = historyRecords,
                    selectedRecordId = selectedRecordId,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = onDeleteSwipe,
                    isAnyDialogOpen = isAnyDialogOpen,
                    lazyListState = historyListState
                ) { record ->
                    HealthHistoryItemBody(category = currentCategory, record = record)
                }
                VerticalScrollIndicator(lazyListState = historyListState)
            }
            // 右側: グラフ または 詳細入力 (比率 1.5)
            Box(modifier = Modifier.weight(1.5f)) {
                if (selectedRecordId != -1) {
                    HealthRecordDetailPane(
                        personId = personId,
                        category = currentCategory,
                        recordId = selectedRecordId,
                        records = historyRecords,
                        onCancel = { onSelectedRecordIdChange(-1) },
                        onSaveRecord = onSaveRecord
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
                personId = personId,
                category = currentCategory,
                recordId = selectedRecordId,
                records = historyRecords,
                onCancel = { onSelectedRecordIdChange(-1) },
                onSaveRecord = onSaveRecord
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
                        // 履歴表示
                        PersonHistoryList(
                            records = historyRecords,
                            onItemClick = onItemClick,
                            onDeleteSwipe = onDeleteSwipe,
                            isAnyDialogOpen = isAnyDialogOpen,
                            lazyListState = historyListState
                        ) { record ->
                            HealthHistoryItemBody(category = currentCategory, record = record)
                        }
                        VerticalScrollIndicator(lazyListState = historyListState)
                    } else {
                        // グラフ表示
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
