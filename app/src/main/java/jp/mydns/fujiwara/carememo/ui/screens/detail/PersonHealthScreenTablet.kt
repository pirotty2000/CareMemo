package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonHealthScreenTablet
 *
 * 【画面名】
 * 利用者健康記録画面（タブレット版）
 *
 * 【役割】
 * 横長画面（Expandedクラス）に最適化された健康記録UIを提供し、バイタルや記録履歴を効率的に管理する。
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenTablet(
    viewModel: PersonDetailViewModel,
    healthViewModel: PersonHealthViewModel,
    personId: Int,
    currentCategory: Category,
    records: List<Any>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: jp.mydns.fujiwara.carememo.data.PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    selectedRecordId: Int,
    onSelectedRecordIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
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
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { onSelectedRecordIdChange(0) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                        }
                        IconButton(onClick = onShowPdfSettings) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = stringResource(R.string.pdf_export)
                            )
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory
                )
            }
        }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text(stringResource(R.string.delete_data_title)) },
                text = { Text(stringResource(R.string.delete_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            recordToDelete?.let {
                                if (selectedRecordId == it.id) onSelectedRecordIdChange(-1)
                                healthViewModel.deleteRecord(it)
                            }
                            recordToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text(stringResource(R.string.cancel))
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
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (records.isEmpty() && selectedRecordId == -1) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(R.string.empty_records),
                        description = stringResource(R.string.empty_records_description),
                        icon = Icons.Outlined.Description
                    )
                }
            } else {
                PersonHealthScreenContent(
                    isExpanded = true,
                    personId = personId,
                    records = records,
                    currentCategory = currentCategory,
                    preferredShowHistory = true,
                    onPreferredShowHistoryChange = {},
                    selectedRecordId = selectedRecordId,
                    onSelectedRecordIdChange = onSelectedRecordIdChange,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = { record -> recordToDelete = record },
                    onExpandGraph = { index ->
                        onNavigateToGraphExpansion(personId, currentCategory, index)
                    },
                    viewModel = viewModel,
                    healthViewModel = healthViewModel,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}
