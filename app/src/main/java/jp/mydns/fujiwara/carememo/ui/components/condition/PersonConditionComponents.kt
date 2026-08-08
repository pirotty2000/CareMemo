package jp.mydns.fujiwara.carememo.ui.components.condition

/**
 * Component：PersonConditionComponents
 *
 * 【役割】
 * 利用者の「所見記録（カテゴリB）」に関連する履歴リスト、および詳細表示・編集パネル、
 * 写真管理（撮影・表示・削除）のための共通パーツ群を提供します。
 *
 * 【主な機能】
 * ・履歴リスト（ConditionList）：日付順の要約リスト表示とスワイプ削除の提供。
 * ・詳細パネル（ConditionDetailPane）：閲覧と編集のモード切り替え、変更検知による中断保護。
 * ・閲覧表示（ConditionRecordDisplayCard）：テキストと写真のレスポンシブな詳細レイアウト。
 * ・編集フォーム（ConditionRecordEditForm）：音声入力対応のテキスト入力と写真撮影の統合。
 * ・写真管理（PhotoGrid）：3列グリッド表示、キャプション表示、および削除・フルスクリーン遷移。
 * ・迷子写真救済（OrphanedPhotoSelectionDialog）：DBとの不整合で残った写真を記録に再紐付けする機能。
 *
 * 【想定する利用場所】
 * ・PersonConditionScreenContent（所見記録画面のメイン領域）
 *
 * 【このコンポーネントでは行わないこと】
 * ・データベースへの直接アクセス（ViewModel 経由でラムダとして操作を受け取る）。
 * ・OSレベルのカメラ起動ロジック（親の Screen 層または ViewModel が担当）。
 */

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import jp.mydns.fujiwara.carememo.ui.screens.condition.PersonConditionPreviewParameterProvider
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.ui.mapping.ConditionDisplayMapper
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * コンポーネント構造ツリー：
 * ├─ [1] ConditionList (履歴リスト)
 * │    └─ [1-1] ConditionMemoContent (1行分の要約：タイトル・本文・写真アイコン)
 * └─ [2] ConditionDetailPane (詳細ペイン：閲覧/編集の器)
 *      ├─ [2-1] ConditionRecordEditForm (【編集モード】入力フォーム)
 *      │    ├─ [2-1-1] PhotoGrid (写真一覧：削除ボタンあり)
 *      │    └─ 音声入力ランチャー
 *      ├─ [2-2] ConditionRecordDisplayCard (【閲覧モード】詳細表示)
 *      │    └─ [2-2-1] PhotoGrid (写真一覧：フルスクリーン遷移)
 *      └─ [2-3] OrphanedPhotoSelectionDialog (迷子写真の再紐付け用)
 */

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [1] ConditionList
 * 所見メモ専用の履歴リスト。
 *
 * @param records 表示対象の所見レコードリスト
 * @param selectedId 現在選択中のレコードID
 * @param conditionPhotoMap レコードIDごとの写真有無マップ
 * @param isAnyDialogOpen スワイプ削除状態の制御用フラグ
 * @param onSelect アイテム選択時のコールバック
 * @param onDelete アイテム削除（スワイプ）時のコールバック
 * @param lazyListState スクロール状態
 */
@Composable
fun ConditionList(
    records: ImmutableList<ConditionAtVisit>,
    selectedId: String?,
    conditionPhotoMap: Map<String, Boolean>,
    isAnyDialogOpen: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    // 共通の履歴リスト基盤を使用
    PersonHistoryList(
        records = records,
        modifier = modifier,
        selectedRecordId = selectedId,
        onItemClick = { onSelect(it.id) },
        onDeleteSwipe = onDelete,
        isAnyDialogOpen = isAnyDialogOpen,
        lazyListState = lazyListState
    ) { record ->
        (record as? ConditionAtVisit)?.let {
            // [1-1] ConditionMemoContent
            ConditionMemoContent(it, conditionPhotoMap[it.id] == true)
        }
    }
}

/**
 * [1-1] ConditionMemoContent
 * 所見メモの履歴リスト内カードコンテンツ。
 */
