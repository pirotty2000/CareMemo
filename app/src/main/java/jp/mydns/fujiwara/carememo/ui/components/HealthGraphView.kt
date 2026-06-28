package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*

@Composable
fun HealthGraphView(
    records: List<Any>,
    categoryType: Category,
    onExpandGraph: ((Int) -> Unit)? = null
) {
    var showHelpDialog by remember { mutableStateOf<String?>(null) }
    // 背景色の輝度が低い（0.5未満）場合にダークモードと判定する
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    // 全データを通じた共通のX軸（時間軸）の範囲を計算
    val (globalMinX, globalMaxX) = remember(records) {
        HealthChartHelper.calculateGlobalXRange(records)
    }

    val context = LocalContext.current

    if (showHelpDialog != null) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = null },
            title = { Text(stringResource(R.string.menu_help)) }, // "数値の目安" is usually help
            text = { Text(showHelpDialog!!) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    val graphCount = remember(categoryType) {
        HealthChartHelper.getGraphCount(categoryType)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(graphCount) { index ->
            val config = remember(categoryType, index, records, context, isDark) {
                HealthChartHelper.getChartConfig(context, categoryType, index, records, isDark)
            }

            if (config != null) {
                GraphTitleWithHelp(
                    title = config.title,
                    helpContent = config.helpContent,
                    onShowHelp = { showHelpDialog = it },
                    onExpand = onExpandGraph?.let { { it(index) } }
                )
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    if (config.dataList.any { it.points.isNotEmpty() }) {
                        LineChart(
                            dataList = config.dataList,
                            stepY = config.stepY,
                            ranges = config.ranges,
                            minYConstraint = config.minYConstraint,
                            maxYConstraint = config.maxYConstraint,
                            showDecimal = config.showDecimal,
                            fixedMinX = globalMinX,
                            fixedMaxX = globalMaxX
                        )
                    } else {
                        Text(stringResource(R.string.empty_records), modifier = Modifier.align(Alignment.Center))
                    }
                }
                if (index < graphCount - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun GraphTitleWithHelp(
    title: String,
    helpContent: String,
    onShowHelp: (String) -> Unit,
    onExpand: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (helpContent.isNotBlank()) {
            IconButton(onClick = { onShowHelp(helpContent) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "目安の表示", modifier = Modifier.size(18.dp), tint = Color.Gray)
            }
        }
        if (onExpand != null) {
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ZoomOutMap, contentDescription = "拡大表示", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
