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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.viewmodel.OrphanedPhotoViewModel

/**
 * 迷子写真管理画面 (SCR-S-004)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanedPhotoManagementScreen(
    viewModel: OrphanedPhotoViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var photoToDelete by remember { mutableStateOf<OrphanedPhotoInfo?>(null) }

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is OrphanedPhotoViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    // 削除確認ダイアログ
    photoToDelete?.let { info ->
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = { viewModel.deletePhoto(info) }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orphaned_photo_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        OrphanedPhotoManagementContent(
            uiState = uiState,
            onDelete = { photoToDelete = it },
            modifier = Modifier.padding(paddingValues)
        )
    }
}
