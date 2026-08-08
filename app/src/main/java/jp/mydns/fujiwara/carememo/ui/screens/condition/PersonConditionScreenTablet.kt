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
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    records: ImmutableList<ConditionAtVisit>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conditionPhotoMap: Map<String, Boolean>,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    modifier: Modifier = Modifier,
    selectedId: String?,
    onSelectedIdChange: (String?) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onNavigateToFullScreen: (String, String) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit,
    orphanedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    onMicClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = modifier,
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
                records = records,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedId = selectedId,
                onSelectedIdChange = onSelectedIdChange,
                conditionPhotoMap = conditionPhotoMap,
                photos = photos,
                isProcessing = isProcessing,
                isAnyDialogOpen = isAnyDialogOpen,
                defaultRecorderName = defaultRecorderName,
                onDeleteRecord = onDeleteRecord,
                onSaveRecord = onSaveRecord,
                onDeletePhoto = onDeletePhoto,
                onAddPhotoClick = onAddPhotoClick,
                onPickPhotoClick = onPickPhotoClick,
                onReattachPhoto = onReattachPhoto,
                orphanedPhotos = orphanedPhotos,
                onNavigateToFullScreen = onNavigateToFullScreen,
                onMicClick = onMicClick
            )
        }
    }
}
