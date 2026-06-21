package jp.mydns.fujiwara.carememo.ui.screens

import android.net.Uri
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.io.File
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionDetailScreen(
    viewModel: PersonDetailViewModel,
    personId: Int,
    conditionId: Int,
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String) -> Unit
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val photos by viewModel.currentConditionPhotos.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()

    // 編集モードの状態
    var isEditing by remember { mutableStateOf(conditionId == 0) }
    
    // 入力用状態
    var title by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    // 日時状態
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }

    val memo = remember(records, conditionId) { 
        records.filterIsInstance<ConditionAtVisit>().find { it.id == conditionId }
    }

    // 初期値セット
    LaunchedEffect(memo, isEditing) {
        if (memo != null && title.isEmpty() && condition.isEmpty()) {
            title = memo.title ?: ""
            condition = memo.condition ?: ""
            author = memo.author
            
            val zdt = memo.recordTime.atZone(java.time.ZoneId.systemDefault())
            year = zdt.year.toString()
            month = zdt.monthValue.toString()
            day = zdt.dayOfMonth.toString()
            hour = "%02d".format(zdt.hour)
            minute = "%02d".format(zdt.minute)
        } else if (conditionId == 0 && author.isEmpty()) {
            author = defaultRecorderName
            val now = java.time.LocalDateTime.now()
            year = now.year.toString()
            month = now.monthValue.toString()
            day = now.dayOfMonth.toString()
            hour = "%02d".format(now.hour)
            minute = "%02d".format(now.minute)
        }
    }

    // データのロード
    LaunchedEffect(personId, conditionId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, jp.mydns.fujiwara.carememo.data.Category.CONDITION_AT_VISIT)
        viewModel.setSelectedConditionId(if (conditionId != 0) conditionId else null)
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            onNavigateToPhotoPreview(tempPhotoUri!!, personId, conditionId)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                condition += (if (condition.isEmpty()) "" else "\n") + spokenText + "。"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (conditionId == 0) "新規記録" else if (isEditing) "記録の編集" else "所見詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            if (conditionId == 0) onBack() else isEditing = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "キャンセル")
                        }
                        IconButton(
                            onClick = {
                                val recordTime = try {
                                    java.time.LocalDateTime.of(
                                        year.toInt(), month.toInt(), day.toInt(),
                                        hour.toInt(), minute.toInt()
                                    ).atZone(java.time.ZoneId.systemDefault()).toInstant()
                                } catch (e: Exception) {
                                    memo?.recordTime ?: Instant.now()
                                }

                                val newMemo = ConditionAtVisit(
                                    id = conditionId,
                                    personId = personId,
                                    title = title,
                                    condition = condition,
                                    author = author,
                                    recordTime = recordTime
                                )
                                viewModel.saveRecord(newMemo)
                                if (conditionId == 0) {
                                    // 新規作成時は保存後に一覧へ戻る
                                    onBack()
                                } else {
                                    isEditing = false
                                }
                            },
                            enabled = author.isNotBlank() && condition.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "編集")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (memo == null && conditionId != 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditing) {
                // 編集モード
                Text("記録日時", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactTextField(value = year, onValueChange = { val filtered = it.filter { c -> c.isDigit() }; if (filtered.length <= 4) year = filtered }, modifier = Modifier.weight(1.5f), suffix = { Text("年", style = MaterialTheme.typography.bodySmall) })
                    CompactTextField(value = month, onValueChange = { val filtered = it.filter { c -> c.isDigit() }; if (filtered.length <= 2) month = filtered }, modifier = Modifier.weight(1f), suffix = { Text("月", style = MaterialTheme.typography.bodySmall) })
                    CompactTextField(value = day, onValueChange = { val filtered = it.filter { c -> c.isDigit() }; if (filtered.length <= 2) day = filtered }, modifier = Modifier.weight(1f), suffix = { Text("日", style = MaterialTheme.typography.bodySmall) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactTextField(value = hour, onValueChange = { val filtered = it.filter { c -> c.isDigit() }; if (filtered.length <= 2) hour = filtered }, modifier = Modifier.weight(1f), suffix = { Text("時", style = MaterialTheme.typography.bodySmall) })
                    CompactTextField(value = minute, onValueChange = { val filtered = it.filter { c -> c.isDigit() }; if (filtered.length <= 2) minute = filtered }, modifier = Modifier.weight(1f), suffix = { Text("分", style = MaterialTheme.typography.bodySmall) })
                    Spacer(modifier = Modifier.weight(1.5f))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル (任意)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("記録者") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("所見メモ") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toString())
                            }
                            speechLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "音声入力")
                        }
                    }
                )
            } else {
                // 参照モード
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        memo?.let { m ->
                            Text(
                                text = formatRecordTime(m.recordTime),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!m.title.isNullOrBlank()) {
                                Text(text = m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "記録者: ${m.author}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.End),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // 写真セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "写真 (${photos.size}/3)", style = MaterialTheme.typography.titleMedium)
                if (isEditing && photos.size < 3 && conditionId != 0) {
                    Button(
                        onClick = {
                            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            tempPhotoUri = uri
                            cameraLauncher.launch(uri)
                        },
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("撮影")
                    }
                } else if (isEditing && conditionId == 0) {
                    Text(
                        text = "※所見メモの記録には、後から写真を撮影して残すこともできます",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (photos.isEmpty()) {
                if (!isEditing) {
                    Text("写真はありません", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = isEditing,
                    onPhotoClick = { onNavigateToFullScreen(it.photoFileName) },
                    onDeletePhoto = { photoToDelete = it }
                )
            }
            
            if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text("処理中...")
                }
            }
        }
    }

    if (photoToDelete != null) {
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("写真の削除") },
            text = { Text("この写真を削除してもよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        photoToDelete?.let { viewModel.deletePhoto(context, it) }
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
fun PhotoGrid(
    photos: List<ConditionPhoto>,
    isEditable: Boolean,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit
) {
    val context = LocalContext.current
    
    // 最大3枚なので1行で表示
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
                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = photo.caption,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // 3枚に満たない場合のスペース埋め
        repeat(3 - photos.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        interactionSource = interactionSource,
        enabled = true,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                suffix = suffix,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape,
                    )
                }
            )
        }
    )
}
