package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonConditionScreen
 *
 * 【画面名】
 * 利用者所見記録画面
 *
 * 【役割】
 * 利用者の日々の様子や気になる変化を「所見メモ（カテゴリB）」として詳細に記録・閲覧する画面。
 * テキストによる記録に加え、写真撮影による視覚的な記録保存も行う。
 *
 * 【主な機能】
 * ・所見一覧：時系列での所見履歴表示。
 * ・詳細登録：タイトル、内容、記録者、日時の登録。
 * ・写真管理：カメラ撮影またはギャラリーからの画像取り込み、および写真のフルスクリーン表示。
 * ・PDF出力：所見履歴と写真をまとめたPDFレポートの作成。
 * ・レスポンシブUI：画面サイズに応じたPhone用・Tablet用レイアウトの切り替え。
 *
 * 【遷移】
 * ← MainScreen（戻るボタン）
 * → ConditionPhotoFullScreen / ConditionPhotoPreviewScreen
 *
 * 【使用するViewModel】
 * PersonDetailViewModel, PersonConditionViewModel
 *
 * 【備考】
 * 文字だけでは伝わりにくい患部の状態などを写真として残すことで、より正確な情報の共有を可能にする。
 */

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.PdfExportActionHandler
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun PersonConditionScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val scope = rememberCoroutineScope()

    // データの監視
    val records by conditionViewModel.filteredRecords.collectAsState()
    val isLoading by conditionViewModel.isLoading.collectAsState()
    val searchQuery by conditionViewModel.searchQuery.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()
    val personCategorySummary by viewModel.personCategorySummary.collectAsState()
    val conditionPhotoMap by conditionViewModel.conditionPhotoMap.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 選択中のID管理
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    var showPdfSettingsDialog by remember { mutableStateOf(false) }

    // 初期ロード
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
        viewModel.setCategory(Category.CONDITION_AT_VISIT)
        conditionViewModel.loadPerson(personId)
        // 画面遷移時に選択をリセット
        selectedId = -1
    }

    // IDが選択された際の処理は、詳細ペイン（Component）側の LaunchedEffect に任せるため削除

    if (isExpanded) {
        PersonConditionScreenTablet(
            viewModel = viewModel,
            conditionViewModel = conditionViewModel,
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            conditionPhotoMap = conditionPhotoMap,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onNavigateToPhotoPreview = onNavigateToPhotoPreview,
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            snackbarHostState = snackbarHostState
        )
    } else {
        PersonConditionScreenPhone(
            viewModel = viewModel,
            conditionViewModel = conditionViewModel,
            personId = personId,
            currentPerson = currentPerson,
            isNameMaskingEnabled = isNameMaskingEnabled,
            personCategorySummary = personCategorySummary,
            records = records,
            isLoading = isLoading,
            searchQuery = searchQuery,
            conditionPhotoMap = conditionPhotoMap,
            selectedId = selectedId,
            onSelectedIdChange = { selectedId = it },
            onBack = onBack,
            onNavigateToCategory = onNavigateToCategory,
            onNavigateToPhotoPreview = onNavigateToPhotoPreview,
            onNavigateToFullScreen = onNavigateToFullScreen,
            onShowPdfSettings = {
                if (records.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar("出力するデータがありません") }
                } else {
                    showPdfSettingsDialog = true
                }
            },
            onDeleteRecord = { recordToDelete = it },
            snackbarHostState = snackbarHostState
        )
    }

    // PDF出力共通ハンドラー
    if (showPdfSettingsDialog) {
        val allPhotos = remember { mutableStateOf<List<jp.mydns.fujiwara.carememo.data.ConditionPhoto>>(emptyList()) }
        LaunchedEffect(Unit) {
            allPhotos.value = conditionViewModel.getAllPhotosForPerson(personId)
        }
        
        PdfExportActionHandler(
            showDialog = showPdfSettingsDialog,
            onDismiss = { showPdfSettingsDialog = false },
            category = Category.CONDITION_AT_VISIT,
            person = currentPerson,
            records = records,
            isNameMaskingEnabled = isNameMaskingEnabled,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            photos = allPhotos.value
        )
    }

    // 削除確認ダイアログ
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_data_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { record ->
                            if (selectedId == record.id) selectedId = -1
                            if (record is jp.mydns.fujiwara.carememo.data.ConditionAtVisit) {
                                conditionViewModel.deleteRecord(record)
                            }
                        }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
