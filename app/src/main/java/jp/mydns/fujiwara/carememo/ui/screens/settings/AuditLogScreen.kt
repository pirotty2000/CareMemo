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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.mapping.toActionLabel
import jp.mydns.fujiwara.carememo.ui.mapping.toFeatureLabel
import jp.mydns.fujiwara.carememo.ui.mapping.toResultLabel
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.AuditLogViewModel

@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onBack: () -> Unit,
) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val selectedFeature by viewModel.selectedFeature.collectAsStateWithLifecycle()
    val selectedResult by viewModel.selectedResult.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscending.collectAsStateWithLifecycle()
    val availableFeatures by viewModel.availableFeatures.collectAsStateWithLifecycle()
    val availableResults by viewModel.availableResults.collectAsStateWithLifecycle()

    AuditLogScreenContent(
        auditLogs = auditLogs,
        selectedFeature = selectedFeature,
        selectedResult = selectedResult,
        isAscending = isAscending,
        availableFeatures = availableFeatures,
        availableResults = availableResults,
        onFeatureSelect = { viewModel.setFeatureFilter(it) },
        onResultSelect = { viewModel.setResultFilter(it) },
        onToggleSort = { viewModel.toggleSortOrder() },
        onClearFilters = { viewModel.clearFilters() },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreenContent(
    auditLogs: List<AuditLog>,
    selectedFeature: String?,
    selectedResult: String?,
    isAscending: Boolean,
    availableFeatures: List<String>,
    availableResults: List<String>,
    onFeatureSelect: (String?) -> Unit,
    onResultSelect: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val isFiltered = (selectedFeature != null) || (selectedResult != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("操作ログ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("AuditLog_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
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
                    onClear = onClearFilters
                )
            }

            if (auditLogs.isEmpty()) {
                EmptyState(
                    message = if (isFiltered) "条件に合うログはありません" else "ログはありません",
                    icon = Icons.Rounded.History,
                    description = if (isFiltered) "フィルター設定を変更してください" else "操作を行うとここに履歴が記録されます",
                    modifier = Modifier.weight(1f).testTag("AuditLog_EmptyState"),
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().testTag("AuditLog_List"),
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
    availableFeatures: List<String>,
    availableResults: List<String>,
    onFeatureSelect: (String?) -> Unit,
    onResultSelect: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onClear: () -> Unit,
) {
    var showFeatureMenu by remember { mutableStateOf(value = false) }
    var showResultMenu by remember { mutableStateOf(value = false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
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
                    label = { Text(selectedResult?.toResultLabel ?: "結果: 全て") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (selectedResult != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_ResultFilter")
                )
                DropdownMenu(expanded = showResultMenu, onDismissRequest = { showResultMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("全て") },
                        onClick = {
                            onResultSelect(null)
                            showResultMenu = false
                        },
                    )
                    availableResults.forEach { result ->
                        DropdownMenuItem(
                            text = { Text(result.toResultLabel) },
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
                    label = { Text(selectedFeature?.toFeatureLabel ?: "機能: 全て") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (selectedFeature != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_FeatureFilter")
                )
                DropdownMenu(expanded = showFeatureMenu, onDismissRequest = { showFeatureMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("全て") },
                        onClick = {
                            onFeatureSelect(null)
                            showFeatureMenu = false
                        },
                    )
                    availableFeatures.forEach { feature ->
                        DropdownMenuItem(
                            text = { Text(feature.toFeatureLabel) },
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
                    contentDescription = if (isAscending) "古い順" else "新しい順",
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
                        contentDescription = "フィルター解除",
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
            auditLogs = listOf(
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
            selectedFeature = null,
            selectedResult = null,
            isAscending = false,
            availableFeatures = listOf("PersonList", "PersonHealth", "Settings"),
            availableResults = listOf("SUCCESS", "DB_ERROR", "OTHER_ERROR"),
            onFeatureSelect = {},
            onResultSelect = {},
            onToggleSort = {},
            onClearFilters = {},
            onBack = {}
        )
    }
}

@Composable
fun AuditLogItem(log: AuditLog, modifier: Modifier = Modifier) {
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
                    text = log.actionType.toActionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (log.actionType) {
                        "INSERT" -> Color(0xFF4CAF50)
                        "UPDATE" -> Color(0xFF2196F3)
                        "DELETE" -> Color(0xFFF44336)
                        "LOGICAL_DELETE" -> Color(0xFFFF9800)
                        "RESTORE" -> Color(0xFF9C27B0)
                        "ERROR" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${log.featureName.toFeatureLabel} > ${log.operation}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when (log.resultType) {
                        "SUCCESS" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        "UNKNOWN" -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        when (log.resultType) {
                            "SUCCESS" -> Color(0xFF4CAF50)
                            "UNKNOWN" -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                ) {
                    Text(
                        text = log.resultType.toResultLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = when (log.resultType) {
                            "SUCCESS" -> Color(0xFF388E3C)
                            "UNKNOWN" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        }
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
