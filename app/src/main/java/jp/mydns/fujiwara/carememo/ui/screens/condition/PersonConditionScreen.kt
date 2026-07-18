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
 * ・base/AppDeleteConfirmDialog.kt
 * ・base/AppInfoDialog.kt
 *
 * 【備考】：
 * 画面サイズ（WindowWidthSizeClass）に基づき、Phone版とTablet版を自動的に切り替える。
 */

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.*
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

    val records by conditionViewModel.filteredRecords.collectAsStateWithLifecycle()

    val isLoading by conditionViewModel.isLoading.collectAsStateWithLifecycle()
    val currentPerson by viewModel.currentPerson.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val personCategorySummary by viewModel.personCategorySummary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var selectedId by rememberSaveable { mutableIntStateOf(-1) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var onConfirmOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }

    val noRecordsMsgFormat = stringResource(R.string.p_detail_error_no_records_for_pdf)
    val conditionCategoryName = stringResource(Category.CONDITION_AT_VISIT.displayNameRes)

    // アプリからの通知を受け付ける窓口
    LaunchedEffect(Unit) {
        conditionViewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseViewModel.UiEvent.ShowOverwriteConfirm -> {
                    onConfirmOverwrite = event.onConfirm
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
            Log.d("PersonConditionScreen", "Camera capture success. URI: $tempPhotoUri")
            onNavigateToPhotoPreview(tempPhotoUri!!, personId, selectedId)
        } else {
            Log.w("PersonConditionScreen", "Camera capture failed or cancelled. success=$success, uri=$tempPhotoUri")
            if (!success && tempPhotoUri != null) {
                // 撮影失敗時のみ通知（キャンセル時は通常何もしないが、異常系としてログと通知を行う）
                conditionViewModel.notifyPhotoError("写真の取得に失敗しました。")
            }
        }
    }


    val searchQuery by conditionViewModel.searchQuery.collectAsStateWithLifecycle()
    val conditionPhotoMap by conditionViewModel.conditionPhotoMap.collectAsStateWithLifecycle()
    val photos by conditionViewModel.currentConditionPhotos.collectAsStateWithLifecycle()
    val isProcessing by conditionViewModel.isProcessing.collectAsStateWithLifecycle()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsStateWithLifecycle()
    
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
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    Log.d("PersonConditionScreen", "Temp URI generated: $uri")
                    // カメラ起動前に、戻ってきた際のロックを一時的にスキップする設定を行う
                    viewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Log.e("PersonConditionScreen", "Failed to generate temp URI", e)
                    conditionViewModel.notifyPhotoError("カメラの起動準備に失敗しました。")
                }
            },
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noRecordsMsgFormat.format(conditionCategoryName)) }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onSaveRecord = { pId, cId, state, onSuccess -> 
                conditionViewModel.saveRecord(pId, cId, state, onSuccess) 
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
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    Log.d("PersonConditionScreen", "Temp URI generated: $uri")
                    // カメラ起動前に、戻ってきた際のロックを一時的にスキップする設定を行う
                    viewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Log.e("PersonConditionScreen", "Failed to generate temp URI", e)
                    conditionViewModel.notifyPhotoError("カメラの起動準備に失敗しました。")
                }
            },
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noRecordsMsgFormat.format(conditionCategoryName)) }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onSaveRecord = { pId, cId, state, onSuccess -> 
                conditionViewModel.saveRecord(pId, cId, state, onSuccess)
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
        AppDeleteConfirmDialog(
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
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }

    if (onConfirmOverwrite != null) {
        AppDialog(
            onDismissRequest = { onConfirmOverwrite = null },
            title = { Text(stringResource(R.string.common_confirm_overwrite_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_overwrite_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onConfirmOverwrite?.invoke()
                        onConfirmOverwrite = null
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onConfirmOverwrite = null }
                )
            }
        )
    }
}
