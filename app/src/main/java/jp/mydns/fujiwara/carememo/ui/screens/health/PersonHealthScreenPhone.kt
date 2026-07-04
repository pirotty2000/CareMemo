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
 * ・base/DeleteConfirmDialog.kt
 * ・base/EmptyState.kt
 * ・base/AppTopAppBarColors.kt
 *
 * 【備考】：
 * このコンポーネント自体は状態を持たず、UIの構造定義と親画面へのイベント伝達に特化している。
 *
 * ---
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.DeleteConfirmDialog
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
    personId: Int,
    currentCategory: Category,
    records: List<Any>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: Int,
    onSelectedRecordIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
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
                        IconButton(onClick = { if (selectedRecordId != -1) onSelectedRecordIdChange(-1) else onBack() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = appTopAppBarColors(),
                    // PDF出力
                    actions = {
                        if (selectedRecordId == -1) {
                            IconButton(onClick = onShowPdfSettings) {
                                Icon(
                                    Icons.Rounded.PictureAsPdf,
                                    contentDescription = stringResource(R.string.pdf_export)
                                )
                            }
                        }
                    }
                )
                if (selectedRecordId == -1) {
                    CategorySelectorBar(
                        currentCategory = currentCategory,
                        personCategorySummary = personCategorySummary,
                        onCategoryClick = onNavigateToCategory
                    )
                }
            }
        },
        // 右下の「＋」
        floatingActionButton = {
            if (selectedRecordId == -1) {
                FloatingActionButton(onClick = {
                    onSelectedRecordIdChange(0)
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_new))
                }
            }
        }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            DeleteConfirmDialog(
                onDismiss = { recordToDelete = null },
                onDelete = { recordToDelete?.let { onDeleteRecord(it) } }
            )
        }

        // メイン・コンテンツ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (selectedRecordId == -1) 16.dp else 0.dp)
        ) {
            if (records.isEmpty() && selectedRecordId == -1 && !isLoading) {
                EmptyState(
                    message = stringResource(R.string.empty_records),
                    description = stringResource(R.string.empty_records_description),
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
            personId = 1,
            currentCategory = Category.BP_AND_PULSE,
            records = listOf(
                BpAndPulse(id = 1, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
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
            selectedRecordId = -1,
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