@Composable
private fun ConditionMemoContent(
    record: ConditionAtVisit,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // タイトル（ある場合のみ）
        if (!record.title.isNullOrBlank()) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 本文（最大3行表示）
        Text(
            text = record.condition ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 下部：記録者名と写真有無アイコン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhoto) {
                Icon(
                    imageVector = Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = stringResource(R.string.common_author_format, record.author),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [2] ConditionDetailPane
 * 所見メモの詳細・編集ペイン。
 *
 * @param conditionId 対象のレコードID（空文字なら未選択状態、新規IDなら作成モード）
 * @param records レコードリスト
 * @param photos 対象レコードに紐付く写真リスト
 * @param isProcessing 保存中や削除中などの処理中フラグ
 * @param defaultRecorderName デフォルトの記録者名
 * @param onSaveRecord 保存処理のコールバック
 * @param onDeletePhoto 写真削除処理のコールバック
 * @param onSelectedIdChange 選択ID変更（新規作成中止時など）のコールバック
 * @param onCancel 閲覧モードの終了コールバック
 * @param onAddPhotoClick カメラ起動のコールバック
 * @param onPickPhotoClick ギャラリー起動のコールバック
 * @param onReattachPhoto 迷子写真の再紐付けコールバック
 * @param orphanedPhotos 再紐付け可能な迷子写真のリスト
 * @param onNavigateToFullScreen 写真フルスクリーン表示への遷移コールバック
 * @param onMicClick 音声入力開始時のコールバック（効果音再生等に使用）
 */
@Composable
fun ConditionDetailPane(
    conditionId: String?,
    records: ImmutableList<ConditionAtVisit>,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    defaultRecorderName: String,
    modifier: Modifier = Modifier,
    onSaveRecord: (String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onSelectedIdChange: (String?) -> Unit,
    onCancel: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit = {},
    orphanedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    onNavigateToFullScreen: (String, String) -> Unit,
    onMicClick: () -> Unit,
) {
    val memo = remember(records, conditionId) {
        if (conditionId == null || IdLogic.isNew(conditionId)) null
        else records.find { it.id == conditionId }
    }

    // データロード待ち
    if (memo == null && conditionId != null && !IdLogic.isNew(conditionId)) {
        LoadingScreen(modifier = modifier)
        return
    }

    // 新規作成時のみ編集モードから開始。既存データは閲覧から開始。
    var isEditing by remember(conditionId) { mutableStateOf(IdLogic.isNew(conditionId)) }
    val dateTimeState = rememberDateTimeInputState(initialInstant = memo?.recordTime)

    // 入力項目の状態保持
    var title by remember(conditionId) { mutableStateOf(memo?.title ?: "") }
    var condition by remember(conditionId) { mutableStateOf(memo?.condition ?: "") }
    var author by remember(conditionId, defaultRecorderName) {
        mutableStateOf(memo?.author ?: defaultRecorderName)
    }

    // 【重要】変更検知用の初期スナップショット
    // 入力を途中で破棄しようとした際の警告ダイアログ判定に使用
    val initialTitleSnapshot = remember(conditionId) { title }
    val initialConditionSnapshot = remember(conditionId) { condition }
    val initialAuthorSnapshot = remember(conditionId) { author }
    val initialDateTimeSnapshot = remember(conditionId) { dateTimeState.toInstant() }

    val isChanged by remember(title, condition, author, dateTimeState.year.value, dateTimeState.month.value, dateTimeState.day.value, dateTimeState.hour.value, dateTimeState.minute.value) {
        derivedStateOf {
            title != initialTitleSnapshot ||
                    condition != initialConditionSnapshot ||
                    author != initialAuthorSnapshot ||
                    dateTimeState.toInstant() != initialDateTimeSnapshot
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showOrphanedSelectDialog by remember { mutableStateOf(false) }

    // システム戻るボタンによる破棄保護
    androidx.activity.compose.BackHandler(enabled = isEditing && isChanged) {
        showDiscardDialog = true
    }

    // 変更破棄の最終確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = { AppDialogContent(text = stringResource(R.string.common_confirm_discard_message)) },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        if (IdLogic.isNew(conditionId)) onSelectedIdChange(null) else isEditing = false
                    }
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDiscardDialog = false }
                )
            }
        )
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }

    // 未選択状態の表示
    if (conditionId == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.p_detail_empty_records), color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.p_detail_empty_records_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    if (isEditing) {
        // [2-1] ConditionRecordEditForm (記録の編集)
        ConditionRecordEditForm(
            conditionId = conditionId,
            dateTimeState = dateTimeState,
            title = title,
            onTitleChange = { title = it },
            author = author,
            onAuthorChange = { author = it },
            condition = condition,
            onConditionChange = { condition = it },
            photos = photos,
            isProcessing = isProcessing,
            modifier = modifier,
            onSave = {
                val currentState = PersonConditionUiState(title, condition, author, dateTimeState.toInstant())
                onSaveRecord(conditionId, currentState) { newId ->
                    onSelectedIdChange(newId)
                    isEditing = false
                }
            },
            onCancel = {
                if (isChanged) showDiscardDialog = true
                else if (!IdLogic.isNew(conditionId)) isEditing = false else onSelectedIdChange(null)
            },
            onAddPhotoClick = onAddPhotoClick,
            onPickPhotoClick = onPickPhotoClick,
            onReattachClick = { showOrphanedSelectDialog = true },
            orphanedPhotoCount = orphanedPhotos.size,
            onDeletePhoto = { photoToDelete = it },
            onMicClick = onMicClick,
            isChanged = isChanged
        )
    } else {
        // [2-2] ConditionRecordDisplayCard (記録の閲覧)
        ConditionRecordDisplayCard(
            memo = memo,
            photos = photos,
            isProcessing = isProcessing,
            modifier = modifier,
            onCancel = onCancel,
            onEditClick = { isEditing = true },
            onPhotoClick = { onNavigateToFullScreen(it.id, it.conditionId) },
            onAddPhotoClick = onAddPhotoClick,
            onPickPhotoClick = onPickPhotoClick,
            onReattachClick = { showOrphanedSelectDialog = true },
            orphanedPhotoCount = orphanedPhotos.size
        )
    }

    // 迷子写真の再登録用ダイアログ
    if (showOrphanedSelectDialog) {
        OrphanedPhotoSelectionDialog(
            orphanedPhotos = orphanedPhotos,
            onDismiss = { showOrphanedSelectDialog = false },
            onSelect = { info ->
                onReattachPhoto(info)
                showOrphanedSelectDialog = false
            }
        )
    }

    // 写真削除の確認ダイアログ
    if (photoToDelete != null) {
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = {
                photoToDelete?.let { onDeletePhoto(it) }
                photoToDelete = null
            },
            title = stringResource(R.string.condition_photo_delete_confirm_title),
            message = stringResource(R.string.condition_photo_delete_confirm_msg)
        )
    }
}

