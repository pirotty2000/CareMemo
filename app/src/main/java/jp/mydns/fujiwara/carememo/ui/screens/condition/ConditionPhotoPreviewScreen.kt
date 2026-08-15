package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel

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
    
    val isProcessing = conditionState.isProcessing
    val errorMessage = conditionState.errorMessage
    val person = detailState.person
    val uriString = conditionState.previewUri ?: return
    val uri = uriString.toUri()
    val conditionId = conditionState.selectedConditionId ?: ""

    val initialCaption = remember(uri) { DateTimeUtils.getPhotoCaption(context, uri) }
    var caption by remember { mutableStateOf(initialCaption) }
    val isModified = caption != initialCaption

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (isModified) {
            showDiscardConfirmDialog = true
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(onBack = handleBack)

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
            errorMessage?.let { msg ->
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
                contentScale = ContentScale.Fit,
                onError = {
                }
            )
            
            AppTextField(
                value = caption,
                onValueChange = { caption = it },
                type = AppTextFieldType.TEXT,
                label = { Text(stringResource(R.string.condition_photo_caption_label)) },
                modifier = Modifier.fillMaxWidth().testTag("PhotoPreview_CaptionInput"),
                singleLine = true,
                enabled = !isProcessing
            )

            if (isProcessing) {
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
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_DeleteButton")
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                    Button(
                        onClick = {
                            conditionViewModel.processAndSavePhoto(uri, conditionId, caption)
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_SaveButton")
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
            onDismiss = { showDeleteConfirmDialog = false },
            onDelete = {
                showDeleteConfirmDialog = false
                navController.popBackStack()
            },
            message = stringResource(R.string.condition_photo_delete_confirm_msg)
        )
    }

    // 変更破棄確認ダイアログ
    if (showDiscardConfirmDialog) {
        AppDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    onClick = {
                        showDiscardConfirmDialog = false
                        navController.popBackStack()
                    },
                    type = AppDialogActionType.DELETE
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDiscardConfirmDialog = false }
                )
            }
        )
    }
}
