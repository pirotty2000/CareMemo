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
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenPhone(
    currentCategory: Category,
    records: ImmutableList<HistoryRecord>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: String?,
    onSelectedRecordIdChange: (String?) -> Unit,
    onBack: () -> Unit,
    onExpandGraph: (Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (Category, String, Instant, Map<String, Any?>) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
                            onClick = { if (selectedRecordId != null) onSelectedRecordIdChange(null) else onBack() },
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
                        if (selectedRecordId == null) {
                            IconButton(
                                onClick = onShowPdfSettings,
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
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory,
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        },
        // 右下のFAB
        floatingActionButton = {
            if (selectedRecordId == null) {
                FloatingActionButton(
                    onClick = {
                        onSelectedRecordIdChange(AppSpecifications.Id.NEW_RECORD_ID)
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
                onDelete = { recordToDelete?.let { onDeleteRecord(it) } }
            )
        }

        // メイン・コンテンツ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (selectedRecordId == null) 16.dp else 0.dp)
        ) {
            if (records.isEmpty() && selectedRecordId == null && !isLoading) {
                EmptyState(
                    message = stringResource(R.string.p_detail_empty_records),
                    description = stringResource(R.string.p_detail_empty_records_desc),
                    icon = Icons.Outlined.Description
                )
            } else {
                //-- ui/screens/health/PersonHealtScreeenContent.kt
                PersonHealthScreenContent(
                    isExpanded = false,
                    records = records,
                    isLoading = isLoading,
                    currentCategory = currentCategory,
                    preferredShowHistory = preferredShowHistory,
                    onPreferredShowHistoryChange = onPreferredShowHistoryChange,
                    selectedRecordId = selectedRecordId,
                    onSelectedRecordIdChange = onSelectedRecordIdChange,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = { record -> recordToDelete = record },
                    onExpandGraph = onExpandGraph,
                    onSaveRecord = onSaveRecord,
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
            currentCategory = state.category,
            records = state.records,
            isLoading = state.isLoading,
            currentPerson = state.person,
            personCategorySummary = state.summary,
            isNameMaskingEnabled = false,
            preferredShowHistory = state.preferredShowHistory,
            onPreferredShowHistoryChange = {},
            selectedRecordId = state.selectedRecordId,
            onSelectedRecordIdChange = {},
            onBack = {},
            onExpandGraph = {},
            onNavigateToCategory = {},
            onShowPdfSettings = {},
            onDeleteRecord = {},
            onSaveRecord = { _, _, _, _ -> },
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
