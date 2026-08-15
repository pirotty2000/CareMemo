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
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.common.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailViewEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * Screen：PersonConditionScreen
 *
 * 【役割】
 * 利用者の所見記録（カテゴリB）画面のエントリポイントとなる最上位 Screen コンポーネントです。
 * デバイスの形状（Phone/Tablet）に応じたレイアウトの振り分け、共通ダイアログの制御、
 * および ViewModel からのイベント（通知、遷移等）の購読を担当します。
 *
 * 【主な機能】
 * ・マルチレイアウト制御：WindowWidthSizeClass に基づく Phone/Tablet 版の出し分け。
 * ・イベントハンドリング：ViewModel からの通知（Snackbar, ErrorDialog 等）の UI への反映。
 * ・共通ダイアログ管理：削除確認、上書き確認、PDF 設定、詳細編集（Phone版用ダイアログ）の表示制御。
 * ・外部連携：OS のカメラおよびギャラリー起動の仲介。
 *
 * 【全体像：所見記録画面階層（Condition Hierarchy）】
 *
 * ■ PersonConditionScreen (★本コンポーネント：全体制御)
 * │
 * ├─ [ A ] PersonConditionScreenPhone (Phone版：シングルペイン)
 * │    └─ PersonConditionScreenContent (リスト・詳細トグル)
 * │         └─ ConditionDetailPane (ダイアログ表示)
 * │
 * ├─ [ B ] PersonConditionScreenTablet (Tablet版：2ペイン固定)
 * │    └─ PersonConditionScreenContent (リスト・詳細並列表示)
 * │
 * └─ [ 共通パーツ ]
 *      ├─ PdfExportActionHandler (PDF出力制御)
 *      ├─ AppDeleteConfirmDialog (削除確認)
 *      └─ AppInfoDialog (通知・エラー)
 *
 * 【このコンポーネントでは行わないこと】
 * 実際の UI 描画（下位の ScreenPhone/Tablet または Content が担当）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    conditionViewModel: PersonConditionViewModel,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    onRequireAuthentication: (Int?, Int?, () -> Unit) -> Unit = { _, _, _ -> },
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val conditionState by conditionViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

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

    // 通知イベント監視
    LaunchedEffect(Unit) {
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

    // 共通的な画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        detailViewModel.viewEvent.collect { event ->
            when (event) {
                is PersonDetailViewEvent.NavigateToCategory -> {
                    detailState.personId?.let { personId ->
                        navController.navigate(event.category.toDestination(personId)) {
                            popUpTo<Destination.Main>()
                            launchSingleTop = true
                        }
                    }
                }
                is PersonDetailViewEvent.NavigateBackToMain -> {
                    navController.popBackStack(Destination.Main, inclusive = false)
                }
            }
        }
    }

    // 所見メモ固有の画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        conditionViewModel.viewEvent.collect { event ->
            when (event) {
                is PersonConditionViewEvent.LaunchCamera -> {
                    // ViewModel から直接 Camera 起動を要求する場合に備える (現在は Screen 側で制御)
                }
                is PersonConditionViewEvent.OpenPhotoPicker -> {
                    // ViewModel から直接 Picker 起動を要求する場合に備える (現在は Screen 側で制御)
                }
                is PersonConditionViewEvent.NavigateToPhotoPreview -> {
                    detailState.personId?.let { personId ->
                        navController.navigate(
                            Destination.PhotoPreview(event.uri, personId, event.conditionId)
                        )
                    }
                }
                is PersonConditionViewEvent.NavigateToPhotoFullScreen -> {
                    detailState.personId?.let { personId ->
                        navController.navigate(
                            Destination.PhotoFull(personId, event.conditionId, event.photoId)
                        )
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
            conditionViewModel.onPhotoCaptured(tempPhotoUri!!, conditionState.selectedConditionId ?: "")
        } else {
            if (!success && tempPhotoUri != null) {
                conditionViewModel.notifyPhotoError(context.getString(R.string.p_cond_err_photo_capture_failed))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            conditionViewModel.onPhotoCaptured(uri, conditionState.selectedConditionId ?: "")
        }
    }

    var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    val isAnyDialogOpen = recordToDelete != null || showPdfSettingsDialog || dialogMessage != null

    if (isExpanded) {
        // Tablet
        PersonConditionScreenTablet(
            uiState = conditionState,
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = detailState.personSummary,
            isAnyDialogOpen = isAnyDialogOpen,
            modifier = modifier,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            onSelectedIdChange = { conditionViewModel.setSelectedConditionId(it) },
            onBack = { detailViewModel.navigateBackToMain() },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            onAddPhotoClick = {
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    conditionViewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    conditionViewModel.notifyPhotoError(context.getString(R.string.p_cond_err_camera_launch_failed))
                }
            },
            onPickPhotoClick = {
                conditionViewModel.setLockBypassEnabled(true)
                galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onNavigateToFullScreen = { photoId, conditionId -> 
                conditionViewModel.navigateToPhotoFullScreen(photoId, conditionId) 
            },
            onShowPdfSettings = {
                if (conditionState.records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(noRecordsMsgFormat.format(conditionCategoryName)) }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            onEditClick = { conditionViewModel.startEditSession() },
            onEditInputUpdate = { conditionViewModel.updateEditInput(it) },
            onSaveClick = { onSuccess -> conditionViewModel.saveCurrentEdit(onSuccess) },
            onCancelEdit = { conditionViewModel.cancelEditSession() },
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            onReattachPhoto = { info ->
                val cid = conditionState.selectedConditionId ?: ""
                conditionViewModel.reattachUnassignedPhoto(cid, info)
            },
            onMicClick = { conditionViewModel.setLockBypassEnabled(true) },
            snackbarHostState = snackbarHostState
        )
    } else {
        // Phone
        PersonConditionScreenPhone(
            uiState = conditionState,
            currentPerson = detailState.person,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = detailState.personSummary,
            isAnyDialogOpen = isAnyDialogOpen,
            modifier = modifier,
            onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
            onSelectedIdChange = { conditionViewModel.setSelectedConditionId(it) },
            onBack = { detailViewModel.navigateBackToMain() },
            onNavigateToCategory = { detailViewModel.navigateToCategory(it) },
            // カメラ
            onAddPhotoClick = {
                try {
                    val uri = ImageUtils.getTempPhotoUri(context)
                    tempPhotoUri = uri
                    conditionViewModel.setLockBypassEnabled(true)
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    conditionViewModel.notifyPhotoError(context.getString(R.string.p_cond_err_camera_launch_failed))
                }
            },
            // ギャラリー
            onPickPhotoClick = {
                conditionViewModel.setLockBypassEnabled(true)
                galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            // 写真のフルスクリーン表示
            onNavigateToFullScreen = { photoId, conditionId -> 
                conditionViewModel.navigateToPhotoFullScreen(photoId, conditionId) 
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
            onEditClick = { conditionViewModel.startEditSession() },
            onEditInputUpdate = { conditionViewModel.updateEditInput(it) },
            onSaveClick = { onSuccess -> conditionViewModel.saveCurrentEdit(onSuccess) },
            onCancelEdit = { conditionViewModel.cancelEditSession() },
            // サムネイルのごみ箱アイコン
            onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
            // 未割り当て写真の再アタッチ処理
            onReattachPhoto = { info ->
                val cid = conditionState.selectedConditionId ?: ""
                conditionViewModel.reattachUnassignedPhoto(cid, info)
            },
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
            photos = allPhotos.value.toImmutableList()
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
                    uiState = conditionState,
                    onDeletePhoto = { conditionViewModel.deletePhoto(context, it) },
                    onSelectedIdChange = { conditionViewModel.setSelectedConditionId(it) },
                    onCancel = { conditionViewModel.setSelectedConditionId(null) },
                    onEditClick = { conditionViewModel.startEditSession() },
                    onEditInputUpdate = { conditionViewModel.updateEditInput(it) },
                    onSaveClick = { onSuccess -> conditionViewModel.saveCurrentEdit(onSuccess) },
                    onCancelEdit = { conditionViewModel.cancelEditSession() },
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
                        conditionViewModel.reattachUnassignedPhoto(cid, info)
                    },
                    onNavigateToFullScreen = { photoId, condId ->
                        conditionViewModel.navigateToPhotoFullScreen(photoId, condId)
                    },
                    onMicClick = { conditionViewModel.setLockBypassEnabled(true) }
                )
            }
        }
    }
}
