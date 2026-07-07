package jp.mydns.fujiwara.carememo.ui.screens.condition

/**
 * Screen : PersonConditionScreenContent
 *
 * 【画面名】：
 * 利用者所見記録画面（共通コンテンツレイアウト）
 *
 * 【役割】：
 * 所見記録（カテゴリB）において、Phone版とTablet版で共通して使用される表示・入力ロジックの基盤を提供する。
 * デバイスの形状（1カラム/2カラム）に応じた動的なレイアウト切り替えを担当する。
 *
 * 【主な機能】：
 * ・マルチレイアウト制御（Phone版の表示切り替え型とTablet版の2ペイン固定型を管理）
 * ・履歴リスト表示（ConditionListを用いた時系列データの描画とスワイプ削除の統合）
 * ・詳細入力・編集（ConditionDetailPaneによる入力フォームと写真管理の提供）
 * ・空状態の管理（記録がない場合の EmptyState 表示制御）
 *
 * 【遷移】：
 * なし（親画面である PersonConditionScreenPhone/Tablet が制御）
 *
 * 【使用するViewModel】：
 * なし（Stateless化済み。親からラムダ経由で操作を実行）
 *
 * 【使用するComponents】：
 * ・detail/condition/ConditionList (PersonConditionComponents.kt)
 * ・detail/condition/ConditionDetailPane (PersonConditionComponents.kt)
 * ・base/EmptyState.kt
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.SearchBox
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionDetailPane
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionList

@Composable
fun PersonConditionScreenContent(
    isExpanded: Boolean,
    personId: Int,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedId: Int,
    onSelectedIdChange: (Int?) -> Unit,
    conditionPhotoMap: Map<Int, Boolean>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (ConditionAtVisit, (Int) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onAddPhotoClick: () -> Unit,
    onNavigateToFullScreen: (Int, Int) -> Unit,
    onMicClick: () -> Unit,
) {
    val lazyListState = rememberLazyListState()

    // ConditionAtVisit のリストをフィルタリング
    val conditionRecords = remember(records) {
        records.filterIsInstance<ConditionAtVisit>()
    }

    if (isLoading) {
        LoadingScreen()
    } else if (isExpanded) {
        // タブレット用レイアウト (2ペイン)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBox(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (records.isEmpty()) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            description = stringResource(R.string.p_detail_empty_records_desc),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        ConditionList(
                            records = records,
                            selectedId = selectedId,
                            conditionPhotoMap = conditionPhotoMap,
                            isAnyDialogOpen = isAnyDialogOpen,
                            onSelect = { onSelectedIdChange(it) },
                            onDelete = onDeleteRecord,
                            lazyListState = lazyListState
                        )
                        VerticalScrollIndicator(lazyListState = lazyListState)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .padding(end = 16.dp) // 右端に余白を確保
            ) {
                ConditionDetailPane(
                    personId = personId,
                    conditionId = selectedId,
                    records = conditionRecords,
                    photos = photos,
                    isProcessing = isProcessing,
                    defaultRecorderName = defaultRecorderName,
                    onSaveRecord = onSaveRecord,
                    onDeletePhoto = onDeletePhoto,
                    onSelectedIdChange = { onSelectedIdChange(it) },
                    onAddPhotoClick = onAddPhotoClick,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = onMicClick
                )
            }
        }
    } else {
        // スマホ用レイアウト (切り替え)
        if (selectedId != -1) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ConditionDetailPane(
                    personId = personId,
                    conditionId = selectedId,
                    records = conditionRecords,
                    photos = photos,
                    isProcessing = isProcessing,
                    defaultRecorderName = defaultRecorderName,
                    onSaveRecord = onSaveRecord,
                    onDeletePhoto = onDeletePhoto,
                    onSelectedIdChange = { onSelectedIdChange(it) },
                    onAddPhotoClick = onAddPhotoClick,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = onMicClick
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBox(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (records.isEmpty()) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            description = stringResource(R.string.p_detail_empty_records_desc),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        ConditionList(
                            records = records,
                            selectedId = selectedId,
                            conditionPhotoMap = conditionPhotoMap,
                            isAnyDialogOpen = isAnyDialogOpen,
                            onSelect = { onSelectedIdChange(it) },
                            onDelete = onDeleteRecord,
                            lazyListState = lazyListState
                        )
                        VerticalScrollIndicator(lazyListState = lazyListState)
                    }
                }
            }
        }
    }
}
