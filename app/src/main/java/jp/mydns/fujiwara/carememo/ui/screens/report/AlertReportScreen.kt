package jp.mydns.fujiwara.carememo.ui.screens.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AlertItem
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import jp.mydns.fujiwara.carememo.logic.feature.AlertFilterType
import jp.mydns.fujiwara.carememo.logic.feature.AlertReportScreenState
import jp.mydns.fujiwara.carememo.logic.feature.AlertReportViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.viewmodel.AlertReportViewModel
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils

/**
 * Screen：AlertReportScreen
 *
 * 【役割】
 * 保存された健康データから抽出された「異常値」や「急激な変化（アラート）」を一覧表示する画面です。
 * 重大度に応じた視覚的な強調と、詳細画面へのスムーズな遷移を提供します。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertReportScreen(
    viewModel: AlertReportViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 画面遷移イベントの購読
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is AlertReportViewEvent.NavigateToDetail -> {
                    // カテゴリに応じた詳細画面へ遷移（Category 拡張関数を活用）
                    navController.navigate(event.category.toDestination(event.personId))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("AlertReportScreen"),
        topBar = {
            TopAppBar(
                title = { 
                    val title = stringResource(R.string.alert_report_title)
                    // 絞り込み中なら名前を出すことも検討できるが、まずはシンプルに
                    Text(title, fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("AlertReportScreen_BackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshReport() },
                        modifier = Modifier.testTag("AlertReportScreen_RefreshButton")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                },
                colors = appTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // フィルタバー
            AlertFilterBar(
                selectedType = uiState.filterType,
                onTypeChange = { viewModel.setFilterType(it) }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.screenState) {
                    is AlertReportScreenState.Loading -> {
                        LoadingScreen(modifier = Modifier.fillMaxSize().testTag("AlertReport_Loading"))
                    }
                    is AlertReportScreenState.Error -> {
                        ErrorState(
                            message = stringResource(R.string.common_error_load_failed),
                            onRetry = { viewModel.refreshReport() },
                            modifier = Modifier.testTag("AlertReport_Error")
                        )
                    }
                    is AlertReportScreenState.Active -> {
                        if (uiState.filteredAlerts.isEmpty()) {
                            EmptyState(
                                message = stringResource(R.string.alert_report_empty),
                                icon = Icons.Rounded.DoneAll,
                                modifier = Modifier.testTag("AlertReport_Empty")
                            )
                        } else {
                            AlertList(
                                alerts = uiState.filteredAlerts,
                                onItemClick = { viewModel.navigateToDetail(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertFilterBar(
    selectedType: AlertFilterType,
    onTypeChange: (AlertFilterType) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedType.ordinal,
        edgePadding = 16.dp,
        divider = {},
        containerColor = Color.Transparent,
        modifier = Modifier.testTag("AlertReport_FilterBar")
    ) {
        AlertFilterType.entries.forEach { type ->
            Tab(
                selected = selectedType == type,
                onClick = { onTypeChange(type) },
                text = {
                    Text(
                        text = when (type) {
                            AlertFilterType.ALL -> stringResource(R.string.alert_report_filter_all)
                            AlertFilterType.VITAL -> stringResource(R.string.alert_report_filter_vital)
                            AlertFilterType.WEIGHT -> stringResource(R.string.alert_report_filter_weight)
                            AlertFilterType.GLUCOSE -> stringResource(R.string.alert_report_filter_glucose)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.testTag("AlertFilter_${type.name}")
            )
        }
    }
}

@Composable
private fun AlertList(
    alerts: List<AlertItem>,
    onItemClick: (AlertItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("AlertReport_List"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // キーをさらにユニークにするため、インデックスや項目種別を厳密に組み合わせる
        items(
            items = alerts, 
            key = { alert -> 
                "${alert.id}_${alert.itemNameResId ?: 0}_${alert.messageResId ?: 0}_${alert.alertLevel.name}"
            }
        ) { alert ->
            AlertItemView(
                alert = alert,
                onClick = { onItemClick(alert) },
                // testTag も重複しないように構成
                modifier = Modifier.testTag("AlertItem_${alert.id}_${alert.itemNameResId ?: 0}")
            )
        }
    }
}

@Composable
private fun AlertItemView(
    alert: AlertItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (alert.alertLevel) {
        HealthAlertLevel.ALERT -> MaterialTheme.colorScheme.errorContainer
        HealthAlertLevel.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    
    val contentColor = when (alert.alertLevel) {
        HealthAlertLevel.ALERT -> MaterialTheme.colorScheme.onErrorContainer
        HealthAlertLevel.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.personName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = DateTimeUtils.formatPhotoCaption(alert.recordTime), // yyyy/MM/dd HH:mm
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (alert.alertLevel) {
                        HealthAlertLevel.ALERT -> Icons.Rounded.Error
                        HealthAlertLevel.WARNING -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (alert.alertLevel == HealthAlertLevel.ALERT) MaterialTheme.colorScheme.error 
                           else contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                val itemName = if (alert.itemNameResId != null) stringResource(alert.itemNameResId) else alert.itemName
                Text(
                    text = "${itemName}: ${alert.valueText}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val messageText = if (alert.messageResId != null) {
                stringResource(alert.messageResId, *alert.messageArgs.toTypedArray())
            } else {
                alert.message
            }
            Text(
                text = messageText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
