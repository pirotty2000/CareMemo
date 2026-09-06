package jp.mydns.fujiwara.carememo.ui.components.condition

/**
 * Component：PersonConditionComponents
 *
 * 【役割】
 * 利用者の「所見記録（カテゴリB）」に関連する履歴リスト、および詳細表示・編集パネル、
 * 写真管理（撮影・表示・削除）のための共通パーツ群を提供します。
 */

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import jp.mydns.fujiwara.carememo.ui.screens.condition.PersonConditionPreviewParameterProvider
import jp.mydns.fujiwara.carememo.ui.screens.condition.PersonConditionUiAction
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditInput
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditSession
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionOperation
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * [1] ConditionList
 * 所見メモ専用の履歴リスト。
 */
@Composable
fun ConditionList(
    records: ImmutableList<ConditionAtVisit>,
    selectedId: String?,
    conditionPhotoMap: Map<String, Boolean>,
    isAnyDialogOpen: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    PersonHistoryList(
        records = records,
        modifier = modifier,
        selectedRecordId = selectedId,
        onItemClick = { onSelect(it.id) },
        onDeleteSwipe = onDelete,
        isAnyDialogOpen = isAnyDialogOpen,
        lazyListState = lazyListState
    ) { record ->
        (record as? ConditionAtVisit)?.let {
            ConditionMemoContent(it, conditionPhotoMap[it.id] == true)
        }
    }
}

/**
 * [1-1] ConditionMemoContent
 */
@Composable
private fun ConditionMemoContent(
    record: ConditionAtVisit,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (!record.title.isNullOrBlank()) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record.condition ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhoto) {
                Icon(
                    imageVector = Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = stringResource(R.string.common_author_format, record.author),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * [2] ConditionDetailPane
 * 所見メモの詳細・編集ペイン。
 */
@Composable
fun ConditionDetailPane(
    uiState: PersonConditionUiState,
    onAction: (PersonConditionUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = uiState.editSession
    val memo = remember(uiState.records, session.selectedConditionId) {
        if ((session.selectedConditionId == null || IdLogic.isNew(session.selectedConditionId))) null
        else uiState.records.find { it.id == session.selectedConditionId }
    }

    if (memo == null && session.selectedConditionId != null && !IdLogic.isNew(session.selectedConditionId)) {
        LoadingScreen(modifier = modifier)
        return
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnassignedSelectDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = session.isEditing && session.isChanged) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        onAction(PersonConditionUiAction.CancelEdit)
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDiscardDialog = false }
                )
            }
        )
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }

    if (session.selectedConditionId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.p_detail_empty_records), color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.p_detail_empty_records_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    if (session.isEditing) {
        ConditionRecordEditForm(
            conditionId = session.selectedConditionId,
            operation = uiState.operation,
            editInput = session.editInput,
            initialRecordTime = session.initialRecordTime,
            photos = uiState.currentConditionPhotos,
            isProcessing = uiState.operation != PersonConditionOperation.Idle,
            isSaveEnabled = session.isSaveEnabled,
            isChanged = session.isChanged,
            fieldErrors = session.fieldErrors,
            unassignedPhotoCount = uiState.unassignedPhotoCount,
            onAction = onAction,
            onDeletePhotoRequest = { photoToDelete = it },
            onCancelRequest = {
                if (session.isChanged) showDiscardDialog = true
                else onAction(PersonConditionUiAction.CancelEdit)
            },
            onReattachRequest = { showUnassignedSelectDialog = true },
            modifier = modifier
        )
    } else {
        ConditionRecordDisplayCard(
            memo = memo,
            operation = uiState.operation,
            photos = uiState.currentConditionPhotos,
            unassignedPhotoCount = uiState.unassignedPhotoCount,
            onAction = onAction,
            onReattachRequest = { showUnassignedSelectDialog = true },
            modifier = modifier
        )
    }

    if (showUnassignedSelectDialog) {
        UnassignedPhotoSelectionDialog(
            unassignedPhotos = uiState.availableUnassignedPhotos,
            onDismiss = { showUnassignedSelectDialog = false },
            onSelect = { info ->
                onAction(PersonConditionUiAction.ReattachPhoto(info))
                showUnassignedSelectDialog = false
            }
        )
    }

    if (photoToDelete != null) {
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = {
                photoToDelete?.let { onAction(PersonConditionUiAction.DeletePhoto(it)) }
                photoToDelete = null
            },
            title = stringResource(R.string.condition_photo_delete_confirm_title),
            message = stringResource(R.string.condition_photo_delete_confirm_msg)
        )
    }
}

/**
 * [2-1] ConditionRecordEditForm
 */
