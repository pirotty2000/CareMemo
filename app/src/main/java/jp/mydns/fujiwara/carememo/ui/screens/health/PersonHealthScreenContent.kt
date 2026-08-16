package jp.mydns.fujiwara.carememo.ui.screens.health

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
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.feature.HealthEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.health.HealthGraphView
import jp.mydns.fujiwara.carememo.ui.components.health.HealthHistoryItemBody
import jp.mydns.fujiwara.carememo.ui.components.health.HealthRecordDetailPane
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.PersonHealthPreviewState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme

/**
 * Screen：PersonHealthScreenContent
 *
 * 【役割】
 * 健康記録ドメインにおいて、Phone 版と Tablet 版で共通して使用される「履歴リスト」と「詳細・グラフペイン」のレイアウト基盤を提供します。
 *
 * 【主な機能】
 * ・マルチレイアウト対応：画面幅（isExpanded）に応じた 1 カラム / 2 カラム構成の動的な切り替え。
 * ・履歴・グラフ切り替え：Phone版における SegmentedButton を使用した表示モード制御。
 * ・履歴リスト表示：`PersonHistoryList` を用いた時系列データの描画とスワイプ削除。
 * ・詳細表示・編集：`HealthRecordDetailPane` による各指標（バイタル、血糖等）の入力・閲覧。
 * ・統計グラフ表示：`HealthGraphView` によるデータの可視化。
 *
 * 【全体像：レイアウト構成（Health Layout）】
 *
 * ■ PersonHealthScreenContent (★本コンポーネント)
 * │
 * ├─ [ Phone版 ] (Column 構成：トグル制御)
 * │    ├─ SegmentedButton (履歴 ↔ グラフ 切り替え)
 * │    └─ Box (コンテンツ：HistoryList または HealthGraphView)
 * │         └─ 編集時は HealthRecordDetailPane が最前面へ
 * │
 * └─ [ Tablet版 ] (Row 構成：2ペイン固定)
 *      ├─ 左側 (weight 1)：HistoryList (履歴リスト)
 *      └─ 右側 (weight 1.5)：HealthGraphView (グラフ) または HealthRecordDetailPane (詳細)
 */
@Composable
fun PersonHealthScreenContent(
    isExpanded: Boolean,
    uiState: PersonHealthUiState,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    onSelectedRecordIdChange: (String?) -> Unit,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    onExpandGraph: (Int) -> Unit,
    onEditClick: () -> Unit,
    onEditInputUpdate: ((HealthEditInput) -> HealthEditInput) -> Unit,
    onSaveClick: () -> Unit,
    onCancelEdit: () -> Unit,
    isAnyDialogOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val historyListState = rememberLazyListState()

    if (uiState.isLoading) {
        LoadingScreen(modifier = modifier)
    } else if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: 履歴リスト (比率 1)
            Box(modifier = Modifier.weight(1f).testTag("HealthScreen_HistoryList")) {
                PersonHistoryList(
                    records = uiState.records,
                    selectedRecordId = uiState.selectedRecordId,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = onDeleteSwipe,
                    isAnyDialogOpen = isAnyDialogOpen,
                    lazyListState = historyListState
                ) { record ->
                    HealthHistoryItemBody(category = uiState.currentCategory, record = record)
                }
                VerticalScrollIndicator(lazyListState = historyListState)
            }
            // 右側: グラフ または 詳細入力 (比率 1.5)
            Box(modifier = Modifier.weight(1.5f)) {
                if (uiState.selectedRecordId != null) {
                    Box(modifier = Modifier.testTag("HealthScreen_InputForm")) {
                        HealthRecordDetailPane(
                            uiState = uiState,
                            onCancel = onCancelEdit,
                            onEditClick = onEditClick,
                            onEditInputUpdate = onEditInputUpdate,
                            onSaveClick = onSaveClick
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
                                records = uiState.records,
                                categoryType = uiState.currentCategory,
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
        if (uiState.selectedRecordId != null) {
            Box(modifier = modifier.testTag("HealthScreen_InputForm")) {
                HealthRecordDetailPane(
                    uiState = uiState,
                    onCancel = onCancelEdit,
                    onEditClick = onEditClick,
                    onEditInputUpdate = onEditInputUpdate,
                    onSaveClick = onSaveClick
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
                        selected = uiState.preferredShowHistory,
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
                        selected = !uiState.preferredShowHistory,
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

                    if (uiState.preferredShowHistory) {
                        // 履歴表示
                        Box(modifier = Modifier.testTag("HealthScreen_HistoryList")) {
                            PersonHistoryList(
                                records = uiState.records,
                                onItemClick = onItemClick,
                                onDeleteSwipe = onDeleteSwipe,
                                isAnyDialogOpen = isAnyDialogOpen,
                                lazyListState = historyListState
                            ) { record ->
                                HealthHistoryItemBody(category = uiState.currentCategory, record = record)
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
                                    records = uiState.records,
                                    categoryType = uiState.currentCategory,
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
            uiState = PersonHealthUiState(
                records = state.records,
                isLoading = state.isLoading,
                currentCategory = state.category,
                preferredShowHistory = state.preferredShowHistory,
                selectedRecordId = state.selectedRecordId
            ),
            onPreferredShowHistoryChange = {},
            onSelectedRecordIdChange = {},
            onItemClick = {},
            onDeleteSwipe = {},
            onExpandGraph = {},
            onEditClick = {},
            onEditInputUpdate = {},
            onSaveClick = {},
            onCancelEdit = {},
            isAnyDialogOpen = false
        )
    }
}
