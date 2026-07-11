package jp.mydns.fujiwara.carememo.ui.components.condition

/**
 * Component：PersonConditionComponents
 *
 * 【役割】：
 * 所見記録（カテゴリB）に関連する履歴リスト、および詳細表示・編集用のパネルと
 * 写真管理（撮影・表示・削除）のための共通パーツ群を提供する。
 *
 * 【主な機能】：
 * ・履歴リスト（ConditionList）：時系列データの表示とスワイプ削除の基盤提供。
 * ・詳細パネル（ConditionDetailPane）：閲覧モードと編集モードの動的な切り替え。
 * ・閲覧表示（ConditionRecordDisplayCard）：テキストと写真の見やすいレイアウト提供。
 * ・編集フォーム（ConditionRecordEditForm）：テキスト入力と写真撮影の統合。
 * ・写真表示（PhotoGrid）：3列固定のレスポンシブなサムネイルグリッド表示。
 *
 * 【想定する利用場所】：
 * ・PersonConditionScreenContent（所見記録のメインコンテンツ領域）
 *
 * 【このコンポーネントでは行わないこと】：
 * ・データベースへの直接アクセス（すべて引数またはラムダ経由で外部から操作）
 * ・OSレベルのカメラ起動ロジック（親の Screen 層が担当）
 *
 * 【公開composable】：
 * ・ConditionList
 * ・ConditionDetailPane
 *
 * ---
 * 最終更新日: 2026/07/04
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
import java.time.Instant
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState

/**
 * 所見メモの一覧表示 (リストアイテム用)
 */
@Composable
private fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
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
                    contentDescription = "写真あり",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "記録者: ${record.author}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * 所見メモ用の履歴リスト。
 */
