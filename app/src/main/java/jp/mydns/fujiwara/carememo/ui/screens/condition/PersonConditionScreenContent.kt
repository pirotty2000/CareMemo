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
 * 最終更新日: 2026/07/20 (UUID対応)
 */

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
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.base.SearchBox
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionDetailPane
import jp.mydns.fujiwara.carememo.ui.components.condition.ConditionList
import kotlinx.collections.immutable.ImmutableList

/**
 * 全体像：利用者所見記録（Condition）
 *
 * ■ PersonConditionScreenContent (画面全体の器)
 * ├─【一覧セクション】
 * │  └─ [1] ConditionList (所見記録リスト：PersonConditionComponents.kt)
 * │       └─ ■ ui/components/common/HistoryComponents.kt の PersonHistoryList (共通履歴リストの枠)
 * │            └─ [1-1] ConditionMemoContent (履歴1行分の要約：タイトル・本文・写真アイコン)
 * └─【詳細セクション】
 *      └─ [2] ConditionDetailPane (詳細・編集パネル：PersonConditionComponents.kt)
 *           ├─ [2-1] ConditionRecordEditForm (【編集モード】入力フォーム)
 *           │    ├─ DateTimeInputFields (日時入力)
 *           │    ├─ AppTextField (タイトル、記録者、本文/音声入力対応)
 *           │    ├─ [2-1-1] PhotoGrid (写真一覧：削除ボタンあり)
 *           │    └─ <アクション> キャンセル、保存ボタン
 *           ├─ [2-2] ConditionRecordDisplayCard (【閲覧モード】詳細表示用)
 *           │    ├─ <ヘッダー> 戻るボタン、タイトル、編集ボタン
 *           │    ├─ <内容部> 記録日時、タイトル、本文、記録者名
 *           │    └─ [2-2-1] PhotoGrid (写真一覧：閲覧・フルスクリーン遷移)
 *           └─ [2-3] OrphanedPhotoSelectionDialog (迷子写真の再登録用ダイアログ)
 */

@Composable
fun PersonConditionScreenContent(
    isExpanded: Boolean,
    records: ImmutableList<ConditionAtVisit>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedId: String?,
    onSelectedIdChange: (String?) -> Unit,
    conditionPhotoMap: Map<String, Boolean>,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    modifier: Modifier = Modifier,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit,
    orphanedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    onNavigateToFullScreen: (String, String) -> Unit,
    onMicClick: () -> Unit,
) {
    val lazyListState = rememberLazyListState()

    // ConditionAtVisit のリスト
    val conditionRecords = records

    if (isLoading) {
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
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.main_search_hint_short)
                )
                // 所見メモ・履歴一覧
                Box(modifier = Modifier.weight(1f)) {
                    if (conditionRecords.isEmpty()) {
                        EmptyState(
                            message = stringResource(R.string.p_detail_empty_records),
                            description = stringResource(R.string.p_detail_empty_records_desc),
                            icon = Icons.Outlined.Description
                        )
                    } else {
                        ConditionList(
                            records = conditionRecords,
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
            // 右側・記録の詳細
            Box(
                modifier = Modifier
                    .weight(2f)
                    .padding(end = 16.dp) // 右端に余白を確保
                    .testTag("Condition_DetailPane")
            ) {
                ConditionDetailPane(
                    conditionId = selectedId ?: "",
                    records = conditionRecords,
                    photos = photos,
                    isProcessing = isProcessing,
                    defaultRecorderName = defaultRecorderName,
                    onSaveRecord = onSaveRecord,
                    onDeletePhoto = onDeletePhoto,
                    onSelectedIdChange = { onSelectedIdChange(it) },
                    onCancel = { onSelectedIdChange(null) },
                    onAddPhotoClick = onAddPhotoClick,
                    onPickPhotoClick = onPickPhotoClick,
                    onReattachPhoto = onReattachPhoto,
                    orphanedPhotos = orphanedPhotos,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = onMicClick
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
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.testTag("ConditionScreen_SearchBox")
            )
            // 所見メモ・履歴一覧
            Box(modifier = Modifier.weight(1f)) {
                if (conditionRecords.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.p_detail_empty_records),
                        description = stringResource(R.string.p_detail_empty_records_desc),
                        icon = Icons.Outlined.Description
                    )
                } else {
                    ConditionList(
                        records = conditionRecords,
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
