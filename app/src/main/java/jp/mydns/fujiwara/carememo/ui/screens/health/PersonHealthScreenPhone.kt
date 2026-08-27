package jp.mydns.fujiwara.carememo.ui.screens.health

/**
 * Screen : PersonHealthScreenPhone
 *
 * 【画面名】：
 * 利用者健康記録画面（スマートフォン版レイアウト）
 *
 * 【役割】：
 * 縦長画面（Compact/Mediumクラス）に最適化された健康記録インターフェースを提供する。
 * 片手操作や視認性を重視し、シングルペインでのリスト・詳細切り替えとタブナビゲーションを行う。
 *
 * 【主な機能】：
 * ・モバイル最適化レイアウト（履歴リストと詳細入力、グラフを切り替えて表示）
 * ・タブナビゲーション（バイタル、血糖値、身体計測などのカテゴリを素早く切り替え）
 * ・アクション統合（トップバーからのPDF出力や、FABによる新規記録の追加）
 * ・入力支援（詳細ペインでの数値入力および日付選択）
 *
 * 【遷移】：
 * ← PersonHealthScreen (親コンテナ)
 * → PersonHealthScreenContent (共通コンテンツの呼び出し)
 *
 * 【使用するViewModel】：
 * なし（Stateless化済み。親の PersonHealthScreen から状態とラムダを受け取る）
 *
 * 【使用するComponents】：
 * ・screens/detail/health/PersonHealthScreenContent.kt
 * ・detail/common/CategorySelectorBar.kt
 * ・detail/common/PersonHeaderTitle.kt
 * ・base/AppDeleteConfirmDialog.kt
 * ・base/EmptyState.kt
 * ・base/AppTopAppBarColors.kt
 *
 * 【備考】：
 * このコンポーネント自体は状態を持たず、UIの構造定義と親画面へのイベント伝達に特化している。
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
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
 * Screen：PersonHealthScreenPhone
 *
 * 【役割】
 * スマートフォン等の狭い画面（WindowWidthSizeClass.Compact/Medium）向けに最適化された健康記録画面です。
 * 履歴リストと統計グラフ、詳細入力を柔軟に切り替えて表示します。
 *
 * 【主な機能】
 * ・シングルペイン制御：履歴リストと詳細入力を排他的に表示し、限られた画面領域を活用。
 * ・モード切り替え：履歴 ↔ グラフ の表示モード選択（SegmentedButton）。
 * ・ナビゲーション統合：TopAppBar へのタイトル、戻るボタン、および PDF 出力ボタンの配置。
 * ・フローティングアクションボタン（FAB）：新規レコード作成のショートカット提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenPhone(
    uiState: PersonHealthUiState,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    onAction: (PersonHealthUiAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("HealthScreen_PhoneContent"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 画面最上部のバー
        topBar = {
            Column {
                TopAppBar(
                    // タイトル
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = stringResource(R.string.health_title)
                        )
                    },
                    // 戻る（←）アイコン
                    navigationIcon = {
                        IconButton(
                            onClick = { 
                                if (uiState.selectedRecordId != null) onAction(PersonHealthUiAction.CancelEdit) 
                                else onAction(PersonHealthUiAction.Back) 
                            },
                            modifier = Modifier.testTag("HealthScreen_BackButton")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    colors = appTopAppBarColors(),
                    // PDF出力
                    actions = {
                        if (uiState.selectedRecordId == null) {
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
                    }
                )
                // カテゴリ選択バー
                CategorySelectorBar(
                    currentCategory = uiState.currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { onAction(PersonHealthUiAction.NavigateToCategory(it)) },
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        },
        // 右下のFAB
        floatingActionButton = {
            if (uiState.selectedRecordId == null) {
                FloatingActionButton(
                    onClick = {
                        onAction(PersonHealthUiAction.SelectedRecordIdChanged(AppSpecifications.Id.NEW_RECORD_ID))
                    },
                    modifier = Modifier.testTag("HealthScreen_AddButton")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.main_btn_add_new))
                }
            }
        }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AppDeleteConfirmDialog(
                onDismiss = { recordToDelete = null },
                onDelete = { 
                    recordToDelete?.let { onAction(PersonHealthUiAction.DeleteRecord(it)) } 
                    recordToDelete = null
                }
            )
        }

        // メイン・コンテンツ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (uiState.selectedRecordId == null) 16.dp else 0.dp)
        ) {
            if ((uiState.records.isEmpty() && uiState.selectedRecordId == null && !uiState.isLoading)) {
                EmptyState(
                    message = stringResource(R.string.p_detail_empty_records),
                    description = stringResource(R.string.p_detail_empty_records_desc),
                    icon = Icons.Outlined.Description
                )
            } else {
                //-- ui/screens/health/PersonHealtScreeenContent.kt
                PersonHealthScreenContent(
                    isExpanded = false,
                    uiState = uiState,
                    onAction = onAction,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun PersonHealthScreenPhonePreview(
    @PreviewParameter(PersonHealthPreviewParameterProvider::class) state: PersonHealthPreviewState
) {
    CareMemoTheme {
        PersonHealthScreenPhone(
            uiState = PersonHealthUiState(
                currentCategory = state.category,
                records = state.records,
                isLoading = state.isLoading,
                preferredShowHistory = state.preferredShowHistory,
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
