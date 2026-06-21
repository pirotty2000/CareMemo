package jp.mydns.fujiwara.carememo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import java.io.File
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionDetailScreen(
    viewModel: PersonDetailViewModel,
    conditionId: Int,
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String) -> Unit,
    onEditMemo: (ConditionAtVisit) -> Unit
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val memo = records.filterIsInstance<ConditionAtVisit>().find { it.id == conditionId }
    val photos by viewModel.currentConditionPhotos.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    // カメラ撮影用の一時URI保持
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            onNavigateToPhotoPreview(tempPhotoUri!!, memo?.personId ?: 0, conditionId)
        }
    }

    LaunchedEffect(conditionId) {
        viewModel.setSelectedConditionId(conditionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("所見詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (memo != null) {
                        IconButton(onClick = { onEditMemo(memo) }) {
                            Icon(Icons.Default.Edit, contentDescription = "編集")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (memo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("データが見つかりません")
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
            // メモ内容表示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = formatRecordTime(memo.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!memo.title.isNullOrBlank()) {
                        Text(text = memo.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Text(text = memo.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "記録者: ${memo.author}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.End),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // 写真セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "写真 (${photos.size}/5)", style = MaterialTheme.typography.titleMedium)
                if (photos.size < 5) {
                    Button(
                        onClick = {
                            val tempFile = File(context.cacheDir, "temp_photo.jpg")
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
                }
            }

            if (photos.isEmpty()) {
                Text(
                    text = "写真がありません。カメラアイコンから撮影できます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                // 写真グリッド
                PhotoGrid(
                    photos = photos,
                    onPhotoClick = { onNavigateToFullScreen(it.photoFileName) },
                    onDeletePhoto = { viewModel.deletePhoto(context, it) }
                )
            }
            
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("処理中...", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<ConditionPhoto>,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit
) {
    val context = LocalContext.current
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.chunked(3).forEach { rowPhotos ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { photo ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        AsyncImage(
                            model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                            contentDescription = photo.caption,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onPhotoClick(photo) },
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onDeletePhoto(photo) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // 空きスペース埋め
                repeat(3 - rowPhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
