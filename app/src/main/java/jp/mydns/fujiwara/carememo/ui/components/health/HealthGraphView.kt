package jp.mydns.fujiwara.carememo.ui.components.health

/**
 * Component：HealthGraphView
 *
 * 【役割】
 * 健康記録の各指標（血圧、血糖値、BMI等）をカテゴリごとにグラフ化して一括表示するコンテナコンポーネントです。
 *
 * 【主な機能】
 * ・カテゴリに応じた複数グラフの自動レイアウト（血圧カテゴリなら血圧・脈拍・体温等）。
 * ・HealthChartHelper と連携し、現在のテーマ（ライト/ダーク）に合わせた最適な配色でのグラフ生成。
 * ・各指標の「数値の目安（ヘルプ）」をダイアログ形式で表示する機能。
 * ・グラフ拡大画面（GraphExpansionScreen）への遷移トリガーの提供。
 * ・データが空の場合のプレースホルダー表示。
 *
 * 【想定する利用場所】
 * ・PersonHealthScreenContent（健康記録履歴画面のグラフエリア）
 *
 * 【このコンポーネントでは行わないこと】
 * ・グラフ描画の低レベル実装（LineChart コンポーネントが担当）。
 * ・データのフィルタリングや集計（ViewModel および HealthLogic が担当）。
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
import kotlinx.collections.immutable.ImmutableList

/**
 * 健康記録グラフ表示コンポーネント
 *
 * @param records 表示対象の健康記録データのリスト（Any型で受け取り、内部でキャストして使用）
 * @param categoryType 表示する健康カテゴリ（Category）
 * @param onExpandGraph グラフの拡大アイコンが押された際のコールバック。引数にはグラフのインデックスが渡されます。
 */
@Composable
fun HealthGraphView(
    records: ImmutableList<Any>,
    categoryType: Category,
    onExpandGraph: ((Int) -> Unit)? = null
) {
    var showHelpDialog by remember { mutableStateOf<String?>(null) }
    
    // 背景色の輝度が低い（0.5未満）場合にダークモードと判定し、グラフの配色を調整する
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    // 全データを通じた共通のX軸（時間軸）の範囲を計算し、上下に並ぶグラフの横軸を同期させる
    val (globalMinX, globalMaxX) = remember(records) {
        HealthChartHelper.calculateGlobalXRange(records)
    }

    val context = LocalContext.current

    // 数値の目安（ヘルプ）ダイアログの表示
    if (showHelpDialog != null) {
        AppDialog(
            onDismissRequest = { showHelpDialog = null },
            title = { Text(stringResource(R.string.main_menu_help)) },
            text = {
                AppDialogContent(text = showHelpDialog!!)
            },
            confirmButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_close),
                    onClick = { showHelpDialog = null }
                )
            }
        )
    }

    // カテゴリに応じたグラフの数を取得（血圧なら4つなど）
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
            // 各グラフの設定（タイトル、データ、範囲、補助線など）を生成
            val config = remember(categoryType, index, records, context, isDark) {
                HealthChartHelper.getChartConfig(context, categoryType, index, records, isDark)
            }

            if (config != null) {
                // タイトルと操作ボタンの表示
                GraphTitleWithHelp(
                    title = config.title,
                    helpContent = config.helpContent,
                    onShowHelp = { showHelpDialog = it },
                    onExpand = onExpandGraph?.let { { it(index) } }
                )
                
                Box(modifier = Modifier.height(180.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                    // データが存在する場合のみグラフを描画
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
                        // データがない場合のプレースホルダー
                        Text(
                            text = stringResource(R.string.p_detail_empty_records),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                // グラフ間のスペース設定
                if (index < graphCount - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * グラフのタイトルと、ヘルプ・拡大アクション用ボタンを表示する行コンポーネント。
 *
 * @param title グラフのタイトル
 * @param helpContent ヘルプダイアログに表示する内容。空文字の場合はヘルプアイコンを表示しません。
 * @param onShowHelp ヘルプアイコンが押された際のコールバック。
 * @param onExpand 拡大アイコンが押された際のコールバック。null の場合は拡大アイコンを表示しません。
 */
@Composable
private fun GraphTitleWithHelp(
    title: String,
    helpContent: String,
    onShowHelp: (String) -> Unit,
    onExpand: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        
        // ヘルプボタン（目安の表示）
        if (helpContent.isNotBlank()) {
            IconButton(onClick = { onShowHelp(helpContent) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = "目安の表示",
                    modifier = Modifier.size(18.dp),
                    tint = Color.Gray
                )
            }
        }
        
        // 拡大ボタン（別画面への遷移）
        if (onExpand != null) {
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ZoomOutMap,
                    contentDescription = "拡大表示",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
