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
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
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
                            onClick = onBack,
                            modifier = Modifier.testTag("ConditionScreen_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(onClick = { onSelectedIdChange(AppSpecifications.Id.NEW_RECORD_ID) }) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_create_new))
                        }
                        IconButton(
                            onClick = onShowPdfSettings,
                            modifier = Modifier.testTag("ConditionScreen_PdfButton")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = stringResource(R.string.common_pdf_export))
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = Category.CONDITION_AT_VISIT,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory,
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
