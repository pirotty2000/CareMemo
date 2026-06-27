package jp.mydns.fujiwara.carememo.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.ui.components.HealthChartHelper
import jp.mydns.fujiwara.carememo.ui.components.LineChart
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExpansionScreen(
    viewModel: PersonDetailViewModel,
    personId: Int,
    category: Category,
    initialGraphIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val records by viewModel.filteredRecords.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    // 画面を横向きに固定
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    LaunchedEffect(personId, category) {
        viewModel.loadPerson(personId)
        viewModel.setCategory(category)
    }

    val listState = rememberLazyListState()
    var highlightedIndex by remember { mutableIntStateOf(initialGraphIndex) }

    // 初期スクロールとハイライト
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            listState.scrollToItem(initialGraphIndex)
            delay(500.milliseconds) // 少し待ってからハイライトを消す演出
            highlightedIndex = -1
        }
    }

    Scaffold(
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
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "${currentPerson?.getMaskedName(isNameMaskingEnabled) ?: ""} 様 - ${category.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
                                .border(1.dp, borderColor, MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.padding(4.dp).height(210.dp)) { // 360dp高に最適化
                                SingleGraphInLandscape(records, category, index)
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
fun SingleGraphInLandscape(records: List<Any>, category: Category, index: Int) {
    // 共通のX軸範囲計算
    val (globalMinX, globalMaxX) = remember(records) {
        HealthChartHelper.calculateGlobalXRange(records)
    }

    val config = remember(category, index, records) {
        HealthChartHelper.getChartConfig(category, index, records)
    }

    if (config != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(config.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("データがありません")
                }
            }
        }
    }
}
