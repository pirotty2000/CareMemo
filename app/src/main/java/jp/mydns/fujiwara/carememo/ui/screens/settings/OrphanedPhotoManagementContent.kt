package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoUiState

@Composable
fun OrphanedPhotoManagementContent(
    uiState: OrphanedPhotoUiState,
    onDelete: (OrphanedPhotoInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading && uiState.orphanedPhotos.isEmpty()) {
        LoadingScreen(modifier = modifier.testTag("OrphanedPhoto_Loading"))
        return
    }

    if (uiState.orphanedPhotos.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.orphaned_photo_empty_msg),
            icon = Icons.Default.Info,
            modifier = modifier.fillMaxSize().testTag("OrphanedPhoto_EmptyState")
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize().testTag("OrphanedPhoto_Grid")
    ) {
        items(uiState.orphanedPhotos) { info ->
            OrphanedPhotoItem(
                info = info,
                onDelete = { onDelete(info) },
                modifier = Modifier.testTag("OrphanedPhoto_Item_${info.photoFileName}")
            )
        }
    }
}

@Composable
fun OrphanedPhotoItem(
    info: OrphanedPhotoInfo,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbFile = info.thumbnailFileName?.let { ImageUtils.getPhotoFile(context, it) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
                AsyncImage(
                    model = thumbFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // 削除ボタン
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("OrphanedPhoto_DeleteButton")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = Color.White)
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = stringResource(info.descriptionResId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = info.photoFileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
