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
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
    personId: String,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conditionPhotoMap: Map<String, Boolean>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    selectedId: String,
    onSelectedIdChange: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onNavigateToFullScreen: (String, String) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (String, String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit,
    orphanedPhotos: List<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    onMicClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = "所見記録",
                            modifier = Modifier.testTag("PersonHeader_Title")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("ConditionScreen_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(onClick = { onSelectedIdChange(jp.mydns.fujiwara.carememo.logic.feature.PersonConditionLogic.NEW_RECORD_ID) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                        }
                        IconButton(
                            onClick = onShowPdfSettings,
                            modifier = Modifier.testTag("ConditionScreen_PdfButton")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力")
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
                personId = personId,
                records = records,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedId = selectedId,
                onSelectedIdChange = { id -> 
                    onSelectedIdChange(id)
                },
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
