package jp.mydns.fujiwara.carememo.ui.screens.detail

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.utils.PdfExporter
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
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val recordsByDate by viewModel.recordsByDate.collectAsState()
    val allRecords by viewModel.allRecords.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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

    if (showPdfSettingsDialog) {
        PdfSettingsDialog(
            category = Category.MEDICATION,
            onDismiss = { showPdfSettingsDialog = false }
        ) { range, order, start, end, _, password ->
            showPdfSettingsDialog = false
            viewModel.setLockBypassEnabled(true)
            scope.launch {
                currentPerson?.let { person ->
                    val success = PdfExporter.exportAndShare(
                        context = context,
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        category = Category.MEDICATION,
                        records = allRecords,
                        range = range,
                        order = order,
                        customStartDate = start,
                        customEndDate = end,
                        password = password
                    )
                    if (!success) {
                        snackbarHostState.showSnackbar("PDFの作成に失敗したか、対象データがありません")
                    }
                }
            }
        }
    }

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
