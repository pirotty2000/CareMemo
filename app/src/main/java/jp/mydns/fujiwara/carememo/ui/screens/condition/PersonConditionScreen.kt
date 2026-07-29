package jp.mydns.fujiwara.carememo.ui.screens.condition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 利用者所見記録画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    conditionViewModel: PersonConditionViewModel,
    initialQuery: String = "",
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPhotoPreview: (Uri, String, String) -> Unit,
    onNavigateToFullScreen: (String, String) -> Unit,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val conditionState by conditionViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val defaultRecorderName by conditionViewModel.defaultRecorderName.collectAsStateWithLifecycle()

    // 1.2.2項に基づき、ID変更時のみロードをトリガー
    LaunchedEffect(detailState.personId) {
        detailState.personId?.let { 
            conditionViewModel.loadPerson(it)
            conditionViewModel.updateSearchQuery(initialQuery)
        }
    }

    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var onConfirmOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }

    val noRecordsMsgFormat = stringResource(R.string.p_detail_error_no_records_for_pdf)
    val conditionCategoryName = stringResource(Category.CONDITION_AT_VISIT.displayNameRes)

    // イベント監視
    LaunchedEffect(Unit) {
        launch {
            conditionViewModel.uiEventFlow.collect { event ->
                when (event) {
                    is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                        snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                    }
                    is BaseUiStateViewModel.UiEvent.ShowErrorDialog -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                    }
                    is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes -> {
                        dialogTitle = context.getString(event.titleResId)
                        dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                    }
                    is BaseUiStateViewModel.UiEvent.ShowOverwriteConfirm -> {
                        onConfirmOverwrite = event.onConfirm
                    }
                    else -> {}
                }
            }
        }
        launch {
            conditionViewModel.viewEvent.collect { event ->
                when (event) {
                    is PersonConditionViewEvent.NavigateToPhotoPreview -> {
                        onNavigateToPhotoPreview(event.uri, event.personId, event.conditionId)
                    }
                    is PersonConditionViewEvent.NavigateToPhotoFullScreen -> {
                        onNavigateToFullScreen(event.photoId, event.conditionId)
                    }
                }
            }
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoUri != null) {
            val cid = conditionState.selectedConditionId ?: ""
            conditionViewModel.onPhotoCaptured(tempPhotoUri!!, cid)
        } else {
            if (!success && tempPhotoUri != null) {
                conditionViewModel.notifyPhotoError("写真の取得に失敗しました。")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val cid = conditionState.selectedConditionId ?: ""
            conditionViewModel.onPhotoCaptured(uri, cid)
        }
    }

    var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    val isAnyDialogOpen = recordToDelete != null || showPdfSettingsDialog || dialogMessage != null

    if (isExpanded) {
        // Tablet
        PersonConditionScreenTablet(
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = detailState.personSummary,
            records = conditionState.filteredRecords,
            isLoading = conditionState.isLoading,
            searchQuery = conditionState.searchQuery,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            conditionPhotoMap = conditionState.conditionPhotoMap,
            photos = conditionState.currentConditionPhotos,
            isProcessing = conditionState.isProcessing,
            isAnyDialogOpen = isAnyDialogOpen,
            defaultRecorderName = defaultRecorderName,
            selectedId = conditionState.selectedConditionId ?: "",
            onSelectedIdChange = { id: String -> conditionViewModel.setSelectedConditionId(id.ifEmpty { null }) },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onAddPhotoClick = {
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    conditionViewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    conditionViewModel.notifyPhotoError("カメラの起動準備に失敗しました。")
                }
            },
            onPickPhotoClick = {
                conditionViewModel.setLockBypassEnabled(true)
                galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onNavigateToFullScreen = { photoId, conditionId -> 
                onNavigateToFullScreen(photoId, conditionId) 
            },
            onShowPdfSettings = {
                if (conditionState.records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noRecordsMsgFormat.format(conditionCategoryName)) }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onSaveRecord = { cId, s, onSuccess -> 
                conditionViewModel.saveRecord(cId, s.title, s.condition, s.author, s.recordTime ?: Instant.now(), onSuccess) 
            },
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            onReattachPhoto = { info ->
                val cid = conditionState.selectedConditionId ?: ""
                conditionViewModel.reattachOrphanedPhoto(cid, info)
            },
            orphanedPhotos = conditionState.availableOrphanedPhotos,
            onMicClick = { conditionViewModel.setLockBypassEnabled(true) },
            snackbarHostState = snackbarHostState
        )
    } else {
        // Phone
        PersonConditionScreenPhone(
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = detailState.personSummary,
            records = conditionState.filteredRecords,
            isLoading = conditionState.isLoading,
            searchQuery = conditionState.searchQuery,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            conditionPhotoMap = conditionState.conditionPhotoMap,
            photos = conditionState.currentConditionPhotos,
            isProcessing = conditionState.isProcessing,
            isAnyDialogOpen = isAnyDialogOpen,
            defaultRecorderName = defaultRecorderName,
            selectedId = conditionState.selectedConditionId ?: "",
            onSelectedIdChange = { id: String -> conditionViewModel.setSelectedConditionId(id.ifEmpty { null }) },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            // カメラ
            onAddPhotoClick = {
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    conditionViewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    conditionViewModel.notifyPhotoError("カメラの起動準備に失敗しました。")
                }
            },
            // ギャラリー
            onPickPhotoClick = {
                conditionViewModel.setLockBypassEnabled(true)
                galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            // 写真のフルスクリーン表示
            onNavigateToFullScreen = { photoId, conditionId -> 
                onNavigateToFullScreen(photoId, conditionId) 
            },
            // PDF出力
            onShowPdfSettings = {
                if (conditionState.records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noRecordsMsgFormat.format(conditionCategoryName)) }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            // 削除
            onDeleteRecord = { recordToDelete = it },
            // 保存
            onSaveRecord = { cId, s, onSuccess ->
                conditionViewModel.saveRecord(cId, s.title, s.condition, s.author, s.recordTime ?: Instant.now(), onSuccess) 
            },
            // サムネイルのごみ箱アイコン
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            // 迷子写真の再アタッチ処理
            onReattachPhoto = { info ->
                val cid = conditionState.selectedConditionId ?: ""
                conditionViewModel.reattachOrphanedPhoto(cid, info)
            },
            // 迷子写真のコレクション
            orphanedPhotos = conditionState.availableOrphanedPhotos,
            // マイク
            onMicClick = { conditionViewModel.setLockBypassEnabled(true) },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        val allPhotos = remember { mutableStateOf<List<ConditionPhoto>>(emptyList()) }
        LaunchedEffect(Unit) {
            allPhotos.value = conditionViewModel.getAllPhotosForPerson()
        }
        PdfExportActionHandler(
            showDialog = showPdfSettingsDialog,
            onDismiss = { showPdfSettingsDialog = false },
            category = Category.CONDITION_AT_VISIT,
            person = detailState.person,
            records = conditionState.records,
            viewModel = conditionViewModel,
            onRequireAuthentication = onRequireAuthentication,
            photos = allPhotos.value
        )
    }

    // 削除確認ダイアログ
    if (recordToDelete != null) {
        jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog(
            onDismiss = { recordToDelete = null },
            onDelete = {
                recordToDelete?.let { record ->
                    if (conditionState.selectedConditionId == record.id) conditionViewModel.setSelectedConditionId(null)
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
        jp.mydns.fujiwara.carememo.ui.components.base.AppDialog(
            onDismissRequest = { onConfirmOverwrite = null },
            title = { Text(stringResource(R.string.common_confirm_overwrite_title)) },
            text = {
                jp.mydns.fujiwara.carememo.ui.components.base.AppDialogContent(text = stringResource(R.string.common_confirm_overwrite_message))
            },
            confirmButton = {
                jp.mydns.fujiwara.carememo.ui.components.base.AppDialogConfirmButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onConfirmOverwrite?.invoke()
                        onConfirmOverwrite = null
                    }
                )
            },
            dismissButton = {
                jp.mydns.fujiwara.carememo.ui.components.base.AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onConfirmOverwrite = null }
                )
            }
        )
    }

    // 詳細・編集ダイアログ
    if (!isExpanded && conditionState.selectedConditionId != null && detailState.personId != null) {
        Dialog(
            onDismissRequest = { /* Pane側のキャンセル処理に委ねる */ },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                jp.mydns.fujiwara.carememo.ui.components.condition.ConditionDetailPane(
                    conditionId = conditionState.selectedConditionId!!,
                    records = conditionState.records,
                    photos = conditionState.currentConditionPhotos,
                    isProcessing = conditionState.isProcessing,
                    defaultRecorderName = defaultRecorderName,
                    onSaveRecord = { cId: String, s, onSuccess ->
                        conditionViewModel.saveRecord(cId, s.title, s.condition, s.author, s.recordTime ?: Instant.now(), onSuccess)
                    },
                    onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
                    onSelectedIdChange = { id: String -> conditionViewModel.setSelectedConditionId(
                        id.ifEmpty { null }) },
                    onCancel = { conditionViewModel.setSelectedConditionId(null) },
                    onAddPhotoClick = {
                        try {
                            val uri = ImageUtils.getTempPhotoUri(context)
                            tempPhotoUri = uri
                            conditionViewModel.setLockBypassEnabled(true)
                            cameraLauncher.launch(uri)
                        } catch (_: Exception) {
                            conditionViewModel.notifyPhotoError("カメラの起動準備に失敗しました。")
                        }
                    },
                    onPickPhotoClick = {
                        conditionViewModel.setLockBypassEnabled(true)
                        galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onReattachPhoto = { info ->
                        val cid = conditionState.selectedConditionId ?: ""
                        conditionViewModel.reattachOrphanedPhoto(cid, info)
                    },
                    orphanedPhotos = conditionState.availableOrphanedPhotos,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = { conditionViewModel.setLockBypassEnabled(true) }
                )
            }
        }
    }
}
