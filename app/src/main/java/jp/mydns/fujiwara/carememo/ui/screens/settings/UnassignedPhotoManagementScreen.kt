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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoOperation
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.viewmodel.UnassignedPhotoViewModel

/**
 * UI Action：未割り当て写真管理画面におけるユーザー操作の集約定義
 */
sealed interface UnassignedPhotoUiAction {
    data class DeleteRequest(val info: UnassignedPhotoInfo) : UnassignedPhotoUiAction
    data object ConfirmDelete : UnassignedPhotoUiAction
    data object Back : UnassignedPhotoUiAction
    data object DismissDialog : UnassignedPhotoUiAction
}

/**
 * Screen：UnassignedPhotoManagementScreen
 *
 * 【役割】
 * DB レコード（経過記録）との紐付けが失われた「未割り当て」の画像ファイル（SCR-S-004）を一覧管理するための画面です。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnassignedPhotoManagementScreen(
    viewModel: UnassignedPhotoViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var photoToDelete by remember { mutableStateOf<UnassignedPhotoInfo?>(null) }
    val isOperating = uiState.operation != UnassignedPhotoOperation.Idle

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is UnassignedPhotoViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    val handleAction: (UnassignedPhotoUiAction) -> Unit = remember(viewModel, photoToDelete) {
        { action ->
            when (action) {
                is UnassignedPhotoUiAction.DeleteRequest -> photoToDelete = action.info
                UnassignedPhotoUiAction.ConfirmDelete -> {
                    photoToDelete?.let { viewModel.deletePhoto(it) }
                    photoToDelete = null
                }
                UnassignedPhotoUiAction.Back -> viewModel.navigateBack()
                UnassignedPhotoUiAction.DismissDialog -> photoToDelete = null
            }
        }
    }

    photoToDelete?.let {
        AppDeleteConfirmDialog(
            onDismiss = { handleAction(UnassignedPhotoUiAction.DismissDialog) },
            onDelete = { handleAction(UnassignedPhotoUiAction.ConfirmDelete) }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.unassigned_photo_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { handleAction(UnassignedPhotoUiAction.Back) },
                        enabled = !isOperating,
                        modifier = Modifier.testTag("UnassignedPhoto_BackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        }
    ) { paddingValues ->
        UnassignedPhotoManagementContent(
            uiState = uiState,
            onAction = handleAction,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
