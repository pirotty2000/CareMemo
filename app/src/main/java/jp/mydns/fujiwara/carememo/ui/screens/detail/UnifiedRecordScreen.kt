package jp.mydns.fujiwara.carememo.ui.screens.detail

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.PdfSettingsDialog
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun UnifiedRecordScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    healthViewModel: HealthRecordViewModel,
    initialCategoryType: Category,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    var currentCategory by rememberSaveable { mutableStateOf(initialCategoryType) }

    // ユーザーの表示モード設定（初期値は履歴: true）
    var preferredShowHistory by rememberSaveable { mutableStateOf(true) }

    // タブレット用／編集用：現在選択されている記録のID (-1: 未選択, 0: 新規作成)
    var selectedRecordId by rememberSaveable { mutableIntStateOf(-1) }

    val records by healthViewModel.records.collectAsState()
    val conditionPhotoMap by conditionViewModel.getConditionPhotoMap(healthViewModel.records).collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noRecordsMsgFormat = stringResource(R.string.error_no_records_for_pdf)
    val pdfExportFailedMsg = stringResource(R.string.error_pdf_export_failed)

    var showPdfSettingsDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(
                    event.message
                )
                is jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(currentCategory, personId) {
        viewModel.loadPerson(personId)
        conditionViewModel.loadPerson(personId)
        healthViewModel.loadPerson(personId)
        viewModel.setCategory(currentCategory)
        healthViewModel.setCategory(currentCategory)
        // カテゴリが切り替わったら選択をリセット
        selectedRecordId = -1
    }

    val currentCategoryName = stringResource(currentCategory.displayNameRes)

    if (isExpanded) {
        UnifiedRecordScreenTablet(
            healthViewModel = healthViewModel,
            personId = personId,
            currentCategory = currentCategory,
            records = records,
            conditionPhotoMap = conditionPhotoMap,
            currentPerson = currentPerson,
            personCategorySummary = personCategorySummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            selectedConditionId = selectedRecordId,
            onSelectedConditionIdChange = { selectedRecordId = it },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = { category ->
                if (category.hasGraph) {
                    currentCategory = category
                } else {
                    onNavigateToCategory(category)
                }
            },
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(currentCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            snackbarHostState = snackbarHostState
        )
    } else {
        UnifiedRecordScreenPhone(
            healthViewModel = healthViewModel,
            personId = personId,
            currentCategory = currentCategory,
            records = records,
            conditionPhotoMap = conditionPhotoMap,
            currentPerson = currentPerson,
            personCategorySummary = personCategorySummary,
            isNameMaskingEnabled = isNameMaskingEnabled,
            preferredShowHistory = preferredShowHistory,
            onPreferredShowHistoryChange = { preferredShowHistory = it },
            selectedRecordId = selectedRecordId,
            onSelectedRecordIdChange = { selectedRecordId = it },
            onBack = onBack,
            onNavigateToGraphExpansion = onNavigateToGraphExpansion,
            onNavigateToCategory = { category ->
                if (category.hasGraph) {
                    currentCategory = category
                } else {
                    onNavigateToCategory(category)
                }
            },
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar(noRecordsMsgFormat.format(currentCategoryName))
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            snackbarHostState = snackbarHostState
        )
    }

    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                dialogMessage = null
                dialogTitle = null
            },
            title = { dialogTitle?.let { Text(it) } },
            text = { Text(dialogMessage!!) },
            confirmButton = {
                TextButton(onClick = {
                    dialogMessage = null
                    dialogTitle = null
                }) { Text("閉じる") }
            }
        )
    }

    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = currentCategory,
            onDismiss = { showPdfSettingsDialog = false }
        ) { r, o, start, end, photos, password ->
            showPdfSettingsDialog = false
            viewModel.setLockBypassEnabled(true)
            scope.launch {
                val allPhotos =
                    if (currentCategory.hasOption && photos) conditionViewModel.getAllPhotosForPerson(
                        personId
                    ) else emptyList()
                currentPerson?.let { person ->
                    val success = PdfExporter.exportAndShare(
                        context = context,
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        category = currentCategory,
                        records = records,
                        allPhotos = allPhotos,
                        range = r,
                        order = o,
                        customStartDate = start,
                        customEndDate = end,
                        password = password
                    )
                    if (!success) {
                        snackbarHostState.showSnackbar(pdfExportFailedMsg)
                    }
                }
            }
        }
    }
}
