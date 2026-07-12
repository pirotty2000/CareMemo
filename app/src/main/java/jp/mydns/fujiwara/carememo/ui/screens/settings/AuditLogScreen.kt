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
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    val selectedFeature by viewModel.selectedFeature.collectAsStateWithLifecycle()
    val availableTables by viewModel.availableTables.collectAsStateWithLifecycle()
    val availableFeatures by viewModel.availableFeatures.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val isFiltered = (selectedTable != null) || (selectedFeature != null)

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
            // フィルターバー
            if (availableTables.isNotEmpty() || availableFeatures.isNotEmpty() || isFiltered) {
                AuditLogFilterBar(
                    selectedTable = selectedTable,
                    selectedFeature = selectedFeature,
                    availableTables = availableTables,
                    availableFeatures = availableFeatures,
                    onTableSelect = { viewModel.setTableFilter(it) },
                    onFeatureSelect = { viewModel.setFeatureFilter(it) },
                ) {
                    viewModel.clearFilters()
                }
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
    selectedTable: String?,
    selectedFeature: String?,
    availableTables: List<String>,
    availableFeatures: List<String>,
    onTableSelect: (String?) -> Unit,
    onFeatureSelect: (String?) -> Unit,
    onClear: () -> Unit,
) {
    var showTableMenu by remember { mutableStateOf(value = false) }
    var showFeatureMenu by remember { mutableStateOf(value = false) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // テーブル絞り込み
        item {
            Box {
                FilterChip(
                    selected = selectedTable != null,
                    onClick = { showTableMenu = true },
                    label = { Text(selectedTable ?: "テーブル: 全て") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (selectedTable != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_TableFilter")
                )
                DropdownMenu(expanded = showTableMenu, onDismissRequest = { showTableMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("全て") },
                        onClick = {
                            onTableSelect(null)
                            showTableMenu = false
                        },
                    )
                    availableTables.forEach { table ->
                        DropdownMenuItem(
                            text = { Text(table) },
                            onClick = {
                                onTableSelect(table)
                                showTableMenu = false
                            },
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
                    label = { Text(selectedFeature ?: "機能: 全て") },
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
                            text = { Text(feature) },
                            onClick = {
                                onFeatureSelect(feature)
                                showFeatureMenu = false
                            },
                        )
                    }
                }
            }
        }

        // クリアボタン
        if ((selectedTable != null) || (selectedFeature != null)) {
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
                    text = log.actionType,
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
            
            Text(
                text = "${log.featureName} > ${log.operation}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            
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