/**
 * [2-1] ConditionRecordEditForm
 * 所見記録の入力フォーム。
 */
@Composable
private fun ConditionRecordEditForm(
    conditionId: String,
    dateTimeState: DateTimeInputState,
    title: String,
    onTitleChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    condition: String,
    onConditionChange: (String) -> Unit,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onReattachClick: () -> Unit = {},
    orphanedPhotoCount: Int = 0,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onMicClick: () -> Unit,
    isChanged: Boolean
) {
    // 音声認識ランチャーの設定
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                // 音声入力の結果を既存のテキストに追記
                val newCondition = "$condition$spokenText。\n"
                onConditionChange(newCondition)
            }
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (IdLogic.isNew(conditionId)) stringResource(R.string.common_create_new) else stringResource(R.string.common_edit_record),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 入力カード
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(state = dateTimeState)
                    HorizontalDivider(thickness = 0.5.dp)
                    AppTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_title_optional)) },
                        maxLength = AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = author,
                        onValueChange = onAuthorChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_author)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = condition,
                        onValueChange = onConditionChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_memo)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("Condition_MemoInput"),
                        singleLine = false,
                        trailingIcon = {
                            // 音声入力ボタン
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力を開始します")
                                }
                                onMicClick()
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.condition_btn_mic_desc), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).testTag("Condition_SaveButton"),
                            enabled = PersonConditionLogic.isValid(PersonConditionUiState(title, condition, author, dateTimeState.toInstant())) && isChanged
                        ) { Text(stringResource(R.string.common_save)) }
                    }
                }
            }

            // 写真セクションヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.common_photo_count_format, photos.size, AppSpecifications.Condition.Photo.MAX_COUNT), style = MaterialTheme.typography.titleMedium)

                Row {
                    // 迷子写真の再登録ボタン (既存レコードかつ迷子がある場合のみ表示)
                    if (orphanedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onReattachClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = stringResource(R.string.common_orphaned_photo_reattach_title),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    // ギャラリーから追加
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onPickPhotoClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.condition_btn_gallery_desc),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    // カメラで撮影
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onAddPhotoClick, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = stringResource(R.string.condition_btn_camera_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            
            // 新規作成時のみ表示されるガイド
            if (IdLogic.isNew(conditionId)) {
                Text(
                    text = stringResource(R.string.condition_photo_add_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // 写真グリッド
            if (photos.isEmpty()) {
                Text(stringResource(R.string.common_no_photos), color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(photos = photos, isEditable = true, onPhotoClick = {}, onDeletePhoto = onDeletePhoto)
            }

            // 撮影ボタン（強調用）
            if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                Button(onClick = onAddPhotoClick, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.condition_btn_capture))
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * [2-1-1] & [2-2-1] PhotoGrid
 * 写真を3列のグリッドで表示します。
 *
 * @param photos 表示対象の写真リスト
 * @param isEditable 削除ボタンを表示するかどうか
 * @param onPhotoClick 写真タップ時のコールバック
 * @param onDeletePhoto 削除ボタンタップ時のコールバック
 */
@Composable
private fun PhotoGrid(
    photos: ImmutableList<ConditionPhoto>,
    isEditable: Boolean,
    modifier: Modifier = Modifier,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current
    val rows = photos.chunked(3)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("Condition_PhotoList"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowPhotos ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { photo ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            // サムネイル画像の表示
                            AsyncImage(
                                model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                                contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize().clickable { onPhotoClick(photo) },
                                contentScale = ContentScale.Crop
                            )
                            // 編集モード時のみ削除アイコンを表示
                            if (isEditable) {
                                IconButton(
                                    onClick = { onDeletePhoto(photo) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // 写真キャプション（UUID対応のID等が表示される場合がある）
                        Text(text = photo.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // 3列に満たない場合に空白で埋める
                repeat(3 - rowPhotos.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * [2-2] ConditionRecordDisplayCard
 * 所見記録の詳細閲覧用カード。
 */
@Composable
private fun ConditionRecordDisplayCard(
    memo: ConditionAtVisit?,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onEditClick: () -> Unit,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onReattachClick: () -> Unit = {},
    orphanedPhotoCount: Int = 0,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ヘッダー部：戻るボタン、タイトル、編集ボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = stringResource(R.string.common_record_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp)
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.common_edit))
                }
            }

            // 内容カード
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    memo?.let { m ->
                        // 記録日時
                        Text(text = DateTimeUtils.formatRecordTime(m.recordTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        // タイトル（任意）
                        if (!m.title.isNullOrBlank()) {
                            Text(text = m.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        // 本文
                        Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        // 記録者名
                        Text(text = stringResource(R.string.common_author_format, m.author),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // 写真セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.common_photo_count_format, photos.size, AppSpecifications.Condition.Photo.MAX_COUNT),
                    style = MaterialTheme.typography.titleMedium)

                Row {
                    if (orphanedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onReattachClick, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = stringResource(R.string.common_orphaned_photo_reattach_title), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onPickPhotoClick, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.condition_btn_gallery_desc), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT) {
                        IconButton(onClick = onAddPhotoClick, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = stringResource(R.string.condition_btn_camera_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (photos.isEmpty()) {
                Text(stringResource(R.string.common_no_photos), color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(photos = photos, isEditable = false, onPhotoClick = onPhotoClick, onDeletePhoto = {})
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * [2-3] OrphanedPhotoSelectionDialog
 * データベースの不整合により、親となる記録IDが失われた「迷子写真」を
 * 現在の記録に再紐付けするための選択ダイアログです。
 *
 * @param orphanedPhotos 迷子写真情報のリスト
 * @param onDismiss キャンセル時のコールバック
 * @param onSelect 写真選択時のコールバック
 */
@Composable
private fun OrphanedPhotoSelectionDialog(
    orphanedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSelect: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit
) {
    val context = LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.common_orphaned_photo_reattach_title)) },
        text = {
            AppDialogContent {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.common_orphaned_photo_reattach_msg), style = MaterialTheme.typography.bodySmall)

                    // 迷子写真を2列グリッドで提示
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 400.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(orphanedPhotos.size) { index ->
                            val info = orphanedPhotos[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(info) }
                            ) {
                                Box(modifier = Modifier.aspectRatio(1f)) {
                                    AsyncImage(
                                        model = info.thumbnailFileName?.let { ImageUtils.getPhotoFile(context, it) },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(
                                    text = info.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}, // 選択により即座に確定するため confirmButton は未使用
        dismissButton = {
            AppDialogDismissButton(text = stringResource(R.string.common_cancel), onClick = onDismiss)
        }
    )
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Previews
////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun PreviewConditionDetailPane(
    @PreviewParameter(PersonConditionPreviewParameterProvider::class) state: PersonConditionPreviewState
) {
    MaterialTheme {
        ConditionDetailPane(
            conditionId = state.selectedRecordId,
            records = state.records,
            photos = persistentListOf(),
            isProcessing = state.isLoading,
            defaultRecorderName = "A",
            onSaveRecord = { _, _, _ -> },
            onDeletePhoto = {},
            onSelectedIdChange = {},
            onCancel = {},
            onAddPhotoClick = {},
            onPickPhotoClick = {},
            onReattachPhoto = {},
            orphanedPhotos = persistentListOf(),
            onNavigateToFullScreen = { _, _ -> },
            onMicClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, name = "編集フォーム")
@Composable
private fun PreviewConditionRecordEditFormDirect() {
    MaterialTheme {
        ConditionRecordEditForm(
            conditionId = "new",
            dateTimeState = rememberDateTimeInputState(),
            title = MockData.condition.title ?: "",
            onTitleChange = {},
            author = MockData.condition.author,
            onAuthorChange = {},
            condition = MockData.condition.condition ?: "",
            onConditionChange = {},
            photos = persistentListOf(),
            isProcessing = false,
            orphanedPhotoCount = 2,
            onSave = {},
            onCancel = {},
            onAddPhotoClick = {},
            onPickPhotoClick = {},
            onReattachClick = {},
            onDeletePhoto = {},
            onMicClick = {},
            isChanged = true
        )
    }
}
