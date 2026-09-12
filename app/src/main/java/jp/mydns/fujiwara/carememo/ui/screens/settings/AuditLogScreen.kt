package jp.mydns.fujiwara.carememo.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogScreenState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.viewmodel.AuditLogViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Screen：AuditLogScreen
 *
 * 【役割】
 * アプリ内でのユーザー操作履歴（監査ログ）を閲覧するための画面です。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // イベント購読
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                AuditLogViewEvent.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("AuditLogScreen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_log_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("AuditLogScreen_BackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // ソート切り替え
                    IconButton(
                        onClick = { viewModel.toggleSortOrder() },
                        modifier = Modifier.testTag("AuditLog_SortToggle")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "ソート切り替え",
                            tint = if (uiState.isAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // フィルタクリア
                    if (uiState.selectedFeature != null || uiState.selectedResult != null) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Rounded.FilterListOff, contentDescription = "フィルタ解除")
                        }
                    }
                },
                colors = appTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // フィルタセクション
            AuditLogFilterBar(
                availableFeatures = uiState.availableFeatures,
                selectedFeature = uiState.selectedFeature,
                onFeatureChange = { viewModel.setFeatureFilter(it) },
                availableResults = uiState.availableResults,
                selectedResult = uiState.selectedResult,
                onResultChange = { viewModel.setResultFilter(it) }
            )

            // ログリスト
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.screenState) {
                    is AuditLogScreenState.Loading -> {
                        LoadingScreen(modifier = Modifier.fillMaxSize().testTag("AuditLog_Loading"))
                    }
                    is AuditLogScreenState.Error -> {
                        ErrorState(
                            message = stringResource(R.string.common_error_load_failed),
                            onRetry = { /* viewModel.startLogsObservation() */ }
                        )
                    }
                    is AuditLogScreenState.Active -> {
                        if (uiState.filteredLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().testTag("AuditLog_EmptyState"), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (uiState.selectedFeature != null || uiState.selectedResult != null)
                                        stringResource(R.string.audit_log_empty_filtered_msg) else stringResource(R.string.audit_log_empty),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            AuditLogList(logs = uiState.filteredLogs)
                        }
                    }
                }
            }
        }
    }
}

/**
 * ログのフィルタバー
 */
@Composable
private fun AuditLogFilterBar(
    availableFeatures: List<String>,
    selectedFeature: String?,
    onFeatureChange: (String?) -> Unit,
    availableResults: List<String>,
    selectedResult: String?,
    onResultChange: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 機能フィルタ
        FilterChipDropdown(
            label = "機能",
            items = listOf("すべて") + availableFeatures,
            selectedItem = selectedFeature ?: "すべて",
            onItemSelected = { if (it == "すべて") onFeatureChange(null) else onFeatureChange(it) },
            modifier = Modifier.weight(1f).testTag("AuditLog_FeatureFilter")
        )
        // 結果フィルタ
        FilterChipDropdown(
            label = "結果",
            items = listOf("すべて") + availableResults,
            selectedItem = selectedResult ?: "すべて",
            onItemSelected = { if (it == "すべて") onResultChange(null) else onResultChange(it) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 簡易的なドロップダウン・フィルタチップ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipDropdown(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilterChip(
            selected = selectedItem != "すべて",
            onClick = { expanded = true },
            label = { Text(if (selectedItem == "すべて") label else selectedItem) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    modifier = Modifier.testTag("FeatureFilterItem_$item")
                )
            }
        }
    }
}

/**
 * ログの一覧表示
 */
@Composable
private fun AuditLogList(logs: List<AuditLog>) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize().testTag("AuditLog_LogList")) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                AuditLogItem(log)
            }
        }
        VerticalScrollIndicator(lazyListState = listState)
    }
}

/**
 * ログの各行アイテム
 */
@Composable
private fun AuditLogItem(log: AuditLog) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") }
    val timeStr = log.timestamp.atZone(ZoneId.systemDefault()).format(formatter)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("AuditLogItem_${log.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (log.resultType == "SUCCESS")
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = log.resultType,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.featureName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " - ${log.operation}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!log.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
