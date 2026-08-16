package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle

/**
 * Screen：PersonConditionScreenPhone
 *
 * 【役割】
 * スマートフォン等の狭い画面（WindowWidthSizeClass.Compact/Medium）向けに最適化された所見記録画面です。
 *
 * 【主な機能】
 * ・シングルペイン制御：リスト表示と詳細表示（ダイアログ）を切り替えて表示。
 * ・ナビゲーション統合：TopAppBar へのタイトル、戻るボタン、および PDF 出力ボタンの配置。
 * ・フローティングアクションボタン（FAB）：新規レコード作成のショートカット提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenPhone(
    uiState: PersonConditionUiState,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    isAnyDialogOpen: Boolean,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit,
    onSelectedIdChange: (String?) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onNavigateToFullScreen: (String, String) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onEditClick: () -> Unit,
    onEditInputUpdate: ((ConditionEditInput) -> ConditionEditInput) -> Unit,
    onSaveClick: ((String) -> Unit) -> Unit,
    onCancelEdit: () -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo) -> Unit,
    onMicClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = modifier.testTag("ConditionScreen_PhoneContent"),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = "",
                            modifier = Modifier.testTag("PersonHeader_Title")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (uiState.selectedConditionId != null) onCancelEdit() else onBack() },
                            modifier = Modifier.testTag("ConditionScreen_BackButton")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        if (uiState.selectedConditionId == null) {
                            IconButton(
                                onClick = onShowPdfSettings,
                                modifier = Modifier.testTag("ConditionScreen_PdfButton")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.common_pdf_export))
                            }
                        }
                    },
                    colors = appTopAppBarColors()
                )
                CategorySelectorBar(
                    currentCategory = Category.CONDITION_AT_VISIT,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory,
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedConditionId == null) {
                FloatingActionButton(
                    onClick = { onSelectedIdChange(AppSpecifications.Id.NEW_RECORD_ID) },
                    modifier = Modifier.testTag("ConditionScreen_AddButton")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_create_new))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading && uiState.records.isEmpty()) {
            LoadingScreen(modifier = Modifier.padding(padding))
        } else {
            Box(modifier = Modifier.padding(padding)) {
                PersonConditionScreenContent(
                    isExpanded = false,
                    uiState = uiState,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectedIdChange = onSelectedIdChange,
                    onDeleteRecord = onDeleteRecord,
                    onEditClick = onEditClick,
                    onEditInputUpdate = onEditInputUpdate,
                    onSaveClick = onSaveClick,
                    onCancelEdit = onCancelEdit,
                    onDeletePhoto = onDeletePhoto,
                    onAddPhotoClick = onAddPhotoClick,
                    onPickPhotoClick = onPickPhotoClick,
                    onReattachPhoto = onReattachPhoto,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = onMicClick,
                    isAnyDialogOpen = isAnyDialogOpen
                )
            }
        }
    }
}
