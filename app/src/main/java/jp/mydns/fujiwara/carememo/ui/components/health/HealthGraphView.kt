package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：HealthGraphView
 *
 * 【役割】：
 * 健康記録の各指標（血圧、血糖値等）をカテゴリごとにグラフ化して表示するコンテナコンポーネント。
 *
 * 【主な機能】：
 * ・カテゴリごとの複数グラフ（血圧なら3種、血糖値なら2種等）の自動レイアウト表示。
 * ・HealthChartHelper と連携した、現在のテーマ（ライト/ダーク）に合わせた動的な配色生成。
 * ・グラフごとの「数値の目安（ヘルプ）」ダイアログ表示機能。
 * ・グラフ拡大画面（GraphExpansionScreen）への遷移トリガーの提供。
 *
 * 【想定する利用場所】：
 * 健康記録のメインコンテンツ領域（PersonHealthScreenContent）、および拡大表示画面。
 *
 * 【このコンポーネントでは行わないこと】：
 * グラフ描画エンジン自体の実装（LineChart が担当）や、詳細データの数値リスト表示。
 *
 * 【公開composable】：
 * HealthGraphView
 */

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
import jp.mydns.fujiwara.carememo.ui.components.base.*

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
        AppDialog(
            onDismissRequest = { showHelpDialog = null },
            title = { Text(stringResource(R.string.main_menu_help)) },
            text = {
                AppDialogContent(text = showHelpDialog!!)
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_close),
                    onClick = { showHelpDialog = null }
                )
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
                            limits = config.limits,
                            minYConstraint = config.minYConstraint,
                            maxYConstraint = config.maxYConstraint,
                            showDecimal = config.showDecimal,
                            fixedMinX = globalMinX,
                            fixedMaxX = globalMaxX
                        )
                    } else {
                        Text(stringResource(R.string.p_detail_empty_records), modifier = Modifier.align(Alignment.Center))
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
