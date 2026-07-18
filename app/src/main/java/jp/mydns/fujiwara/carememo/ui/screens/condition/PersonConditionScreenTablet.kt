package jp.mydns.fujiwara.carememo.ui.screens.condition

/**
 * Screen : PersonConditionScreenTablet
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
    personId: Int,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conditionPhotoMap: Map<Int, Boolean>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    selectedId: Int,
    onSelectedIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onAddPhotoClick: () -> Unit,
    onNavigateToFullScreen: (Int, Int) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (Int, Int, PersonConditionUiState, (Int) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { onSelectedIdChange(0) }) {
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
                onNavigateToFullScreen = onNavigateToFullScreen,
                onMicClick = onMicClick
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun PersonConditionScreenTabletPreview() {
    CareMemoTheme {
        PersonConditionScreenTablet(
            personId = 1,
            currentPerson = Person(
                lastName = "山田",
                firstName = "太郎",
                lastNameFurigana = "ヤマダ",
                firstNameFurigana = "タロウ",
                birthday = Instant.now()
            ),
            isNameMaskingEnabled = false,
            personCategorySummary = null,
            records = listOf(
                ConditionAtVisit(
                    id = 1,
                    personId = 1,
                    title = "サンプルタイトル",
                    condition = "サンプルの所見内容です。",
                    author = "記録者A",
                    recordTime = Instant.now()
                )
            ),
            isLoading = false,
            searchQuery = "",
            onSearchQueryChange = {},
            conditionPhotoMap = emptyMap(),
            photos = emptyList(),
            isProcessing = false,
            isAnyDialogOpen = false,
            defaultRecorderName = "記録者",
            selectedId = -1,
            onSelectedIdChange = {},
            onBack = {},
            onNavigateToCategory = {},
            onAddPhotoClick = {},
            onNavigateToFullScreen = { _, _ -> },
            onShowPdfSettings = {},
            onDeleteRecord = {},
            onSaveRecord = { _, _, _, _ -> },
            onDeletePhoto = {},
            onMicClick = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
