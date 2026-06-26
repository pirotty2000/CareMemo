package jp.mydns.fujiwara.carememo.ui.screens

import android.net.Uri
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.ui.components.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.rememberDateTimeInputState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatRecordTime
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
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val photos by viewModel.currentConditionPhotos.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val defaultRecorderName by viewModel.defaultRecorderName.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    val dateTimeState = rememberDateTimeInputState()

    // 編集モードの状態
    var isEditing by rememberSaveable { mutableStateOf(conditionId == 0) }

    // 入力用状態
    var title by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }

    val memo = remember(records, conditionId) { 
        records.asSequence().filterIsInstance<ConditionAtVisit>().find { it.id == conditionId }
    }

    // 初期値セット
    LaunchedEffect(memo) {
        if ((memo != null) && title.isEmpty() && condition.isEmpty()) {
            title = memo.title ?: ""
            condition = memo.condition ?: ""
            author = memo.author
            dateTimeState.setFromInstant(memo.recordTime)
        } else if ((conditionId == 0) && dateTimeState.year.value.isEmpty()) {
            // 新規作成時：現在の日時をセット
            dateTimeState.setFromInstant(Instant.now())
            
            if (author.isEmpty() && defaultRecorderName.isNotEmpty()) {
                author = defaultRecorderName
            }
        }
    }

    // デフォルト記録者が後から読み込まれた場合の考慮
    LaunchedEffect(defaultRecorderName) {
        if (conditionId == 0 && author.isEmpty() && defaultRecorderName.isNotEmpty()) {
            author = defaultRecorderName
        }
    }

    // データのロード
    LaunchedEffect(personId, conditionId) {
        viewModel.loadPerson(personId)
        viewModel.loadRecords(personId, jp.mydns.fujiwara.carememo.data.Category.CONDITION_AT_VISIT)
        viewModel.setSelectedConditionId(if (conditionId != 0) conditionId else null)
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
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
                // 句読点の自動補完と改行の挿入
                val formattedText = buildString {
                    append(spokenText.trim())
                    // 末尾が句読点等でなければ「。」を付与
                    if (!spokenText.trim().any { it in "。、？！?.!" }) {
                        append("。")
                    }
                    append("\n")
                }
                
                // 既存の文章があれば末尾に追加
                condition = if (condition.isBlank()) {
                    formattedText
                } else {
                    // 既存の末尾が改行でなければ改行を挟んでから追加
                    val separator = if (condition.endsWith("\n")) "" else "\n"
                    condition + separator + formattedText
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = "所見記録"
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isEditing && conditionId != 0) isEditing = false else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Rounded.EditNote, contentDescription = "編集")
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
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateTimeInputFields(state = dateTimeState)

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        viewModel.setLockBypassEnabled(true)
                                        speechLauncher.launch(intent)
                                    }) {
                                        Icon(Icons.Rounded.Mic, contentDescription = "音声入力")
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { if (conditionId == 0) onBack() else isEditing = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("キャンセル")
                                }
                                Button(
                                    onClick = {
                                        val recordTime = dateTimeState.toInstant() ?: Instant.now()

                                        val newMemo = ConditionAtVisit(
                                            id = conditionId,
                                            personId = personId,
                                            title = title,
                                            condition = condition,
                                            author = author,
                                            recordTime = recordTime
                                        )
                                        viewModel.saveRecord(newMemo)
                                        if (conditionId == 0) onBack() else isEditing = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = author.isNotBlank() && condition.isNotBlank()
                                ) {
                                    Text("保存")
                                }
                            }
                        }
                    }
                }
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
                            viewModel.setLockBypassEnabled(true)
                            cameraLauncher.launch(uri)
                        },
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("撮影")
                    }
                } else if (isEditing && conditionId == 0) {
                    Spacer(modifier = Modifier.width(16.dp)) // 少し右にずらすためのスペース
                    Text(
                        text = "※所見メモの記録には、後から写真を撮影して残すこともできます",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
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
                    onPhotoClick = { onNavigateToFullScreen(it.photoFileName, it.caption) },
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
    onDeletePhoto: (ConditionPhoto) -> Unit,
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
                            Icon(Icons.Rounded.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
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

