package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditSession
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionScreenState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import kotlinx.collections.immutable.toImmutableList
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
    val session = uiState.editSession
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
                                if (session.selectedConditionId != null) onAction(PersonConditionUiAction.CancelEdit) 
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
                        if (session.selectedConditionId == null) {
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
            if (session.selectedConditionId == null) {
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
        if (uiState.screenState is PersonConditionScreenState.Loading && uiState.records.isEmpty()) {
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

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun PersonConditionScreenPhonePreview(
    @PreviewParameter(PersonConditionPreviewParameterProvider::class) state: PersonConditionPreviewState
) {
    CareMemoTheme {
        PersonConditionScreenPhone(
            uiState = PersonConditionUiState(
                screenState = if (state.isLoading) PersonConditionScreenState.Loading else PersonConditionScreenState.Active,
                records = state.records.toImmutableList(),
                editSession = ConditionEditSession(
                    selectedConditionId = state.selectedRecordId
                )
            ),
            currentPerson = MockData.person,
            isNameMaskingEnabled = false,
            personCategorySummary = null,
            isAnyDialogOpen = false,
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
