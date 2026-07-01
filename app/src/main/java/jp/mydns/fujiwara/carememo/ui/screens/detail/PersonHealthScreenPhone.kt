package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonHealthScreenPhone
 *
 * 【画面名】
 * 利用者健康記録画面（スマートフォン版）
 *
 * 【役割】
 * 縦長画面（Compact/Mediumクラス）に最適化された、バイタルや血糖値などの健康記録UIを提供する。
 *
 * 【主な機能】
 * ・モバイル最適化レイアウト：シングルペインでのリスト・入力画面構成。
 * ・タブナビゲーション：限られたスペースでのカテゴリ（バイタル、血糖値、体重等）切り替え。
 * ・アクション統合：トップバーにPDF出力やグラフ表示などの主要アクションを配置。
 *
 * 【遷移】
 * ← PersonHealthScreen（呼び出し元）
 *
 * 【備考】
 * モバイルデバイスでの片手操作や視認性を考慮し、スクロールとタブ切り替えを基本とした構造を採用している。
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
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonHealthScreenPhone(
    healthViewModel: PersonHealthViewModel,
    personId: Int,
    currentCategory: Category,
    records: List<Any>,
    isLoading: Boolean,
    currentPerson: Person?,
    personCategorySummary: jp.mydns.fujiwara.carememo.data.PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
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
                        IconButton(onClick = { if (selectedRecordId != -1) onSelectedRecordIdChange(-1) else onBack() }) {
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
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text(stringResource(R.string.delete_data_title)) },
                text = { Text(stringResource(R.string.delete_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            recordToDelete?.let { healthViewModel.deleteRecord(it) }
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
                .padding(horizontal = if (selectedRecordId == -1) 16.dp else 0.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
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
                    healthViewModel = healthViewModel,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}
