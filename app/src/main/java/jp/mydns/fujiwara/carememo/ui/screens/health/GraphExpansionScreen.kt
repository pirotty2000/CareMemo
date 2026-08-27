package jp.mydns.fujiwara.carememo.ui.screens.health

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.base.EmptyState
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.health.HealthChartHelper
import jp.mydns.fujiwara.carememo.ui.components.health.LineChart
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI Action：グラフ拡大画面におけるユーザー操作の集約定義
 */
sealed interface GraphExpansionUiAction {
    data object Back : GraphExpansionUiAction
}

/**
 * Screen：GraphExpansionScreen
 *
 * 【役割】
 * 健康記録の各グラフ（血圧、血糖、BMI 等）を全画面かつ横向き（ランドスケープ）で詳細に閲覧するための画面です。
 * 時間軸を広く取ることで、長期的な推移や微妙な変化を捉えやすくします。
 *
 * 【主な機能】
 * ・ランドスケープ固定：`DisposableEffect` を使用し、画面表示中のみ強制的に横画面へ切り替え。
 * ・初期位置へのスクロール：遷移元のグラフインデックスに基づき、対象グラフを自動的にフォーカス。
 * ・ハイライト表示：対象グラフに一時的な枠線アニメーションを適用し、視認性を向上。
 * ・詳細閲覧：`LineChart` エンジンによる詳細な推移表示（ズーム・ツールチップ対応）。
 *
 * 【全体像：グラフ拡大構成（Graph Expansion）】
 *
 * ■ GraphExpansionScreen (★本コンポーネント)
 * │
 * ├─ TopAppBar (利用者名 ＋ 戻るボタン)
 * └─ LazyColumn (グラフリスト)
 *      └─ Card (各グラフの器)
 *           └─ [1] SingleGraphInLandscape (横画面用描画コンテナ)
 *                └─ LineChart (描画エンジン：ui/components/health/LineChart.kt)
 *
 * 【このコンポーネントでは行わないこと】
 * ・データの保存や削除操作。
 * ・詳細入力画面（ScreenContent）との共有状態管理（拡大表示に特化）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExpansionScreen(
    detailViewModel: PersonDetailUiStateViewModel,
    healthViewModel: PersonHealthViewModel,
    initialGraphIndex: Int,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val healthState by healthViewModel.uiState.collectAsStateWithLifecycle()
    val isNameMaskingEnabled by detailViewModel.isNameMaskingEnabled.collectAsStateWithLifecycle()

    val category = detailState.currentCategory

    val records by remember(category, healthState.personId) { 
        healthViewModel.getHealthRecords(category) 
    }.collectAsStateWithLifecycle()
    
    // 画面を横向きに固定
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val handleAction: (GraphExpansionUiAction) -> Unit = remember(navController) {
        { action ->
            when (action) {
                GraphExpansionUiAction.Back -> navController.popBackStack()
            }
        }
    }

    GraphExpansionScreenContent(
        uiState = healthState,
        records = records,
        person = detailState.person,
        category = category,
        isNameMaskingEnabled = isNameMaskingEnabled,
        initialGraphIndex = initialGraphIndex,
        onAction = handleAction,
        modifier = modifier
    )
}

/**
 * Screen：GraphExpansionScreenContent
 *
 * 【役割】
 * グラフ拡大画面のレイアウト本体 (Stateless)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExpansionScreenContent(
    uiState: PersonHealthUiState,
    records: List<Any>,
    person: Person?,
    category: Category,
    isNameMaskingEnabled: Boolean,
    initialGraphIndex: Int,
    onAction: (GraphExpansionUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var highlightedIndex by remember { mutableIntStateOf(initialGraphIndex) }

    // 初期スクロールとハイライト
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            listState.scrollToItem(initialGraphIndex)
            delay(500.milliseconds)
            highlightedIndex = -1
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onAction(GraphExpansionUiAction.Back) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("GraphExpansion_BackButton")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "${person?.getMaskedName(isNameMaskingEnabled) ?: ""}${stringResource(R.string.common_honorific_suffix)}${stringResource(R.string.common_title_separator)}${stringResource(category.displayNameRes)}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.testTag("GraphExpansion_HeaderTitle")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isLoading && records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().testTag("GraphExpansion_Loading")) {
                    LoadingScreen()
                }
            } else if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().testTag("GraphExpansion_EmptyState")) {
                    EmptyState(
                        message = stringResource(R.string.p_detail_empty_records),
                        icon = Icons.AutoMirrored.Rounded.ShowChart
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("GraphExpansion_GraphList"),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val graphCount = HealthChartHelper.getGraphCount(category)

                    items(graphCount) { index ->
                        val isHighlighted = index == initialGraphIndex && highlightedIndex == index
                        val borderColor by animateColorAsState(
                            targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(durationMillis = 1000),
                            label = "Highlight"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                                .testTag("GraphExpansion_GraphCard_$index"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.padding(4.dp).height(210.dp)) {
                                SingleGraphInLandscape(
                                    records = records,
                                    category = category,
                                    index = index,
                                    modifier = Modifier.testTag("GraphExpansion_ChartView_$index")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 横画面用に特定のインデックスのグラフのみを描画する
 */
@Composable
fun SingleGraphInLandscape(
    records: List<Any>,
    category: Category,
    index: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (globalMinX, globalMaxX) = remember(records) {
        HealthChartHelper.calculateGlobalXRange(records)
    }

    val config = remember(category, index, records, context, isDark) {
        HealthChartHelper.getChartConfig(context, category, index, records, isDark)
    }

    if (config != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {}
        ) {
            Text(config.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (config.dataList.any { it.points.isNotEmpty() }) {
                LineChart(
                    dataList = config.dataList,
                    modifier = Modifier.weight(1f),
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.p_detail_empty_records))
                }
            }
        }
    }
}
