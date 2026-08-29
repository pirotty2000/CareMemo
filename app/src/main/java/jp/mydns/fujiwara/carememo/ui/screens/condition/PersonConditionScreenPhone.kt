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
    onAction: (PersonConditionUiAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
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
                            onClick = { 
                                if (uiState.selectedConditionId != null) onAction(PersonConditionUiAction.CancelEdit) 
                                else onAction(PersonConditionUiAction.Back) 
                            },
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
                                onClick = { onAction(PersonConditionUiAction.ShowPdfSettings) },
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
                    onCategoryClick = { onAction(PersonConditionUiAction.NavigateToCategory(it)) },
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedConditionId == null) {
                FloatingActionButton(
                    onClick = { onAction(PersonConditionUiAction.SelectedIdChanged(AppSpecifications.Id.NEW_RECORD_ID)) },
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
                    onAction = onAction,
                    isAnyDialogOpen = isAnyDialogOpen
                )
            }
        }
    }
}
