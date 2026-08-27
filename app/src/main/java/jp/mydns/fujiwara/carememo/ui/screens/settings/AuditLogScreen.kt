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
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
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
import kotlinx.collections.immutable.persistentListOf

/**
 * UI Action：監査ログ画面におけるユーザー操作の集約定義
 */
sealed interface AuditLogUiAction {
    data class SetFeatureFilter(val feature: String?) : AuditLogUiAction
    data class SetResultFilter(val result: String?) : AuditLogUiAction
    data object ToggleSortOrder : AuditLogUiAction
    data object ClearFilters : AuditLogUiAction
    data object Back : AuditLogUiAction
}

/**
 * Screen：AuditLogScreen
 *
 * 【役割】
 * アプリケーション内で発生した重要な操作やエラー（監査ログ：SCR-S-002）を一覧参照するための画面です。
 * 開発者モードにおいて、データの不整合や操作履歴のデバッグ・追跡を支援します。
 *
 * 【主な機能】
 * ・一覧表示：`AuditLogItem` による操作日時、機能名、アクション種別、結果のリスト表示。
 * ・フィルタリング：機能（PersonList, Health 等）や結果（SUCCESS, DB_ERROR 等）による動的な絞り込み。
 * ・ソート機能：タイムスタンプに基づく昇順・降順の切り替え。
 * ・詳細確認：発生した例外メッセージや影響を受けたレコード ID の詳細閲覧。
 *
 * 【全体像：監査ログ構成（Audit Log Layout）】
 *
 * ■ AuditLogScreen (★本コンポーネント：制御層)
 * │
 * └─ [1] AuditLogScreenContent (表示層)
 *      ├─ AuditLogFilterBar (フィルタ・ソート操作バー)
 *      │    ├─ FilterChip (結果・機能別のドロップダウン)
 *      │    └─ IconButton (昇順/降順トグル、フィルタクリア)
 *      └─ LazyColumn (ログリスト)
 *           └─ [2] AuditLogItem (ログ項目：ui/mapping と連携した配色・ラベル表示)
 */
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

    // アクションハンドラ
    val handleAction: (AuditLogUiAction) -> Unit = remember(viewModel) {
        { action ->
            when (action) {
                is AuditLogUiAction.SetFeatureFilter -> viewModel.setFeatureFilter(action.feature)
                is AuditLogUiAction.SetResultFilter -> viewModel.setResultFilter(action.result)
                AuditLogUiAction.ToggleSortOrder -> viewModel.toggleSortOrder()
                AuditLogUiAction.ClearFilters -> viewModel.clearFilters()
                AuditLogUiAction.Back -> viewModel.navigateBack()
            }
        }
    }

    AuditLogScreenContent(
        uiState = uiState,
        onAction = handleAction,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreenContent(
    uiState: AuditLogUiState,
    onAction: (AuditLogUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val isFiltered = (uiState.selectedFeature != null) || (uiState.selectedResult != null)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_log_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(AuditLogUiAction.Back) }, modifier = Modifier.testTag("AuditLogScreen_BackButton")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors(),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // フィルター・ソートバー
            if (uiState.availableFeatures.isNotEmpty() || uiState.availableResults.isNotEmpty() || isFiltered) {
                AuditLogFilterBar(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier.testTag("AuditLog_FilterChips")
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("AuditLog_Loading"))
                }
            } else if (uiState.filteredLogs.isEmpty()) {
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
                        items(uiState.filteredLogs) { log ->
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
    uiState: AuditLogUiState,
    onAction: (AuditLogUiAction) -> Unit,
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
                    selected = uiState.selectedResult != null,
                    onClick = { showResultMenu = true },
                    label = { 
                        val label = uiState.selectedResult?.let {
                            val resId = it.toResultLabelRes
                            if (resId != 0) stringResource(resId) else it
                        } ?: stringResource(R.string.audit_log_filter_result_prefix, stringResource(R.string.audit_log_filter_all))
                        Text(label)
                    },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (uiState.selectedResult != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_ResultFilter")
                )
                DropdownMenu(expanded = showResultMenu, onDismissRequest = { showResultMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audit_log_filter_all)) },
                        onClick = {
                            onAction(AuditLogUiAction.SetResultFilter(null))
                            showResultMenu = false
                        },
                    )
                    uiState.availableResults.forEach { result ->
                        DropdownMenuItem(
                            text = {
                                val resId = result.toResultLabelRes
                                val label = if (resId != 0) stringResource(resId) else result
                                Text(label)
                            },
                            onClick = {
                                onAction(AuditLogUiAction.SetResultFilter(result))
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
                    selected = uiState.selectedFeature != null,
                    onClick = { showFeatureMenu = true },
                    label = { 
                        val label = uiState.selectedFeature?.let {
                            val resId = it.toFeatureLabelRes
                            if (resId != 0) stringResource(resId) else it
                        } ?: stringResource(R.string.audit_log_filter_feature_prefix, stringResource(R.string.audit_log_filter_all))
                        Text(label)
                    },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    leadingIcon = if (uiState.selectedFeature != null) {
                        { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.testTag("AuditLog_FeatureFilter")
                )
                DropdownMenu(expanded = showFeatureMenu, onDismissRequest = { showFeatureMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audit_log_filter_all)) },
                        onClick = {
                            onAction(AuditLogUiAction.SetFeatureFilter(null))
                            showFeatureMenu = false
                        },
                    )
                    uiState.availableFeatures.forEach { feature ->
                        DropdownMenuItem(
                            text = {
                                val resId = feature.toFeatureLabelRes
                                val label = if (resId != 0) stringResource(resId) else feature
                                Text(label)
                            },
                            onClick = {
                                onAction(AuditLogUiAction.SetFeatureFilter(feature))
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
                onClick = { onAction(AuditLogUiAction.ToggleSortOrder) },
                modifier = Modifier.testTag("AuditLog_SortToggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = if (uiState.isAscending) stringResource(R.string.audit_log_sort_asc) else stringResource(R.string.audit_log_sort_desc),
                    tint = if (uiState.isAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // クリアボタン
        if ((uiState.selectedFeature != null) || (uiState.selectedResult != null)) {
            item {
                IconButton(onClick = { onAction(AuditLogUiAction.ClearFilters) }, modifier = Modifier.testTag("AuditLog_FilterClear")) {
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
    val mockLogs = persistentListOf(
        AuditLog(
            id = 1,
            timestamp = java.time.Instant.now(),
            featureName = "PersonList",
            operation = "addPerson",
            tableName = "person_db",
            actionType = "INSERT",
            affectedId = "1",
            resultType = "SUCCESS"
        )
    )
    CareMemoTheme {
        AuditLogScreenContent(
            uiState = AuditLogUiState(
                auditLogs = mockLogs,
                filteredLogs = mockLogs,
                isLoading = false,
                availableFeatures = persistentListOf("PersonList", "PersonHealth", "Settings"),
                availableResults = persistentListOf("SUCCESS", "DB_ERROR", "OTHER_ERROR")
            ),
            onAction = {}
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
                val actionResId = log.actionType.toActionLabelRes
                Text(
                    text = if (actionResId != 0) stringResource(actionResId) else log.actionType,
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
                    val resultLabelRes = log.resultType.toResultLabelRes
                    Text(
                        text = if (resultLabelRes != 0) stringResource(resultLabelRes) else log.resultType,
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
