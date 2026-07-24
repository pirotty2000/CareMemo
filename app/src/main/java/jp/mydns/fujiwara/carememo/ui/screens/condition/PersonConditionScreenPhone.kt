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
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenPhone(
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
    onNavigateToFullScreen: (String, String) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (String, String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onMicClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
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
                            onClick = { if (selectedId.isNotEmpty()) onSelectedIdChange("") else onBack() },
                            modifier = Modifier.testTag("ConditionScreen_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onShowPdfSettings,
                            modifier = Modifier.testTag("ConditionScreen_PdfButton")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF出力")
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
            if (selectedId.isEmpty()) {
                FloatingActionButton(
                    onClick = { onSelectedIdChange("0") },
                    modifier = Modifier.testTag("ConditionScreen_AddButton")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新規追加")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading && records.isEmpty()) {
            LoadingScreen(modifier = Modifier.padding(padding))
        } else {
            Box(modifier = Modifier.padding(padding)) {
                PersonConditionScreenContent(
                    isExpanded = false,
                    personId = personId,
                    records = records,
                    isLoading = isLoading,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    selectedId = selectedId,
                    onSelectedIdChange = { onSelectedIdChange(it) },
                    conditionPhotoMap = conditionPhotoMap,
                    photos = photos,
                    isProcessing = isProcessing,
                    isAnyDialogOpen = isAnyDialogOpen,
                    defaultRecorderName = defaultRecorderName,
                    onDeleteRecord = onDeleteRecord,
                    onSaveRecord = onSaveRecord,
                    onDeletePhoto = onDeletePhoto,
                    onAddPhotoClick = onAddPhotoClick,
                    onNavigateToFullScreen = onNavigateToFullScreen,
                    onMicClick = onMicClick
                )
            }
        }
    }
}
