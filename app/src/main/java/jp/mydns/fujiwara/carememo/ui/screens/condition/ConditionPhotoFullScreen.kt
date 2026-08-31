package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import kotlin.math.abs

/**
 * UI Action：写真全画面閲覧におけるユーザー操作の集約定義
 */
sealed interface ConditionPhotoFullUiAction {
    data object Back : ConditionPhotoFullUiAction
}

/**
 * Screen：ConditionPhotoFullScreen
 *
 * 【役割】
 * 所見メモに添付された写真を全画面で閲覧するための画面です。
 * 複数の写真をスワイプで切り替えたり、ピンチ操作による拡大・縮小表示（ズーム）を提供します。
 *
 * 【主な機能】
 * ・写真カルーセル：`HorizontalPager` による左右スワイプでの写真切り替え。
 * ・ズーム機能：ピンチズーム、ダブルタップによる拡大・縮小、および拡大時のパン操作。
 * ・キャプション表示：写真に付随する説明文のオーバーレイ表示。
 * ・ナビゲーション：戻るボタンによる詳細画面への復帰。
 *
 * 【全体像：写真閲覧構造（Photo Viewer）】
 *
 * ■ ConditionPhotoFullScreen (★本コンポーネント)
 * │
 * ├─ HorizontalPager (カルーセル)
 * │    └─ [1] ZoomableImage (個別の写真：ズーム・パン制御)
 * │         └─ AsyncImage (画像描画：Coil)
 * │
 * ├─ IconButton (戻るボタン)
 * └─ Surface (キャプション：下部オーバーレイ)
 *
 * 【このコンポーネントでは行わないこと】
 * データの保存や削除操作（閲覧専用）。
 */
@Composable
fun ConditionPhotoFullScreen(
    viewModel: PersonConditionViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleAction: (ConditionPhotoFullUiAction) -> Unit = remember(navController) {
        { action ->
            when (action) {
                ConditionPhotoFullUiAction.Back -> navController.popBackStack()
            }
        }
    }

    ConditionPhotoFullContent(
        uiState = uiState,
        onAction = handleAction,
        modifier = modifier
    )
}

/**
 * Screen：ConditionPhotoFullContent
 *
 * 【役割】
 * 写真全画面閲覧のレイアウト本体 (Stateless)
 */
@Composable
fun ConditionPhotoFullContent(
    uiState: PersonConditionUiState,
    onAction: (ConditionPhotoFullUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photos = uiState.currentConditionPhotos
    val initialPhotoId = uiState.initialPhotoId
    val isLoading = uiState.isLoading

    if (photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black).testTag("PhotoFullScreen_Container"), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.testTag("PhotoFullScreen_Spinner"))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.condition_msg_no_photos_to_show), color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onAction(ConditionPhotoFullUiAction.Back) }) {
                        Text(stringResource(R.string.common_back))
                    }
                }
            }
        }
        return
    }

    val initialIndex = remember(photos, initialPhotoId) {
        val index = photos.indexOfFirst { it.id == initialPhotoId }
        if (index != -1) index else 0
    }

    key(initialPhotoId) {
        val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
        var isAnyImageZoomed by remember { mutableStateOf(false) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("PhotoFullScreen_Pager"),
                pageSpacing = 16.dp,
                userScrollEnabled = !isAnyImageZoomed
            ) { page ->
                val photo = photos[page]
                ZoomableImage(
                    photo = photo,
                    isCurrentPage = pagerState.currentPage == page,
                    onZoomStateChanged = { zoomed ->
                        if (pagerState.currentPage == page) {
                            isAnyImageZoomed = zoomed
                        }
                    }
                )
            }

            IconButton(
                onClick = { onAction(ConditionPhotoFullUiAction.Back) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 32.dp, start = 16.dp)
                    .testTag("PhotoFullScreen_BackButton"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }

            val currentPhoto = photos.getOrNull(pagerState.currentPage)
            if (currentPhoto != null && currentPhoto.caption.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .testTag("PhotoFullScreen_Caption"),
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ) {
                    Text(
                        text = currentPhoto.caption,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * ズーム・パン操作が可能な画像表示コンポーネント。
 */
@Composable
fun ZoomableImage(
    photo: ConditionPhoto,
    isCurrentPage: Boolean,
    onZoomStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoFile = remember(photo.photoFileName) {
        ImageUtils.getPhotoFile(context, photo.photoFileName)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onZoomStateChanged(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("PhotoFullScreen_Image_${photo.id}")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero 
                        }
                        onZoomStateChanged(scale > 1.05f)
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val isMultiTouch = event.changes.size > 1
                                if (scale > 1.05f || isMultiTouch) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    val newOffset = if (newScale > 1.05f) offset + panChange else Offset.Zero
                                    
                                    scale = newScale
                                    offset = newOffset
                                    onZoomStateChanged(scale > 1.05f)
                                    
                                    event.changes.forEach { 
                                        if (it.positionChange() != Offset.Zero) it.consume() 
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photoFile,
            contentDescription = photo.caption,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