@Composable
private fun ConditionRecordEditForm(
    conditionId: String,
    operation: PersonConditionOperation,
    editInput: ConditionEditInput,
    initialRecordTime: Instant?,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    isSaveEnabled: Boolean,
    isChanged: Boolean,
    fieldErrors: Map<String, Int?>,
    onAction: (PersonConditionUiAction) -> Unit,
    onDeletePhotoRequest: (ConditionPhoto) -> Unit,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier,
    unassignedPhotoCount: Int = 0,
    onReattachRequest: () -> Unit = {},
) {
    val isOperating = operation != PersonConditionOperation.Idle

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                onAction(PersonConditionUiAction.EditInputUpdate { it.copy(condition = "${it.condition}$spokenText。\n") })
            }
        }
    }

    val dateTimeState = rememberDateTimeInputState(initialInstant = initialRecordTime)

    LaunchedEffect(
        dateTimeState.year.value,
        dateTimeState.month.value,
        dateTimeState.day.value,
        dateTimeState.hour.value,
        dateTimeState.minute.value
    ) {
        val nextTime = dateTimeState.toInstant()
        if (nextTime != editInput.recordTime) {
            onAction(PersonConditionUiAction.EditInputUpdate { it.copy(recordTime = nextTime) })
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancelRequest,
                    enabled = !isOperating,
                    modifier = Modifier
                        .offset(x = (-12).dp)
                        .testTag("Condition_EditBackButton")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    text = if (IdLogic.isNew(conditionId)) stringResource(R.string.common_create_new) else stringResource(R.string.common_edit_record),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (-8).dp)
                )
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(
                        state = dateTimeState,
                        enabled = !isOperating,
                        isError = fieldErrors["recordTime"] != null,
                        supportingText = fieldErrors["recordTime"]?.let { resId -> { Text(stringResource(resId)) } },
                        onFocusChanged = { field, _ -> onAction(PersonConditionUiAction.MarkFieldAsTouched(field)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    AppTextField(
                        value = editInput.title,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(title = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_title_optional)) },
                        maxLength = AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE,
                        enabled = !isOperating,
                        isError = fieldErrors["title"] != null,
                        supportingText = fieldErrors["title"]?.let { resId -> { Text(stringResource(resId)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("title")) },
                        modifier = Modifier.fillMaxWidth().testTag("Condition_TitleInput")
                    )
                    AppTextField(
                        value = editInput.author,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(author = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_author)) },
                        enabled = !isOperating,
                        isError = fieldErrors["author"] != null,
                        supportingText = fieldErrors["author"]?.let { resId -> { Text(stringResource(resId)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("author")) },
                        modifier = Modifier.fillMaxWidth().testTag("Condition_AuthorInput")
                    )
                    AppTextField(
                        value = editInput.condition,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(condition = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_memo)) },
                        enabled = !isOperating,
                        isError = fieldErrors["condition"] != null,
                        supportingText = fieldErrors["condition"]?.let { resId -> { Text(stringResource(resId)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("condition")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("Condition_MemoInput"),
                        singleLine = false,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力を開始します")
                                    }
                                    onAction(PersonConditionUiAction.MicClick)
                                    speechLauncher.launch(intent)
                                },
                                enabled = !isOperating
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.condition_btn_mic_desc), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancelRequest,
                            enabled = !isOperating,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(if (isChanged) R.string.common_cancel else R.string.common_back)) }
                        Button(
                            onClick = { onAction(PersonConditionUiAction.SaveClick { onAction(PersonConditionUiAction.SelectedIdChanged(it)) }) },
                            modifier = Modifier.weight(1f).testTag("Condition_SaveButton"),
                            enabled = isSaveEnabled && !isOperating
                        ) {
                            if (operation is PersonConditionOperation.Saving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.common_save))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.common_photo_count_format, photos.size, AppSpecifications.Condition.Photo.MAX_COUNT), style = MaterialTheme.typography.titleMedium)

                Row {
                    if (unassignedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onReattachRequest, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = stringResource(R.string.common_unassigned_photo_reattach_title),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = { onAction(PersonConditionUiAction.PickPhotoClick) }, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.condition_btn_gallery_desc),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.AddPhotoClick) },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("Condition_AddPhotoButton")
                        ) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = stringResource(R.string.condition_btn_camera_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            
            if (IdLogic.isNew(conditionId)) {
                Text(
                    text = stringResource(R.string.condition_photo_add_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (photos.isEmpty()) {
                Text(stringResource(R.string.common_no_photos), color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = !isOperating,
                    onPhotoClick = {},
                    onDeletePhoto = onDeletePhotoRequest
                )
            }

            if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                Button(onClick = { onAction(PersonConditionUiAction.AddPhotoClick) }, enabled = !isOperating, modifier = Modifier.fillMaxWidth()) {
                    if (operation is PersonConditionOperation.PhotoProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.condition_btn_capture))
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * PhotoGrid
 */
@Composable
private fun PhotoGrid(
    photos: ImmutableList<ConditionPhoto>,
    isEditable: Boolean,
    modifier: Modifier = Modifier,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current
    val rows = photos.chunked(3)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("Condition_PhotoList"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowPhotos ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { photo ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                                contentDescription = photo.caption,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onPhotoClick(photo) }
                                    .testTag("ConditionPhoto_${photo.id}"),
                                contentScale = ContentScale.Crop
                            )
                            if (isEditable) {
                                IconButton(
                                    onClick = { onDeletePhoto(photo) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = photo.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - rowPhotos.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * [2-2] ConditionRecordDisplayCard
 */
@Composable
private fun ConditionRecordDisplayCard(
    memo: ConditionAtVisit?,
    operation: PersonConditionOperation,
    photos: ImmutableList<ConditionPhoto>,
    onAction: (PersonConditionUiAction) -> Unit,
    modifier: Modifier = Modifier,
    unassignedPhotoCount: Int = 0,
    onReattachRequest: () -> Unit = {},
) {
    val isOperating = operation != PersonConditionOperation.Idle
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAction(PersonConditionUiAction.SelectedIdChanged(null)) },
                        enabled = !isOperating,
                        modifier = Modifier
                            .offset(x = (-12).dp)
                            .testTag("Condition_DisplayBackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = stringResource(R.string.common_record_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }
                IconButton(
                    onClick = { onAction(PersonConditionUiAction.EditClick) },
                    enabled = !isOperating
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.common_edit))
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    memo?.let { m ->
                        Text(text = DateTimeUtils.formatRecordTime(m.recordTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!m.title.isNullOrBlank()) {
                            Text(text = m.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.common_author_format, m.author),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.common_photo_count_format, photos.size, AppSpecifications.Condition.Photo.MAX_COUNT),
                    style = MaterialTheme.typography.titleMedium)

                Row {
                    if (unassignedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onReattachRequest, enabled = !isOperating) {
                            Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = stringResource(R.string.common_unassigned_photo_reattach_title), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = { onAction(PersonConditionUiAction.PickPhotoClick) }, enabled = !isOperating) {
                            Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.condition_btn_gallery_desc), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.AddPhotoClick) },
                            enabled = !isOperating,
                            modifier = Modifier.testTag("Condition_AddPhotoButton")
                        ) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = stringResource(R.string.condition_btn_camera_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (photos.isEmpty()) {
                Text(stringResource(R.string.common_no_photos), color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = false,
                    onPhotoClick = { onAction(PersonConditionUiAction.NavigateToPhotoFullScreen(it.id, it.conditionId)) },
                    onDeletePhoto = {}
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * UnassignedPhotoSelectionDialog
 */
@Composable
private fun UnassignedPhotoSelectionDialog(
    unassignedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSelect: (jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo) -> Unit
) {
    val context = LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.common_unassigned_photo_reattach_title)) },
        text = {
            AppDialogContent {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.common_unassigned_photo_reattach_msg), style = MaterialTheme.typography.bodySmall)

                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 400.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unassignedPhotos.size) { index ->
                            val info = unassignedPhotos[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(info) }
                            ) {
                                Box(modifier = Modifier.aspectRatio(1f)) {
                                    AsyncImage(
                                        model = info.thumbnailFileName?.let { ImageUtils.getPhotoFile(context, it) },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(
                                    text = stringResource(info.descriptionResId),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppDialogDismissButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    )
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Previews
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun PreviewConditionDetailPane(
    @PreviewParameter(PersonConditionPreviewParameterProvider::class) state: PersonConditionPreviewState
) {
    MaterialTheme {
        ConditionDetailPane(
            uiState = PersonConditionUiState(
                screenState = jp.mydns.fujiwara.carememo.logic.feature.PersonConditionScreenState.Active,
                editSession = ConditionEditSession(
                    selectedConditionId = state.selectedRecordId
                ),
                records = state.records
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, name = "編集フォーム")
@Composable
private fun PreviewConditionRecordEditFormDirect() {
    MaterialTheme {
        ConditionRecordEditForm(
            conditionId = "new",
            operation = PersonConditionOperation.Idle,
            editInput = ConditionEditInput(
                title = MockData.condition.title ?: "",
                author = MockData.condition.author,
                condition = MockData.condition.condition ?: "",
                recordTime = Instant.now()
            ),
            initialRecordTime = Instant.now(),
            photos = persistentListOf(),
            isProcessing = false,
            isSaveEnabled = true,
            isChanged = false,
            fieldErrors = emptyMap(),
            unassignedPhotoCount = 0,
            onAction = {},
            onDeletePhotoRequest = {},
            onCancelRequest = {},
            onReattachRequest = {}
        )
    }
}
