package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.time.Instant

/**
 * 所見メモの内容表示 (リストアイテム用)
 */
@Composable
fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
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
 * 検索ボックス (所見メモ用)
 */
@Composable
fun SearchBox(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    placeholder: String = "所見メモを検索..."
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "クリア")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

/**
 * 所見メモ用の履歴リスト (EmptyState管理込み)
 */
@Composable
fun ObservationList(
    records: List<Any>,
    selectedId: Int,
    conditionPhotoMap: Map<Int, Boolean>,
    onSelect: (Int) -> Unit,
    onDelete: (HistoryRecord) -> Unit
) {
    if (records.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.empty_records),
            description = stringResource(R.string.empty_records_description),
            icon = Icons.Outlined.Description
        )
    } else {
        PersonHistoryList(
            records = records.filterIsInstance<HistoryRecord>(),
            selectedRecordId = selectedId,
            onItemClick = { onSelect(it.id) },
            onDeleteSwipe = onDelete,
            isAnyDialogOpen = false
        ) { record ->
            (record as? ConditionAtVisit)?.let {
                ConditionMemoContent(it, conditionPhotoMap[it.id] == true)
            }
        }
    }
}

/**
 * 所見メモ詳細ペイン
 */
@Composable
fun ConditionDetailPane(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    conditionId: Int,
    onNavigateToPhotoPreview: (android.net.Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val records by conditionViewModel.records.collectAsState()
    val photos by conditionViewModel.currentConditionPhotos.collectAsState()
    val isProcessing by conditionViewModel.isProcessing.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()

    val dateTimeState = rememberDateTimeInputState()
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(conditionId) { isEditing = conditionId == 0 }

    val memo = remember(records, conditionId) {
        records.find { it.id == conditionId }
    }

    var title by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    LaunchedEffect(memo, defaultRecorderName, conditionId) {
        if (memo != null) {
            title = memo.title ?: ""
            condition = memo.condition ?: ""
            author = memo.author
            dateTimeState.setFromInstant(memo.recordTime)
        } else if (conditionId == 0) {
            title = ""
            condition = ""
            author = defaultRecorderName
            dateTimeState.setFromInstant(Instant.now())
        }
    }

    LaunchedEffect(personId, conditionId) {
        conditionViewModel.loadPerson(personId)
        conditionViewModel.setSelectedConditionId(if (conditionId != 0) conditionId else null)
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoUri != null) {
            onNavigateToPhotoPreview(tempPhotoUri!!, personId, conditionId)
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (conditionId == 0) "新規作成" else "記録の詳細",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isEditing && conditionId != 0) {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }
        }

        if (isEditing) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(state = dateTimeState)
                    HorizontalDivider(thickness = 0.5.dp)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("タイトル (任意)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("記録者") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("所見メモ") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (conditionId == 0) { /* nop */ } else isEditing = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                        Button(
                            onClick = {
                                val recordTime = dateTimeState.toInstant() ?: Instant.now()
                                val newMemo = ConditionAtVisit(id = conditionId, personId = personId, title = title, condition = condition, author = author, recordTime = recordTime)
                                conditionViewModel.saveRecord(newMemo)
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = author.isNotBlank() && condition.isNotBlank()
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    memo?.let { m ->
                        Text(text = formatTime(m.recordTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!m.title.isNullOrBlank()) {
                            Text(text = m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "記録者: ${m.author}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        Text(text = "写真 (${photos.size}/3)", style = MaterialTheme.typography.titleMedium)
        if (photos.isEmpty()) {
            Text("写真はありません", color = MaterialTheme.colorScheme.outline)
        } else {
            PhotoGrid(photos = photos, isEditable = isEditing, onPhotoClick = { onNavigateToFullScreen(it.photoFileName, it.caption) }, onDeletePhoto = { photoToDelete = it })
        }

        if (isEditing && photos.size < 3 && conditionId != 0) {
            Button(onClick = {
                val tempFile = java.io.File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            }, enabled = !isProcessing) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("撮影")
            }
        }
    }

    if (photoToDelete != null) {
        AlertDialog(onDismissRequest = { photoToDelete = null }, title = { Text("写真の削除") }, text = { Text("この写真を削除してもよろしいですか？") }, confirmButton = { TextButton(onClick = { photoToDelete?.let { conditionViewModel.deletePhoto(context, it) }; photoToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("削除") } }, dismissButton = { TextButton(onClick = { photoToDelete = null }) { Text("キャンセル") } })
    }
}

/**
 * 写真グリッド表示
 */
@Composable
fun PhotoGrid(
    photos: List<ConditionPhoto>,
    isEditable: Boolean,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEach { photo ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    AsyncImage(
                        model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                        contentDescription = photo.caption,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPhotoClick(photo) },
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
                Text(
                    text = photo.caption,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        repeat(3 - photos.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
