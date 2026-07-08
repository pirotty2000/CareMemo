package jp.mydns.fujiwara.carememo.ui.screens.condition

/**
 * Screen : PersonConditionScreen
 *
 * 【画面名】：利用者所見記録画面
 *
 * 【役割】：
 * 利用者の日々の様子や気になる変化を「所見メモ（カテゴリB）」として詳細に記録・閲覧する画面。
 * テキストによる記録に加え、写真撮影による視覚的な記録保存も担当する。
 *
 * 【主な機能】：
 * ・利用者情報の表示（ヘッダー）。
 * ・履歴一覧と詳細表示の切り替え（Phone版はシングルペイン、Tablet版は2ペイン）。
 * ・所見記録のCRUD操作（新規登録、編集、スワイプ削除）。
 * ・写真の管理（撮影連携、表示、削除）。
 * ・PDFレポート出力機能。
 *
 * 【遷移】：
 * ← MainScreen (戻るボタン)
 * → ConditionPhotoFullScreen / ConditionPhotoPreviewScreen
 *
 * 【使用するViewModel】：
 * ・PersonDetailViewModel (詳細画面共通フレームワーク)
 * ・PersonConditionViewModel (所見記録固有ロジック)
 *
 * 【使用するComponents】：
 * ・PersonConditionScreenPhone / PersonConditionScreenTablet / PersonConditionScreenContent
 * ・common/PdfExportActionHandler.kt
 * ・base/DeleteConfirmDialog.kt
 * ・base/InfoDialog.kt
 *
 * 【備考】：
 * 画面サイズ（WindowWidthSizeClass）に基づき、Phone版とTablet版を自動的に切り替える。
 */

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.DeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.InfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun PersonConditionScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    initialQuery: String = "",
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (Int, Int) -> Unit,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val records by conditionViewModel.filteredRecords.collectAsState()

    val isLoading by conditionViewModel.isLoading.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var selectedId by rememberSaveable { mutableIntStateOf(-1) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // アプリからの通知を受け付ける窓口
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowInfoDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is BaseViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                else -> {}
            }
        }
    }

    // 選択されたIDが変更されたら、ViewModel側に通知して写真をロードさせる
    LaunchedEffect(selectedId) {
        conditionViewModel.setSelectedConditionId(if (selectedId != -1) selectedId else null)
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoUri != null) {
            onNavigateToPhotoPreview(tempPhotoUri!!, personId, selectedId)
        }
    }


    val searchQuery by conditionViewModel.searchQuery.collectAsState()
    val conditionPhotoMap by conditionViewModel.conditionPhotoMap.collectAsState()
    val photos by conditionViewModel.currentConditionPhotos.collectAsState()
    val isProcessing by conditionViewModel.isProcessing.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()
    
    var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }

    val isAnyDialogOpen = recordToDelete != null || showPdfSettingsDialog || dialogMessage != null

    // 最後にロードした利用者IDを保持して、不要なリセットを防ぐ
    var lastLoadedPersonId by rememberSaveable { mutableIntStateOf(-1) }

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
        viewModel.setCategory(Category.CONDITION_AT_VISIT)
        conditionViewModel.loadPerson(personId, initialQuery)
        
        // 実際に別の利用者の画面へ遷移した時だけ、選択状態をリセットする。
        // これにより、写真撮影画面から同じ利用者の画面に戻った際は、選択状態（selectedId）が維持される。
        if (lastLoadedPersonId != personId) {
            selectedId = -1
            lastLoadedPersonId = personId
        }
    }

    if (isExpanded) {
        // ---------- タブレット ----------
        PersonConditionScreenTablet(
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            conditionPhotoMap = conditionPhotoMap,
            photos = photos,
            isProcessing = isProcessing,
            isAnyDialogOpen = isAnyDialogOpen,
            defaultRecorderName = defaultRecorderName,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onAddPhotoClick = {
                val uri = ImageUtils.getTempPhotoUri(context)
                tempPhotoUri = uri
                // カメラ起動前に、戻ってきた際のロックを一時的にスキップする設定を行う
                viewModel.setLockBypassEnabled(true)
                cameraLauncher.launch(uri)
            },
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onSaveRecord = { record, onSuccess -> 
                conditionViewModel.saveRecord(record, onSuccess) 
            },
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            onMicClick = { viewModel.setLockBypassEnabled(true) },
            snackbarHostState = snackbarHostState
        )
    } else {
        // ---------- スマホ ----------
        PersonConditionScreenPhone(
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            conditionPhotoMap = conditionPhotoMap,
            photos = photos,
            isProcessing = isProcessing,
            isAnyDialogOpen = isAnyDialogOpen,
            defaultRecorderName = defaultRecorderName,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onAddPhotoClick = {
                val uri = ImageUtils.getTempPhotoUri(context)
                tempPhotoUri = uri
                // カメラ起動前に、戻ってきた際のロックを一時的にスキップする設定を行う
                viewModel.setLockBypassEnabled(true)
                cameraLauncher.launch(uri)
            },
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onSaveRecord = { record, onSuccess -> 
                conditionViewModel.saveRecord(record, onSuccess) 
            },
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            onMicClick = { viewModel.setLockBypassEnabled(true) },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        val allPhotos = remember { mutableStateOf<List<ConditionPhoto>>(emptyList()) }
        LaunchedEffect(Unit) {
            allPhotos.value = conditionViewModel.getAllPhotosForPerson(personId)
        }
        
        PdfExportActionHandler(
            showDialog = showPdfSettingsDialog,
            onDismiss = { showPdfSettingsDialog = false },
            category = Category.CONDITION_AT_VISIT,
            person = currentPerson,
            records = records,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            onRequireAuthentication = onRequireAuthentication,
            photos = allPhotos.value
        )
    }

    // 削除確認ダイアログ
    if (recordToDelete != null) {
        DeleteConfirmDialog(
            onDismiss = { recordToDelete = null },
            onDelete = {
                recordToDelete?.let { record ->
                    if (selectedId == record.id) selectedId = -1
                    if (record is ConditionAtVisit) {
                        conditionViewModel.deleteRecord(record)
                    }
                }
            }
        )
    }

    if (dialogMessage != null) {
        InfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }
}
