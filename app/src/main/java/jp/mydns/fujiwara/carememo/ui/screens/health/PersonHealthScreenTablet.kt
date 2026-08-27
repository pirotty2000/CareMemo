package jp.mydns.fujiwara.carememo.ui.screens.health

/**
 * Screen : PersonHealthScreenTablet
 *
 * 【画面名】
 * 利用者健康記録画面（タブレット版）
 *
 * 【役割】
 * 横長画面（Expandedクラス）に最適化された健康記録UI提供し、バイタルや記録履歴を効率的に管理する。
 *
 * 【主な機能】
 * ・2ペインレイアウト：左側に履歴リスト、右側に詳細入力と統計グラフを配置。
 * ・マルチビュー：履歴データを確認しながら、同時にグラフでの推移分析や新規データの入力が可能。
 * ・高効率なナビゲーション：サイドバーまたは拡張タブによる素早いカテゴリ切り替え。
 *
 * 【遷移】
 * ← PersonHealthScreen（呼び出し元）
 *
 * 【備考】
 * 広い画面領域を活用し、記録作業と分析作業を同一画面内で完結させることで操作ステップを削減している。
 * 
 * ---
 * 最終更新日: 2026/07/20 (UUID対応)
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.PersonHealthPreviewState
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme

/**
 * Screen：PersonHealthScreenTablet
 *
 * 【役割】
 * タブレット等の広い画面（WindowWidthSizeClass.Expanded）向けに最適化された健康記録画面です。
 * 履歴データと統計分析・詳細入力を並列に扱うことができ、管理作業の効率を最大化します。
 *
 * 【主な機能】
 * ・2ペインレイアウト：左側に履歴リスト、右側にグラフまたは詳細入力パネルを常時固定配置。
 * ・マルチタスク：過去の記録を参照しながらの分析や入力が可能。
 * ・ナビゲーション統合：TopAppBar へのタイトル、戻るボタン、新規追加ボタン、および PDF 出力ボタンの配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenTablet(
    uiState: PersonHealthUiState,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    onAction: (PersonHealthUiAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("HealthScreen_TabletContent"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = stringResource(R.string.health_title)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { onAction(PersonHealthUiAction.Back) },
                            modifier = Modifier.testTag("HealthScreen_BackButton")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(onClick = { onAction(PersonHealthUiAction.SelectedRecordIdChanged(AppSpecifications.Id.NEW_RECORD_ID)) }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_create_new))
                        }
                        IconButton(
                            onClick = { onAction(PersonHealthUiAction.ShowPdfSettings) },
                            modifier = Modifier.testTag("HealthScreen_PdfButton")
                        ) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = stringResource(R.string.common_pdf_export)
                            )
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = uiState.currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { onAction(PersonHealthUiAction.NavigateToCategory(it)) },
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AppDeleteConfirmDialog(
                onDismiss = { recordToDelete = null },
                onDelete = {
                    recordToDelete?.let {
                        if (uiState.selectedRecordId == it.id) onAction(PersonHealthUiAction.SelectedRecordIdChanged(null))
                        onAction(PersonHealthUiAction.DeleteRecord(it))
                    }
                    recordToDelete = null
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if ((uiState.records.isEmpty() && uiState.selectedRecordId == null && !uiState.isLoading)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(R.string.p_detail_empty_records),
                        description = stringResource(R.string.p_detail_empty_records_desc),
                        icon = Icons.Outlined.Description
                    )
                }
            } else {
                PersonHealthScreenContent(
                    isExpanded = true,
                    uiState = uiState,
                    onAction = onAction,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun PersonHealthScreenTabletPreview(
    @PreviewParameter(PersonHealthPreviewParameterProvider::class) state: PersonHealthPreviewState
) {
    CareMemoTheme {
        PersonHealthScreenTablet(
            uiState = PersonHealthUiState(
                currentCategory = state.category,
                records = state.records,
                isLoading = state.isLoading,
                selectedRecordId = state.selectedRecordId
            ),
            currentPerson = state.person,
            personCategorySummary = state.summary,
            isNameMaskingEnabled = false,
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
