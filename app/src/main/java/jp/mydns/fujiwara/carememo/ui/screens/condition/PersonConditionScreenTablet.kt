package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors

/**
 * Screen：PersonConditionScreenTablet
 *
 * 【役割】
 * タブレット等の広い画面（WindowWidthSizeClass.Expanded）向けに最適化された所見記録画面です。
 *
 * 【主な機能】
 * ・2ペイン構成：左側に履歴リスト、右側に詳細・編集パネルを常時並列表示し、大画面を有効活用。
 * ・ナビゲーション統合：TopAppBar へのタイトル、戻るボタン、新規追加ボタン、および PDF 出力ボタンの配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
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
        modifier = modifier.testTag("ConditionScreen_TabletContent"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = stringResource(R.string.condition_title),
                            modifier = Modifier.testTag("PersonHeader_Title")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.Back) },
                            modifier = Modifier.testTag("ConditionScreen_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(onClick = { onAction(PersonConditionUiAction.SelectedIdChanged(AppSpecifications.Id.NEW_RECORD_ID)) }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_create_new))
                        }
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.ShowPdfSettings) },
                            modifier = Modifier.testTag("ConditionScreen_PdfButton")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = stringResource(R.string.common_pdf_export))
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = Category.CONDITION_AT_VISIT,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { onAction(PersonConditionUiAction.NavigateToCategory(it)) },
                    modifier = Modifier.testTag("CategorySelectorBar")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PersonConditionScreenContent(
                isExpanded = true,
                uiState = uiState,
                onAction = onAction,
                isAnyDialogOpen = isAnyDialogOpen
            )
        }
    }
}
