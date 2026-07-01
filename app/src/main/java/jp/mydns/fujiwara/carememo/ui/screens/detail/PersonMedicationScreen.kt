package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonMedicationScreen
 *
 * 【画面名】
 * 利用者服薬記録画面
 *
 * 【役割】
 * 特定の利用者の服薬履歴を管理し、新規の服薬記録の登録、過去の記録の確認、
 * および服薬状況のPDFレポート出力を行う画面。
 *
 * 【主な機能】
 * ・履歴表示：日ごとの服薬状況をカレンダー形式またはリスト形式で表示。
 * ・服薬登録：薬品名、用量、時間帯（朝・昼・夕・寝る前など）の記録。
 * ・PDF出力：指定した期間の服薬記録をPDFとして生成し、共有・印刷可能にする。
 * ・画面最適化：デバイスの画面幅（Phone/Tablet）に応じて最適なレイアウトを自動選択。
 * ・カテゴリ連携：上部メニューから他の健康記録カテゴリへ直接遷移。
 *
 * 【遷移】
 * ← MainScreen（戻るボタン）
 * → PersonMedicationScreenPhone / PersonMedicationScreenTablet（画面幅に応じて内部で分岐）
 *
 * 【使用するViewModel】
 * PersonMedicationViewModel
 *
 * 【備考】
 * 履歴データは月単位でロードされ、カレンダーによる日別の絞り込みが可能。
 */

import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PersonMedicationScreen(
    viewModel: PersonMedicationViewModel,
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
        PersonMedicationScreenTablet(
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
        PersonMedicationScreenPhone(
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
        viewModel = viewModel
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
