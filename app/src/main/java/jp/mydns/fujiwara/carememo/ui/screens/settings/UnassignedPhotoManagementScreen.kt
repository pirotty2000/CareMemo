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
import jp.mydns.fujiwara.carememo.viewmodel.UnassignedPhotoUiState
import jp.mydns.fujiwara.carememo.viewmodel.UnassignedPhotoViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
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
 * ストレージ容量の節約や、データの整合性維持のための保守機能を提供します。
 *
 * 【主な機能】
 * ・一覧表示：`UnassignedPhotoManagementContent` による孤立した写真のサムネイル表示。
 * ・削除操作：不要になった画像ファイルのストレージからの完全削除。
 * ・安全性：削除実行前に `AppDeleteConfirmDialog` による確認を強制。
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

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is UnassignedPhotoViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    // アクションハンドラ
    // 削除対象の選択状態をキーに含め、ダイアログ表示状態の変化に追従する
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

    // 削除確認ダイアログ
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
                    IconButton(onClick = { handleAction(UnassignedPhotoUiAction.Back) }, modifier = Modifier.testTag("UnassignedPhoto_BackButton")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
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
