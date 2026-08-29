package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.SearchBox
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionDetailPane
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme

/**
 * Screen：PersonConditionScreenContent
 *
 * 【役割】
 * 所見記録ドメインにおいて、Phone 版と Tablet 版で共通して使用される「履歴リスト」と「詳細・編集ペイン」のレイアウト基盤を提供します。
 *
 * 【主な機能】
 * ・マルチレイアウト対応：画面幅（isExpanded）に応じた 1 カラム / 2 カラム構成の動的な切り替え。
 * ・履歴リスト表示：`ConditionList` を用いた時系列データの描画とスワイプ削除、検索バーの統合。
 * ・空状態管理：記録が存在しない場合の `EmptyState` 表示制御。
 * ・垂直スクロール補助：`VerticalScrollIndicator` によるリスト位置の可視化。
 *
 * 【全体像：レイアウト構成（Condition Layout）】
 *
 * @param isExpanded タブレット版（2カラム）として表示するかどうか
 * @param uiState UI 状態
 * @param onAction アクションハンドラ
 * @param isAnyDialogOpen 他のダイアログが開いているかどうか。スワイプ状態のリセットに使用。
 * @param modifier 修飾子
 */
@Composable
fun PersonConditionScreenContent(
    isExpanded: Boolean,
    uiState: PersonConditionUiState,
    onAction: (PersonConditionUiAction) -> Unit,
    isAnyDialogOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    if (uiState.isLoading) {
        LoadingScreen(modifier = modifier)
    } else if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("Condition_TabletLayout"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左側: 履歴リスト
            Column(
                modifier = Modifier.weight(1f).testTag("Condition_HistoryList"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 検索ボックス
                SearchBox(
                    query = uiState.searchQuery,
                    onQueryChange = { onAction(PersonConditionUiAction.SearchQueryChanged(it)) },
                    placeholder = stringResource(R.string.main_search_hint_short)
                )
                // 所見メモ・履歴一覧
                Box(modifier = Modifier.weight(1f)) {
                    if (uiState.filteredRecords.isEmpty()) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            description = stringResource(R.string.p_detail_empty_records_desc),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        ConditionList(
                            records = uiState.filteredRecords,
                            selectedId = uiState.selectedConditionId,
                            conditionPhotoMap = uiState.conditionPhotoMap,
                            isAnyDialogOpen = isAnyDialogOpen,
                            onSelect = { onAction(PersonConditionUiAction.SelectedIdChanged(it)) },
                            onDelete = { onAction(PersonConditionUiAction.DeleteRecordRequest(it)) },
                            lazyListState = lazyListState
                        )
                        VerticalScrollIndicator(lazyListState = lazyListState)
                    }
                }
            }
            // 右側・記録の詳細
            Box(
                modifier = Modifier
                    .weight(2f)
                    .padding(end = 16.dp) // 右端に余白を確保
                    .testTag("Condition_DetailPane")
            ) {
                ConditionDetailPane(
                    uiState = uiState,
                    onAction = onAction
                )
            }
        }
    } else {
        // スマホ用レイアウト
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 検索ボックス
            SearchBox(
                query = uiState.searchQuery,
                onQueryChange = { onAction(PersonConditionUiAction.SearchQueryChanged(it)) },
                modifier = Modifier.testTag("ConditionScreen_SearchBox")
            )
            // 所見メモ・履歴一覧
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.filteredRecords.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.p_detail_empty_records),
                        description = stringResource(R.string.p_detail_empty_records_desc),
                        icon = Icons.Outlined.Description
                    )
                } else {
                    ConditionList(
                        records = uiState.filteredRecords,
                        selectedId = uiState.selectedConditionId,
                        conditionPhotoMap = uiState.conditionPhotoMap,
                        isAnyDialogOpen = isAnyDialogOpen,
                        onSelect = { onAction(PersonConditionUiAction.SelectedIdChanged(it)) },
                        onDelete = { onAction(PersonConditionUiAction.DeleteRecordRequest(it)) },
                        lazyListState = lazyListState
                    )
                    VerticalScrollIndicator(lazyListState = lazyListState)
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
private fun PreviewPersonConditionScreenContent(
    @PreviewParameter(PersonConditionPreviewParameterProvider::class) state: PersonConditionPreviewState
) {
    CareMemoTheme {
        PersonConditionScreenContent(
            isExpanded = state.isExpanded,
            uiState = PersonConditionUiState(
                records = state.records,
                isLoading = state.isLoading,
                selectedConditionId = state.selectedRecordId
            ),
            onAction = {},
            isAnyDialogOpen = false
        )
    }
}