@Composable
fun ConditionList(
    records: List<Any>,
    selectedId: Int,
    conditionPhotoMap: Map<Int, Boolean>,
    isAnyDialogOpen: Boolean,
    onSelect: (Int) -> Unit,
    onDelete: (HistoryRecord) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    PersonHistoryList(
        records = records.filterIsInstance<HistoryRecord>(),
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
 * 所見メモ詳細ペイン。
 */
@Composable
fun ConditionDetailPane(
    personId: Int,
    conditionId: Int,
    records: List<ConditionAtVisit>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    defaultRecorderName: String,
    onSaveRecord: (ConditionAtVisit, (Int) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onSelectedIdChange: (Int) -> Unit,
    onAddPhotoClick: () -> Unit,
    onNavigateToFullScreen: (Int, Int) -> Unit,
    onMicClick: () -> Unit,
) {
    val memo = remember(records, conditionId) {
        records.find { it.id == conditionId }
    }

    if (memo == null && conditionId > 0) {
        LoadingScreen()
        return
    }

    var isEditing by remember(conditionId) { mutableStateOf(conditionId == 0) }
    val dateTimeState = rememberDateTimeInputState(initialInstant = memo?.recordTime)
    
    var title by remember(conditionId) { mutableStateOf(memo?.title ?: "") }
    var condition by remember(conditionId) { mutableStateOf(memo?.condition ?: "") }
    var author by remember(conditionId) { 
        mutableStateOf(memo?.author ?: defaultRecorderName) 
    }

    // 変更検知用の初期状態
    val initialDateTime = remember(conditionId) { memo?.recordTime }
    val initialTitle = remember(conditionId) { memo?.title ?: "" }
    val initialCondition = remember(conditionId) { memo?.condition ?: "" }
    val initialAuthor = remember(conditionId, defaultRecorderName) { memo?.author ?: defaultRecorderName }

    val isChanged by remember(title, condition, author, dateTimeState.year.value, dateTimeState.month.value, dateTimeState.day.value, dateTimeState.hour.value, dateTimeState.minute.value) {
        derivedStateOf {
            title != initialTitle ||
            condition != initialCondition ||
            author != initialAuthor ||
            dateTimeState.toInstant() != initialDateTime
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    // システム戻るボタンの制御
    androidx.activity.compose.BackHandler(enabled = isEditing && isChanged) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_discard_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        if (conditionId == 0) onSelectedIdChange(-1) else isEditing = false
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

    LaunchedEffect(defaultRecorderName) {
        if (conditionId == 0 && author.isBlank()) {
            author = defaultRecorderName
        }
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }

    val isDateTimeValid by remember(dateTimeState) {
        derivedStateOf { dateTimeState.toInstant() != null }
    }

    if (conditionId == -1) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("左のリストから記録を選択してください", color = MaterialTheme.colorScheme.outline)
                Text("右上の「＋」から新しい記録を追加できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

     if (isEditing) {
         ConditionRecordEditForm(
            conditionId = conditionId,
            dateTimeState = dateTimeState,
            title = title,
            onTitleChange = { title = it },
            author = author,
            onAuthorChange = { author = it },
            condition = condition,
            onConditionChange = { condition = it },
            photos = photos,
            isProcessing = isProcessing,
            isDateTimeValid = isDateTimeValid,
            onSave = {
                dateTimeState.toInstant()?.let { recordTime ->
                    val newMemo = ConditionAtVisit(
                        id = conditionId,
                        personId = personId,
                        title = title,
                        condition = condition,
                        author = author,
                        recordTime = recordTime
                    )
                    onSaveRecord(newMemo) { newId ->
                        onSelectedIdChange(newId)
                        isEditing = false
                    }
                }
            },
            onCancel = {
                if (isChanged) {
                    showDiscardDialog = true
                } else {
                    if (conditionId != 0) isEditing = false else onSelectedIdChange(-1)
                }
            },
            onAddPhotoClick = onAddPhotoClick,
            onDeletePhoto = { photoToDelete = it },
            onMicClick = onMicClick
        )
    } else {
        ConditionRecordDisplayCard(
            memo = memo,
            photos = photos,
            isProcessing = isProcessing,
            onEditClick = { isEditing = true },
            onPhotoClick = { onNavigateToFullScreen(it.conditionId, it.id) },
            onAddPhotoClick = onAddPhotoClick
        )
    }

    if (photoToDelete != null) {
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = { 
                photoToDelete?.let { onDeletePhoto(it) }
                photoToDelete = null
            },
            title = "写真の削除",
            message = "この写真を削除してもよろしいですか？"
        )
    }
}

@Composable
private fun ConditionRecordDisplayCard(
    memo: ConditionAtVisit?,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    onEditClick: () -> Unit,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onAddPhotoClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().testTag("ConditionDetailPane")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "記録の詳細",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    memo?.let { m ->
                        Text(text = DateTimeUtils.formatTime(m.recordTime),
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
                        Text(text = "記録者: ${m.author}",
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
                Text(text = "写真 (${photos.size}/${AppThresholds.CONDITION_PHOTO_MAX_COUNT})",
                    style = MaterialTheme.typography.titleMedium)
                if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT) {
                    IconButton(
                        onClick = onAddPhotoClick,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddAPhoto,
                            contentDescription = "写真を撮影",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (photos.isEmpty()) {
                Text("写真はありません", color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = false,
                    onPhotoClick = onPhotoClick,
                    onDeletePhoto = {}
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

@Composable
private fun ConditionRecordEditForm(
    conditionId: Int,
    dateTimeState: DateTimeInputState,
    title: String,
    onTitleChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    condition: String,
    onConditionChange: (String) -> Unit,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isDateTimeValid: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onMicClick: () -> Unit,
) {
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                val newCondition = "$condition$spokenText。\n"
                onConditionChange(newCondition)
            }
        }
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().testTag("ConditionDetailPane")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (conditionId == 0) "新規作成" else "記録の編集",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(state = dateTimeState)
                    HorizontalDivider(thickness = 0.5.dp)
                    AppTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("タイトル (任意)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = author,
                        onValueChange = onAuthorChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("記録者") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = condition,
                        onValueChange = onConditionChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("所見メモ") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("Condition_MemoInput"),
                        singleLine = false,
                        trailingIcon = {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力を開始します")
                                }
                                onMicClick()
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Rounded.Mic, contentDescription = "音声入力", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("Condition_SaveButton"),
                            enabled = author.isNotBlank() && condition.isNotBlank() && isDateTimeValid
                        ) { Text(stringResource(R.string.common_save)) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "写真 (${photos.size}/${AppThresholds.CONDITION_PHOTO_MAX_COUNT})", style = MaterialTheme.typography.titleMedium)
                if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT && conditionId != 0) {
                    IconButton(onClick = onAddPhotoClick, enabled = !isProcessing) {
                        Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = "写真を撮影", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (conditionId == 0) {
                Text(
                    text = stringResource(R.string.condition_photo_add_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (photos.isEmpty()) {
                Text("写真はありません", color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(photos = photos, isEditable = true, onPhotoClick = {}, onDeletePhoto = onDeletePhoto)
            }

            if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT && conditionId != 0) {
                Button(onClick = onAddPhotoClick, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("撮影")
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

@Composable
private fun PhotoGrid(
    photos: List<ConditionPhoto>,
    isEditable: Boolean,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current
    val rows = photos.chunked(3)
    Column(modifier = Modifier.fillMaxWidth().testTag("Condition_PhotoList"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowPhotos ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { photo ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                                contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize().clickable { onPhotoClick(photo) },
                                contentScale = ContentScale.Crop
                            )
                            if (isEditable) {
                                IconButton(
                                    onClick = { onDeletePhoto(photo) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
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

@Preview(showBackground = true)
@Composable
private fun PreviewConditionDetailPane() {
    MaterialTheme {
        ConditionDetailPane(
            personId = 1,
            conditionId = 1,
            records = listOf(ConditionAtVisit(id = 1, personId = 1, title = "サンプル", condition = "内容", author = "A", recordTime = Instant.now())),
            photos = emptyList(),
            isProcessing = false,
            defaultRecorderName = "A",
            onSaveRecord = { _, _ -> },
            onDeletePhoto = {},
            onSelectedIdChange = {},
            onAddPhotoClick = {},
            onNavigateToFullScreen = { _, _ -> },
            onMicClick = {}
        )
    }
}
