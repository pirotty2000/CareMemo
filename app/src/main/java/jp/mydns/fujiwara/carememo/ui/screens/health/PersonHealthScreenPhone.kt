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
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle

import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenPhone(
    personId: String,
    currentCategory: Category,
    records: List<Any>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: String,
    onSelectedRecordIdChange: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (String, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (Any) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
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
                            defaultTitle = "健康記録"
                        )
                    },
                    // 戻る（←）アイコン
                    navigationIcon = {
                        IconButton(
                            onClick = { if (selectedRecordId.isNotEmpty()) onSelectedRecordIdChange("") else onBack() },
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
                        if (selectedRecordId.isEmpty()) {
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
                CategorySelectorBar(
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory,
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        },
        // 右下の「＋」
        floatingActionButton = {
            if (selectedRecordId.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        onSelectedRecordIdChange(jp.mydns.fujiwara.carememo.logic.feature.PersonHealthLogic.NEW_RECORD_ID)
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
                .padding(horizontal = if (selectedRecordId.isEmpty()) 16.dp else 0.dp)
        ) {
            if (records.isEmpty() && selectedRecordId.isEmpty() && !isLoading) {
                EmptyState(
                    message = stringResource(R.string.p_detail_empty_records),
                    description = stringResource(R.string.p_detail_empty_records_desc),
                    icon = Icons.Outlined.Description
                )
            } else {
                PersonHealthScreenContent(
                    isExpanded = false,
                    personId = personId,
                    records = records,
                    isLoading = isLoading,
                    currentCategory = currentCategory,
                    preferredShowHistory = preferredShowHistory,
                    onPreferredShowHistoryChange = onPreferredShowHistoryChange,
                    selectedRecordId = selectedRecordId,
                    onSelectedRecordIdChange = onSelectedRecordIdChange,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = { record -> recordToDelete = record },
                    onExpandGraph = { index ->
                        onNavigateToGraphExpansion(personId, currentCategory, index)
                    },
                    onSaveRecord = onSaveRecord,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun PersonHealthScreenPhonePreview() {
    CareMemoTheme {
        PersonHealthScreenPhone(
            personId = "person-1",
            currentCategory = Category.BP_AND_PULSE,
            records = listOf(
                BpAndPulse(id = "1", personId = "person-1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
            ),
            isLoading = false,
            currentPerson = Person(
                lastName = "山田", 
                firstName = "太郎",
                lastNameFurigana = "ヤマダ",
                firstNameFurigana = "タロウ",
                birthday = Instant.now()
            ),
            personCategorySummary = null,
            isNameMaskingEnabled = false,
            preferredShowHistory = true,
            onPreferredShowHistoryChange = {},
            selectedRecordId = "",
            onSelectedRecordIdChange = {},
            onBack = {},
            onNavigateToGraphExpansion = { _, _, _ -> },
            onNavigateToCategory = {},
            onShowPdfSettings = {},
            onDeleteRecord = {},
            onSaveRecord = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
