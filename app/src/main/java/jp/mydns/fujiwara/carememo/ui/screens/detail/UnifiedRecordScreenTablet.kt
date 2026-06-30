package jp.mydns.fujiwara.carememo.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecordScreenTablet(
    healthViewModel: HealthRecordViewModel,
    personId: Int,
    currentCategory: Category,
    records: List<Any>,
    conditionPhotoMap: Map<Int, Boolean>,
    currentPerson: Person?,
    personCategorySummary: jp.mydns.fujiwara.carememo.data.PersonCategorySummary?,
    isNameMaskingEnabled: Boolean,
    selectedConditionId: Int,
    onSelectedConditionIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToGraphExpansion: (Int, Category, Int) -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
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
                            defaultTitle = stringResource(R.string.app_name)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { onSelectedConditionIdChange(0) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                        }
                        IconButton(onClick = onShowPdfSettings) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = stringResource(R.string.pdf_export)
                            )
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = currentCategory,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory
                )
            }
        }
    ) { paddingValues ->
        var recordToDelete by remember { mutableStateOf<HistoryRecord?>(null) }
        if (recordToDelete != null) {
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text(stringResource(R.string.delete_data_title)) },
                text = { Text(stringResource(R.string.delete_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            recordToDelete?.let {
                                if (selectedConditionId == it.id) onSelectedConditionIdChange(-1)
                                healthViewModel.deleteRecord(it)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (records.isEmpty() && selectedConditionId == -1) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(R.string.empty_records),
                        description = stringResource(R.string.empty_records_description),
                        icon = Icons.Outlined.Description
                    )
                }
            } else {
                UnifiedRecordContent(
                    isExpanded = true,
                    personId = personId,
                    records = records,
                    currentCategory = currentCategory,
                    conditionPhotoMap = conditionPhotoMap,
                    preferredShowHistory = true,
                    onPreferredShowHistoryChange = {},
                    selectedRecordId = selectedConditionId,
                    onSelectedRecordIdChange = onSelectedConditionIdChange,
                    onItemClick = { record -> onSelectedConditionIdChange(record.id) },
                    onDeleteSwipe = { record -> recordToDelete = record },
                    onExpandGraph = { index ->
                        onNavigateToGraphExpansion(personId, currentCategory, index)
                    },
                    healthViewModel = healthViewModel,
                    isAnyDialogOpen = recordToDelete != null
                )
            }
        }
    }
}
