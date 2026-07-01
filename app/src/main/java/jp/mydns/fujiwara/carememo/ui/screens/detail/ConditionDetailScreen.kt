package jp.mydns.fujiwara.carememo.ui.screens.detail

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch

/**
 * 所見メモ画面 (Bカテゴリ トップレベル画面)
 * WindowSizeに基づいて Phone/Tablet のレイアウトに分岐する。
 */
@Composable
fun ConditionDetailScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()

    // データの監視
    val records by conditionViewModel.filteredRecords.collectAsState()
    val isLoading by conditionViewModel.isLoading.collectAsState()
    val searchQuery by conditionViewModel.searchQuery.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val conditionPhotoMap by conditionViewModel.conditionPhotoMap.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 選択中のID管理
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    // 初期ロード
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
        viewModel.setCategory(Category.CONDITION_AT_VISIT)
        conditionViewModel.loadPerson(personId)
    }

    // IDが選択されたら写真データをロード
    LaunchedEffect(selectedId) {
        conditionViewModel.setSelectedConditionId(if (selectedId > 0) selectedId else null)
    }

    if (isExpanded) {
        ConditionDetailScreenTablet(
            viewModel = viewModel,
            conditionViewModel = conditionViewModel,
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            conditionPhotoMap = conditionPhotoMap,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onNavigateToPhotoPreview = onNavigateToPhotoPreview,
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            snackbarHostState = snackbarHostState
        )
    } else {
        ConditionDetailScreenPhone(
            viewModel = viewModel,
            conditionViewModel = conditionViewModel,
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            conditionPhotoMap = conditionPhotoMap,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onNavigateToPhotoPreview = onNavigateToPhotoPreview,
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    PdfExportActionHandler(
        showDialog = showPdfSettingsDialog,
        onDismiss = { showPdfSettingsDialog = false },
        category = Category.CONDITION_AT_VISIT,
        person = currentPerson,
        records = records,
        isNameMaskingEnabled = isNameMaskingEnabled,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        conditionViewModel = conditionViewModel,
        personId = personId
    )

    // 削除確認ダイアログ
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_data_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let {
                            if (selectedId == it.id) selectedId = -1
                            conditionViewModel.deleteRecord(it as jp.mydns.fujiwara.carememo.data.ConditionAtVisit)
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
}
