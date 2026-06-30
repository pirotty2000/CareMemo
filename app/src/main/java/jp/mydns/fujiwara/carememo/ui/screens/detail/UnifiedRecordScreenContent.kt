package jp.mydns.fujiwara.carememo.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.HealthGraphView
import jp.mydns.fujiwara.carememo.ui.components.HealthRecordDetailPane
import jp.mydns.fujiwara.carememo.ui.components.UnifiedHistoryList
import jp.mydns.fujiwara.carememo.ui.components.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.viewmodel.HealthRecordViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel

@Composable
fun UnifiedRecordContent(
    isExpanded: Boolean,
    personId: Int,
    records: List<Any>,
    currentCategory: Category,
    conditionPhotoMap: Map<Int, Boolean>,
    preferredShowHistory: Boolean,
    onPreferredShowHistoryChange: (Boolean) -> Unit,
    selectedRecordId: Int,
    onSelectedRecordIdChange: (Int) -> Unit,
    onItemClick: (HistoryRecord) -> Unit,
    onDeleteSwipe: (HistoryRecord) -> Unit,
    onExpandGraph: (Int) -> Unit,
    viewModel: PersonDetailViewModel,
    healthViewModel: HealthRecordViewModel,
    isAnyDialogOpen: Boolean
) {
    if (isExpanded) {
        // --- タブレット・横向き: 2カラムレイアウト ---
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左側: 履歴リスト (比率 1)
            Box(modifier = Modifier.weight(1f)) {
                UnifiedHistoryList(
                    records = records.filterIsInstance<HistoryRecord>(),
                    category = currentCategory,
                    conditionPhotoMap = conditionPhotoMap,
                    selectedRecordId = selectedRecordId,
                    onItemClick = { record -> onSelectedRecordIdChange(record.id) },
                    onDeleteSwipe = onDeleteSwipe,
                    isAnyDialogOpen = isAnyDialogOpen
                )
            }
            // 右側: グラフ または 詳細入力 (比率 1.5)
            Box(modifier = Modifier.weight(1.5f)) {
                if (selectedRecordId != -1) {
                    HealthRecordDetailPane(
                        viewModel = viewModel,
                        healthViewModel = healthViewModel,
                        personId = personId,
                        category = currentCategory,
                        recordId = selectedRecordId,
                        onCancel = { onSelectedRecordIdChange(-1) },
                        onSaveSuccess = { onSelectedRecordIdChange(-1) }
                    )
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp)
                        ) {
                            HealthGraphView(
                                records = records,
                                categoryType = currentCategory,
                                onExpandGraph = onExpandGraph
                            )
                        }
                        if (scrollState.maxValue > 0) {
                            VerticalScrollIndicator(scrollState)
                        }
                    }
                }
            }
        }
    } else {
        // --- スマホ: 1カラム・切り替えレイアウト ---
        if (selectedRecordId != -1) {
            BackHandler { onSelectedRecordIdChange(-1) }
            HealthRecordDetailPane(
                viewModel = viewModel,
                healthViewModel = healthViewModel,
                personId = personId,
                category = currentCategory,
                recordId = selectedRecordId,
                onCancel = { onSelectedRecordIdChange(-1) },
                onSaveSuccess = { onSelectedRecordIdChange(-1) }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 表示切り替えボタン
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_history)) }
                    SegmentedButton(
                        selected = !preferredShowHistory,
                        onClick = { onPreferredShowHistoryChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Text(stringResource(R.string.tab_graph)) }
                }

                // コンテンツ表示
                Box(modifier = Modifier.weight(1f)) {
                    if (preferredShowHistory) {
                        UnifiedHistoryList(
                            records = records.filterIsInstance<HistoryRecord>(),
                            category = currentCategory,
                            conditionPhotoMap = conditionPhotoMap,
                            onItemClick = onItemClick,
                            onDeleteSwipe = onDeleteSwipe,
                            isAnyDialogOpen = isAnyDialogOpen
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 16.dp)
                            ) {
                                HealthGraphView(
                                    records = records,
                                    categoryType = currentCategory,
                                    onExpandGraph = onExpandGraph
                                )
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                            if (scrollState.maxValue > 0) {
                                VerticalScrollIndicator(scrollState)
                            }
                        }
                    }
                }
            }
        }
    }
}
