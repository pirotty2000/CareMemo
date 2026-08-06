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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    conditionViewModel: PersonConditionViewModel,
    navController: NavHostController
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
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = person,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "写真の確認"
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
                contentDescription = "プレビュー",
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("PhotoPreview_Image"),
                contentScale = ContentScale.Fit,
                onError = {
                }
            )
            
            AppTextField(
                value = caption,
                onValueChange = { caption = it },
                type = AppTextFieldType.TEXT,
                label = { Text("キャプション") },
                modifier = Modifier.fillMaxWidth().testTag("PhotoPreview_CaptionInput"),
                singleLine = true,
                enabled = !isProcessing
            )

            if (isProcessing) {
                LoadingScreen(
                    message = "画像を保存用に最適化しています...",
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
                        Text("削除")
                    }
                    Button(
                        onClick = {
                            conditionViewModel.processAndSavePhoto(context, uri, conditionId, caption)
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_SaveButton")
                    ) {
                        Text("保存する")
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
            message = "写真を削除しますか？"
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
