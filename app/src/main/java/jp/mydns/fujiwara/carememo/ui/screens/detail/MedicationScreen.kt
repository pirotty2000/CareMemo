package jp.mydns.fujiwara.carememo.ui.screens.detail

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.MedicationViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val recordsByDate by viewModel.recordsByDate.collectAsState()
    val allRecords by viewModel.allRecords.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf<LocalDate?>(null) }
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    if (isExpanded) {
        MedicationScreenTablet(
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = isLoading,
            selectedMonth = selectedMonth,
            recordsByDate = recordsByDate,
            personCategorySummary = personCategorySummary,
            onPreviousMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = {
                if (allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("服薬記録がないため出力できません")
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDayClick = { date -> showDialog = date },
            snackbarHostState = snackbarHostState
        )
    } else {
        MedicationScreenPhone(
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            isLoading = isLoading,
            selectedMonth = selectedMonth,
            recordsByDate = recordsByDate,
            personCategorySummary = personCategorySummary,
            isHistoryMode = isHistoryMode,
            onHistoryModeChange = { isHistoryMode = it },
            onPreviousMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onShowPdfSettings = {
                if (allRecords.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("服薬記録がないため出力できません")
                    }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDayClick = { date -> showDialog = date },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    PdfExportActionHandler(
        showDialog = showPdfSettingsDialog,
        onDismiss = { showPdfSettingsDialog = false },
        category = Category.MEDICATION,
        person = currentPerson,
        records = allRecords,
        isNameMaskingEnabled = isNameMaskingEnabled,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        personId = personId
    )

    if (showDialog != null) {
        MedicationInputDialog(
            date = showDialog!!,
            personId = personId,
            records = recordsByDate[showDialog.toString()] ?: emptyList(),
            onDismiss = { showDialog = null },
            onSave = { record ->
                viewModel.saveMedicationRecord(record)
            },
            onDelete = { record ->
                viewModel.deleteMedicationRecord(record)
            }
        )
    }
}
