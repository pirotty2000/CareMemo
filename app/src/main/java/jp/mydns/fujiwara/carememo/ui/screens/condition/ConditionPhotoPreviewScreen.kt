package jp.mydns.fujiwara.carememo.ui.screens.condition

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    uri: Uri,
    personId: Int,
    conditionId: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val isProcessing by conditionViewModel.isProcessing.collectAsStateWithLifecycle()
    val errorMessage by conditionViewModel.errorMessage.collectAsStateWithLifecycle()
    val currentPerson by viewModel.currentPerson.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

    val initialCaption = remember { DateTimeUtils.getCurrentPhotoCaption() }
    var caption by remember { mutableStateOf(initialCaption) }
    val isModified = caption != initialCaption

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (isModified) {
            showDiscardConfirmDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = currentPerson,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "写真の確認"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("PhotoPreview_BackButton")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
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
                    // 読み込みエラー時の処理（本来はViewModelで管理すべきだが、ここでは簡易的にメッセージを表示する仕組みに合わせる）
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
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_DeleteButton"),
                        enabled = !isProcessing
                    ) {
                        Text("削除")
                    }
                    Button(
                        onClick = {
                            conditionViewModel.processAndSavePhoto(context, uri, personId, conditionId, caption)
                            onSaved()
                        },
                        modifier = Modifier.weight(1f).testTag("PhotoPreview_SaveButton"),
                        enabled = !isProcessing
                    ) {
                        Text("保存する")
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("確認") },
            text = { Text("写真を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        conditionViewModel.deleteTempFile(context, uri)
                        onBack()
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 変更破棄確認ダイアログ
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("確認") },
            text = { Text("変更を破棄しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("破棄")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
