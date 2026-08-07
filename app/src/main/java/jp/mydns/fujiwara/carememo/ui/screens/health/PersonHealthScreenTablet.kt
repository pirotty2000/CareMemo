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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenTablet(
    currentCategory: Category,
    records: ImmutableList<HistoryRecord>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    selectedRecordId: String?,
    onSelectedRecordIdChange: (String?) -> Unit,
    onBack: () -> Unit,
    onExpandGraph: (Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (Category, String, Instant, Map<String, Any?>) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = stringResource(R.string.app_name)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
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
                        IconButton(onClick = { onSelectedRecordIdChange(AppSpecifications.Id.NEW_RECORD_ID) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                        }
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
                )
                CategorySelectorBar(
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory,
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
                        if (selectedRecordId == it.id) onSelectedRecordIdChange(null)
                        onDeleteRecord(it)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (records.isEmpty() && selectedRecordId == null && !isLoading) {
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
                    records = records,
                    isLoading = isLoading,
                    currentCategory = currentCategory,
                    preferredShowHistory = true,
                    onPreferredShowHistoryChange = {},
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

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun PersonHealthScreenTabletPreview() {
    CareMemoTheme {
        PersonHealthScreenTablet(
            currentCategory = Category.BP_AND_PULSE,
            records = persistentListOf(
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
            selectedRecordId = null,
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
