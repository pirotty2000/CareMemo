package jp.mydns.fujiwara.carememo.ui.screens.health

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
 * ・health/HealthGraphView.kt
 * ・health/HealthRecordDetailPane (PersonHealthComponents.kt)
 * ・health/PersonHistoryList (PersonHealthComponents.kt)
 * ・base/LoadingScreen.kt
 * ・base/VerticalScrollIndicator.kt
 *
 * 【備考】：
 * このコンポーネントをStatelessに保つことで、Phone/Tabletの両レイアウトでのプレビュー表示とロジックの共通化を両立している。
 *
 * ---
 * 最終更新日: 2026/07/20 (UUID対応)
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.health.HealthGraphView
import jp.mydns.fujiwara.carememo.ui.components.health.HealthHistoryItemBody
import jp.mydns.fujiwara.carememo.ui.components.health.HealthRecordDetailPane
import kotlinx.collections.immutable.ImmutableList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.PersonHealthPreviewState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme

/**
 * 全体像
 *   ★ PersonHealthScreenContent
 *    ├─【左側 / 上部】
 *    │   └─ ui/components/common/HistoryComponents.kt の PersonHistoryList
 *    │     └─ [1] ui/components/health/PersonHealthComponents.kt の HealthHistoryItemBody (履歴1行分の要約)
 *    │          ├─ [1-1] HeightWeightRecordItemContent (身長・体重の要約)
 *    │          ├─ [1-2] VitalRecordItemContent (バイタルの要約)
 *    │          │    └─ [1-2-1] VitalStatusIndicator
 *    │          └─ [1-3] GlucoseRecordItemContent (血糖値の要約)
 *    └─【右側 / 詳細】
 *         └─ [2] ui/components/health/PersonHealthComponents.kt の HealthRecordDetailPane (詳細・編集パネル)
 *              ├─ [2-1] HealthRecordEditForm (入力フォーム)
 *              └─ [2-2] HealthRecordDisplayCard (閲覧用カード)
 *                   └─ [2-2-1] HealthDetailContent (カテゴリ分岐)
 *                       ├── [2-2-1-1] HeightWeightDetailContent x DetailRow
 *                       ├── [2-2-1-2] VitalDetailContent x DetailRow
 *                       └── [2-2-1-3] GlucoseDetailContent x DetailRow
 **/

@Composable
fun PersonHealthScreenContent(
    isExpanded: Boolean,
    records: ImmutableList<HistoryRecord>,
    isLoading: Boolean,
    currentCategory: Category,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: String?,
    onSelectedRecordIdChange: (String?) -> Unit,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    onExpandGraph: (Int) -> Unit,
    onSaveRecord: (Category, String, Instant, Map<String, Any?>) -> Unit,
    isAnyDialogOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val historyListState = rememberLazyListState()

    // HistoryRecord のリストを安定化
    val historyRecords = records

    if (isLoading) {
        LoadingScreen(modifier = modifier)
    } else if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: 履歴リスト (比率 1)
            Box(modifier = Modifier.weight(1f).testTag("HealthScreen_HistoryList")) {
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
                if (selectedRecordId != null) {
                    Box(modifier = Modifier.testTag("HealthScreen_InputForm")) {
                        HealthRecordDetailPane(
                            category = currentCategory,
                            recordId = selectedRecordId,
                            records = historyRecords,
                            onCancel = { onSelectedRecordIdChange(null) },
                            onSaveRecord = onSaveRecord
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize().testTag("HealthScreen_GraphArea")) {
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
        if (selectedRecordId != null) {
            Box(modifier = modifier.testTag("HealthScreen_InputForm")) {
            HealthRecordDetailPane(
                category = currentCategory,
                recordId = selectedRecordId,
                records = historyRecords,
                onCancel = { onSelectedRecordIdChange(null) },
                onSaveRecord = onSaveRecord
            )
            }
        } else {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 表示切り替えボタン
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().testTag("HealthScreen_HistoryGraphSwitch")
                ) {
                    SegmentedButton(
                        selected = preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("HealthScreen_Tab_History")
                    ) { Text(stringResource(R.string.common_tab_history)) }
                    SegmentedButton(
                        selected = !preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("HealthScreen_Tab_Graph")
                    ) { Text(stringResource(R.string.common_tab_graph)) }
                }

                // コンテンツ表示
                Box(modifier = Modifier.weight(1f)) {

                    if (preferredShowHistory) {
                        // 履歴表示
                        Box(modifier = Modifier.testTag("HealthScreen_HistoryList")) {
                            PersonHistoryList(
                                records = historyRecords,
                                onItemClick = onItemClick,
                                onDeleteSwipe = onDeleteSwipe,
                                isAnyDialogOpen = isAnyDialogOpen,
                                lazyListState = historyListState
                            ) { record ->
                                HealthHistoryItemBody(category = currentCategory, record = record)
                            }
                        }
                        VerticalScrollIndicator(lazyListState = historyListState)
                    } else {
                        // グラフ表示
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize().testTag("HealthScreen_GraphArea")) {
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

////////////////////////////////////////////////////////////////////////////////////////////////////
// Previews
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun PreviewPersonHealthScreenContent(
    @PreviewParameter(PersonHealthPreviewParameterProvider::class) state: PersonHealthPreviewState
) {
    CareMemoTheme {
        PersonHealthScreenContent(
            isExpanded = false,
            records = state.records,
            isLoading = state.isLoading,
            currentCategory = state.category,
            preferredShowHistory = state.preferredShowHistory,
            onPreferredShowHistoryChange = {},
            selectedRecordId = state.selectedRecordId,
            onSelectedRecordIdChange = {},
            onItemClick = {},
            onDeleteSwipe = {},
            onExpandGraph = {},
            onSaveRecord = { _, _, _, _ -> },
            isAnyDialogOpen = false
        )
    }
}
