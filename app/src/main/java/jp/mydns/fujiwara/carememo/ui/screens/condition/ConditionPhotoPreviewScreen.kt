package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.core.net.toUri
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel

/**
 * UI Action：写真プレビュー画面におけるユーザー操作の集約定義
 */
sealed interface ConditionPhotoPreviewUiAction {
    data class UpdateCaption(val caption: String) : ConditionPhotoPreviewUiAction
    data object SaveClick : ConditionPhotoPreviewUiAction
    data object DeleteClick : ConditionPhotoPreviewUiAction
    data object ConfirmDelete : ConditionPhotoPreviewUiAction
    data object ConfirmDiscard : ConditionPhotoPreviewUiAction
    data object DismissDialog : ConditionPhotoPreviewUiAction
    data object Back : ConditionPhotoPreviewUiAction
}

/**
 * Screen：ConditionPhotoPreviewScreen
 *
 * 【役割】
 * 写真撮影直後に表示される、保存前の確認およびキャプション編集のための画面です。
 *
 * 【主な機能】
 * ・撮影画像のプレビュー表示。
 * ・キャプション入力：写真に対する説明文の編集。
 * ・保存・破棄：撮影した写真を記録に確定保存するか、破棄するかの選択。
 * ・変更保護：キャプション編集中の不用意な戻り操作に対する警告ダイアログの表示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    conditionViewModel: PersonConditionViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val conditionState by conditionViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val uriString = conditionState.previewUri ?: return
    val uri = remember(uriString) { uriString.toUri() }
    val conditionId = conditionState.selectedConditionId ?: ""

    // 初期キャプションの決定ロジック
    LaunchedEffect(uri) {
        if (conditionState.previewCaption.isEmpty()) {
            conditionViewModel.updatePreviewCaption(DateTimeUtils.getPhotoCaption(context, uri))
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // アクションハンドラ
    val handleAction: (ConditionPhotoPreviewUiAction) -> Unit = remember(conditionViewModel, navController, uri, conditionId, conditionState.previewCaption) {
        { action ->
            when (action) {
                is ConditionPhotoPreviewUiAction.UpdateCaption -> conditionViewModel.updatePreviewCaption(action.caption)
                ConditionPhotoPreviewUiAction.SaveClick -> {
                    conditionViewModel.processAndSavePhoto(uri, conditionId, conditionState.previewCaption)
                    navController.popBackStack()
                }
                ConditionPhotoPreviewUiAction.DeleteClick -> showDeleteConfirmDialog = true
                ConditionPhotoPreviewUiAction.ConfirmDelete -> {
                    showDeleteConfirmDialog = false
                    navController.popBackStack()
                }
                ConditionPhotoPreviewUiAction.ConfirmDiscard -> {
                    showDiscardConfirmDialog = false
                    navController.popBackStack()
                }
                ConditionPhotoPreviewUiAction.DismissDialog -> {
                    showDeleteConfirmDialog = false
                    showDiscardConfirmDialog = false
                }
                ConditionPhotoPreviewUiAction.Back -> {
                    if (conditionState.previewCaption.isNotEmpty()) {
                        showDiscardConfirmDialog = true
                    } else {
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    BackHandler(onBack = { handleAction(ConditionPhotoPreviewUiAction.Back) })

    ConditionPhotoPreviewContent(
        uiState = conditionState,
        person = detailState.person,
        isNameMaskingEnabled = isNameMaskingEnabled,
        onAction = handleAction,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        showDiscardConfirmDialog = showDiscardConfirmDialog,
        modifier = modifier
    )
}

/**
 * Screen：ConditionPhotoPreviewContent
 *
 * 【役割】
 * 写真撮影直後の保存前確認画面のレイアウト本体 (Stateless)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewContent(
    uiState: PersonConditionUiState,
    person: Person?,
    isNameMaskingEnabled: Boolean,
    onAction: (ConditionPhotoPreviewUiAction) -> Unit,
    showDeleteConfirmDialog: Boolean,
    showDiscardConfirmDialog: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriString = uiState.previewUri ?: return
    val uri = remember(uriString) { uriString.toUri() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = stringResource(R.string.condition_photo_preview_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(ConditionPhotoPreviewUiAction.Back) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // エラー表示
            uiState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(enable = true)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stringResource(R.string.common_preview),
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("PhotoPreview_Image"),
                contentScale = ContentScale.Fit
            )
            
            AppTextField(
                value = uiState.previewCaption,
                onValueChange = { onAction(ConditionPhotoPreviewUiAction.UpdateCaption(it)) },
                type = AppTextFieldType.TEXT,
                label = { Text(stringResource(R.string.condition_photo_caption_label)) },
                modifier = Modifier.fillMaxWidth().testTag("PhotoPreview_CaptionInput"),
                singleLine = true,
                enabled = !uiState.isProcessing
            )

            if (uiState.isProcessing) {
                LoadingScreen(
                    message = stringResource(R.string.condition_msg_photo_optimizing),
                    modifier = Modifier.testTag("PhotoPreview_Loading")
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAction(ConditionPhotoPreviewUiAction.DeleteClick) },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_DeleteButton")
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                    Button(
                        onClick = { onAction(ConditionPhotoPreviewUiAction.SaveClick) },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_SaveButton"),
                        enabled = true
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    if (showDeleteConfirmDialog) {
        AppDeleteConfirmDialog(
            onDismiss = { onAction(ConditionPhotoPreviewUiAction.DismissDialog) },
            onDelete = { onAction(ConditionPhotoPreviewUiAction.ConfirmDelete) },
            message = stringResource(R.string.condition_photo_delete_confirm_msg)
        )
    }

    // 変更破棄確認ダイアログ
    if (showDiscardConfirmDialog) {
        AppDialog(
            onDismissRequest = { onAction(ConditionPhotoPreviewUiAction.DismissDialog) },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    onClick = { onAction(ConditionPhotoPreviewUiAction.ConfirmDiscard) },
                    type = AppDialogActionType.DELETE
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onAction(ConditionPhotoPreviewUiAction.DismissDialog) }
                )
            }
        )
    }
}
