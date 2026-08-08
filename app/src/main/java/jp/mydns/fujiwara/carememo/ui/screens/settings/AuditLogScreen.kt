package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.mapping.toActionLabelRes
import jp.mydns.fujiwara.carememo.ui.mapping.toFeatureLabelRes
import jp.mydns.fujiwara.carememo.ui.mapping.toResultLabelRes
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.ui.theme.getAuditActionColor
import jp.mydns.fujiwara.carememo.ui.theme.getAuditResultMainColor
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.AuditLogViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is AuditLogViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    AuditLogScreenContent(
        auditLogs = uiState.filteredLogs, // filteredLogs を使用するように変更
        isLoading = uiState.isLoading,
        selectedFeature = uiState.selectedFeature,
        selectedResult = uiState.selectedResult,
        isAscending = uiState.isAscending,
        availableFeatures = uiState.availableFeatures,
        availableResults = uiState.availableResults,
        onFeatureSelect = { viewModel.setFeatureFilter(it) },
        onResultSelect = { viewModel.setResultFilter(it) },
        onToggleSort = { viewModel.toggleSortOrder() },
        onClearFilters = { viewModel.clearFilters() },
        onBack = { viewModel.navigateBack() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreenContent(
    auditLogs: ImmutableList<AuditLog>,
    isLoading: Boolean,
    selectedFeature: String?,
    selectedResult: String?,
    isAscending: Boolean,
    availableFeatures: ImmutableList<String>,
    availableResults: ImmutableList<String>,
    onFeatureSelect: (String?) -> Unit,
    onResultSelect: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val isFiltered = (selectedFeature != null) || (selectedResult != null)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_log_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("AuditLogScreen_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors(),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // フィルター・ソートバー
            if (availableFeatures.isNotEmpty() || availableResults.isNotEmpty() || isFiltered) {
                AuditLogFilterBar(
                    selectedFeature = selectedFeature,
                    selectedResult = selectedResult,
                    isAscending = isAscending,
                    availableFeatures = availableFeatures,
                    availableResults = availableResults,
                    onFeatureSelect = onFeatureSelect,
                    onResultSelect = onResultSelect,
                    onToggleSort = onToggleSort,
                    onClear = onClearFilters,
                    modifier = Modifier.testTag("AuditLog_FilterChips")
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("AuditLog_Loading"))
                }
            } else if (auditLogs.isEmpty()) {
                EmptyState(
                    message = if (isFiltered) stringResource(R.string.audit_log_empty_filtered_msg) else stringResource(R.string.audit_log_empty),
                    icon = Icons.Rounded.History,
                    description = if (isFiltered) stringResource(R.string.audit_log_empty_filtered_desc) else stringResource(R.string.audit_log_empty_desc),
                    modifier = Modifier.weight(1f).testTag("AuditLog_EmptyState"),
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().testTag("AuditLog_LogList"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(auditLogs) { log ->
                            AuditLogItem(log, modifier = Modifier.testTag("AuditLogItem_${log.id}"))
                        }
                    }

                    VerticalScrollIndicator(lazyListState = lazyListState)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogFilterBar(
    selectedFeature: String?,
    selectedResult: String?,
    isAscending: Boolean,
    availableFeatures: ImmutableList<String>,
    availableResults: ImmutableList<String>,
    onFeatureSelect: (String?) -> Unit,
    onResultSelect: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFeatureMenu by remember { mutableStateOf(value = false) }
    var showResultMenu by remember { mutableStateOf(value = false) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 結果絞り込み
        item {
            Box {
                FilterChip(
                    selected = selectedResult != null,
                    onClick = { showResultMenu = true },
                    label = { Text(selectedResult?.let { stringResource(it.toResultLabelRes) } ?: "結果: 全て") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (selectedResult != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_ResultFilter")
                )
                DropdownMenu(expanded = showResultMenu, onDismissRequest = { showResultMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audit_log_filter_all)) },
                        onClick = {
                            onResultSelect(null)
                            showResultMenu = false
                        },
                    )
                    availableResults.forEach { result ->
                        DropdownMenuItem(
                            text = { Text(stringResource(result.toResultLabelRes)) },
                            onClick = {
                                onResultSelect(result)
                                showResultMenu = false
                            },
                            modifier = Modifier.testTag("ResultFilterItem_$result")
                        )
                    }
                }
            }
        }

        // 機能絞り込み
        item {
            Box {
                FilterChip(
                    selected = selectedFeature != null,
                    onClick = { showFeatureMenu = true },
                    label = { Text(selectedFeature?.let { stringResource(it.toFeatureLabelRes) } ?: "機能: 全て") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (selectedFeature != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_FeatureFilter")
                )
                DropdownMenu(expanded = showFeatureMenu, onDismissRequest = { showFeatureMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audit_log_filter_all)) },
                        onClick = {
                            onFeatureSelect(null)
                            showFeatureMenu = false
                        },
                    )
                    availableFeatures.forEach { feature ->
                        DropdownMenuItem(
                            text = { Text(stringResource(feature.toFeatureLabelRes)) },
                            onClick = {
                                onFeatureSelect(feature)
                                showFeatureMenu = false
                            },
                            modifier = Modifier.testTag("FeatureFilterItem_$feature")
                        )
                    }
                }
            }
        }

        // 並び替えトグル
        item {
            IconButton(
                onClick = onToggleSort,
                modifier = Modifier.testTag("AuditLog_SortToggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = if (isAscending) stringResource(R.string.audit_log_sort_asc) else stringResource(R.string.audit_log_sort_desc),
                    tint = if (isAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // クリアボタン
        if ((selectedFeature != null) || (selectedResult != null)) {
            item {
                IconButton(onClick = onClear, modifier = Modifier.testTag("AuditLog_FilterClear")) {
                    Icon(
                        Icons.Rounded.ClearAll,
                        contentDescription = stringResource(R.string.audit_log_filter_clear),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuditLogScreenPreview() {
    CareMemoTheme {
        AuditLogScreenContent(
            auditLogs = persistentListOf(
                AuditLog(
                    id = 1,
                    timestamp = java.time.Instant.now(),
                    featureName = "PersonList",
                    operation = "addPerson",
                    tableName = "person_db",
                    actionType = "INSERT",
                    affectedId = "1",
                    resultType = "SUCCESS"
                ),
                AuditLog(
                    id = 2,
                    timestamp = java.time.Instant.now().minusSeconds(3600),
                    featureName = "PersonHealth",
                    operation = "saveRecord",
                    tableName = "health_db",
                    actionType = "UPDATE",
                    affectedId = "10",
                    resultType = "DB_ERROR",
                    details = "android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed..."
                ),
                AuditLog(
                    id = 3,
                    timestamp = java.time.Instant.now().minusSeconds(7200),
                    featureName = "Settings",
                    operation = "exportData",
                    tableName = "all_db",
                    actionType = "UPDATE",
                    affectedId = "0",
                    resultType = "OTHER_ERROR",
                    details = "java.io.IOException: Permission denied"
                )
            ),
            isLoading = false,
            selectedFeature = null,
            selectedResult = null,
            isAscending = false,
            availableFeatures = persistentListOf("PersonList", "PersonHealth", "Settings"),
            availableResults = persistentListOf("SUCCESS", "DB_ERROR", "OTHER_ERROR"),
            onFeatureSelect = {},
            onResultSelect = {},
            onToggleSort = {},
            onClearFilters = {},
            onBack = {}
        )
    }
}

@Composable
fun AuditLogItem(
    log: AuditLog,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = DateTimeUtils.formatRecordTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(log.actionType.toActionLabelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = getAuditActionColor(log.actionType),
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val featureLabelRes = log.featureName.toFeatureLabelRes
                val featureText = if (featureLabelRes != 0) stringResource(featureLabelRes) else log.featureName
                Text(
                    text = "$featureText > ${log.operation}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                val resultColor = getAuditResultMainColor(log.resultType)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = resultColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, resultColor)
                ) {
                    Text(
                        text = stringResource(log.resultType.toResultLabelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = resultColor
                    )
                }
            }
            
            Text(
                text = "Table: ${log.tableName} | ID: ${log.affectedId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            log.details?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
