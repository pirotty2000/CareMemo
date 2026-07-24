package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoViewModel

/**
 * 迷子写真管理画面 (SCR-S-004)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanedPhotoManagementScreen(
    viewModel: OrphanedPhotoViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("迷子写真の確認") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        OrphanedPhotoManagementContent(
            uiState = uiState,
            onDelete = viewModel::deletePhoto,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
