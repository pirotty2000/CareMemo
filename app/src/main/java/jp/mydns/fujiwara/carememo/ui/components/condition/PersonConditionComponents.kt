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
 * ・未割り当て写真救済（UnassignedPhotoSelectionDialog）：DBとの不整合で残った写真を記録に再紐付けする機能。
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
import jp.mydns.fujiwara.carememo.ui.screens.condition.PersonConditionUiAction
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditInput
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * 全体像：利用者所見記録（Condition）
 *
 * ■ ui/screens/condition/PersonConditionScreenContent.kt の PersonConditionScreenContent (画面全体の器)
 * │
 * ├─【一覧セクション】
 * │  └─ [1] ConditionList (所見記録リスト：PersonConditionComponents.kt)
 * │       └─ ■ ui/components/common/HistoryComponents.kt の PersonHistoryList (共通履歴リストの枠)
 * │            └─ [1-1] ConditionMemoContent (履歴1行分の要約：タイトル・本文・写真アイコン)
 * │
 * └─【詳細セクション】
 *      └─ [2] ConditionDetailPane (詳細・編集パネル：PersonConditionComponents.kt)
 *           ├─ [2-1] ConditionRecordEditForm (【編集モード】入力フォーム)
 *           │    ├─ DateTimeInputFields (日時入力：ui/components/common/DateTimeInputFields.kt)
 *           │    ├─ AppTextField (各種入力：ui/components/base/AppTextField.kt)
 *           │    ├─ [2-1-1] PhotoGrid (写真一覧：削除ボタンあり)
 *           │    └─ 音声入力ランチャー
 *           ├─ [2-2] ConditionRecordDisplayCard (【閲覧モード】詳細表示)
 *           │    └─ [2-2-1] PhotoGrid (写真一覧：閲覧・フルスクリーン遷移)
 *           └─ [2-3] UnassignedPhotoSelectionDialog (未割り当て写真の再紐付け用)
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
 * @param modifier 修飾子
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
 * @param uiState UI 状態
 * @param onAction アクションハンドラ
 * @param modifier 修飾子
 */
@Composable
fun ConditionDetailPane(
    uiState: PersonConditionUiState,
    onAction: (PersonConditionUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val memo = remember(uiState.records, uiState.selectedConditionId) {
        if ((uiState.selectedConditionId == null || IdLogic.isNew(uiState.selectedConditionId))) null
        else uiState.records.find { it.id == uiState.selectedConditionId }
    }

    // データロード待ち
    if (memo == null && uiState.selectedConditionId != null && !IdLogic.isNew(uiState.selectedConditionId)) {
        LoadingScreen(modifier = modifier)
        return
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnassignedSelectDialog by remember { mutableStateOf(false) }

    // システム戻るボタンによる破棄保護
    androidx.activity.compose.BackHandler(enabled = uiState.isEditing && uiState.isChanged) {
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
                        onAction(PersonConditionUiAction.CancelEdit)
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
    if (uiState.selectedConditionId == null) {
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

    if (uiState.isEditing) {
        // [2-1] ConditionRecordEditForm (記録の編集)
        ConditionRecordEditForm(
            conditionId = uiState.selectedConditionId,
            editInput = uiState.editInput,
            initialRecordTime = uiState.initialRecordTime,
            photos = uiState.currentConditionPhotos,
            isProcessing = uiState.isProcessing,
            isSaveEnabled = uiState.isSaveEnabled,
            isChanged = uiState.isChanged, // 追加
            fieldErrors = uiState.fieldErrors,
            unassignedPhotoCount = uiState.unassignedPhotoCount,
            onAction = onAction,
            onDeletePhotoRequest = { photoToDelete = it },
            onCancelRequest = {
                if (uiState.isChanged) showDiscardDialog = true
                else onAction(PersonConditionUiAction.CancelEdit)
            },
            onReattachRequest = { showUnassignedSelectDialog = true },
            modifier = modifier
        )
    } else {
        // [2-2] ConditionRecordDisplayCard (記録の閲覧)
        ConditionRecordDisplayCard(
            memo = memo,
            photos = uiState.currentConditionPhotos,
            isProcessing = uiState.isProcessing,
            unassignedPhotoCount = uiState.unassignedPhotoCount,
            onAction = onAction,
            onReattachRequest = { showUnassignedSelectDialog = true },
            modifier = modifier
        )
    }

    // 未割り当て写真の再登録用ダイアログ
    if (showUnassignedSelectDialog) {
        UnassignedPhotoSelectionDialog(
            unassignedPhotos = uiState.availableUnassignedPhotos,
            onDismiss = { showUnassignedSelectDialog = false },
            onSelect = { info ->
                onAction(PersonConditionUiAction.ReattachPhoto(info))
                showUnassignedSelectDialog = false
            }
        )
    }

    // 写真削除の確認ダイアログ
    if (photoToDelete != null) {
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = {
                photoToDelete?.let { onAction(PersonConditionUiAction.DeletePhoto(it)) }
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
    editInput: ConditionEditInput,
    initialRecordTime: Instant?,
    photos: ImmutableList<ConditionPhoto>,
    isProcessing: Boolean,
    isSaveEnabled: Boolean,
    isChanged: Boolean, // 追加
    fieldErrors: Map<String, Int?>,
    onAction: (PersonConditionUiAction) -> Unit,
    onDeletePhotoRequest: (ConditionPhoto) -> Unit,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier,
    unassignedPhotoCount: Int = 0,
    onReattachRequest: () -> Unit = {},
) {
    // 音声認識ランチャーの設定
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                // 音声入力の結果を既存のテキストに追記
                onAction(PersonConditionUiAction.EditInputUpdate { it.copy(condition = "${it.condition}$spokenText。\n") })
            }
        }
    }

    val dateTimeState = rememberDateTimeInputState(initialInstant = initialRecordTime)

    // 日時状態を ViewModel へ同期
    LaunchedEffect(
        dateTimeState.year.value,
        dateTimeState.month.value,
        dateTimeState.day.value,
        dateTimeState.hour.value,
        dateTimeState.minute.value
    ) {
        val nextTime = dateTimeState.toInstant()
        if (nextTime != editInput.recordTime) {
            onAction(PersonConditionUiAction.EditInputUpdate { it.copy(recordTime = nextTime) })
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp), // ナビゲーションバー等との重なり防止のため下部余白を拡充
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ヘッダー部：戻るボタン、タイトル
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancelRequest,
                    modifier = Modifier
                        .offset(x = (-12).dp)
                        .testTag("Condition_EditBackButton")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    text = if (IdLogic.isNew(conditionId)) stringResource(R.string.common_create_new) else stringResource(R.string.common_edit_record),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (-8).dp)
                )
            }

            // 入力カード
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(
                        state = dateTimeState,
                        isError = fieldErrors["recordTime"] != null,
                        supportingText = fieldErrors["recordTime"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { field, _ -> onAction(PersonConditionUiAction.MarkFieldAsTouched(field)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    AppTextField(
                        value = editInput.title,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(title = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_title_optional)) },
                        maxLength = AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE,
                        isError = fieldErrors["title"] != null,
                        supportingText = fieldErrors["title"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("title")) },
                        modifier = Modifier.fillMaxWidth().testTag("Condition_TitleInput")
                    )
                    AppTextField(
                        value = editInput.author,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(author = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_author)) },
                        isError = fieldErrors["author"] != null,
                        supportingText = fieldErrors["author"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("author")) },
                        modifier = Modifier.fillMaxWidth().testTag("Condition_AuthorInput")
                    )
                    AppTextField(
                        value = editInput.condition,
                        onValueChange = { v -> onAction(PersonConditionUiAction.EditInputUpdate { it.copy(condition = v) }) },
                        type = AppTextFieldType.TEXT,
                        label = { Text(stringResource(R.string.condition_label_memo)) },
                        isError = fieldErrors["condition"] != null,
                        supportingText = fieldErrors["condition"]?.let { { Text(stringResource(it)) } },
                        onFocusChanged = { if (!it.isFocused) onAction(PersonConditionUiAction.MarkFieldAsTouched("condition")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("Condition_MemoInput"),
                        singleLine = false,
                        trailingIcon = {
                            // 音声入力ボタン
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力を開始します")
                                }
                                onAction(PersonConditionUiAction.MicClick)
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.condition_btn_mic_desc), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancelRequest,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(if (isChanged) R.string.common_cancel else R.string.common_back)) }
                        Button(
                            onClick = { onAction(PersonConditionUiAction.SaveClick { onAction(PersonConditionUiAction.SelectedIdChanged(it)) }) },
                            modifier = Modifier.weight(1f).testTag("Condition_SaveButton"),
                            enabled = isSaveEnabled
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
                    // 未割り当て写真の再登録ボタン (既存レコードかつ未割り当てがある場合のみ表示)
                    if (unassignedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onReattachRequest, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = stringResource(R.string.common_unassigned_photo_reattach_title),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    // ギャラリーから追加
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = { onAction(PersonConditionUiAction.PickPhotoClick) }, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.condition_btn_gallery_desc),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    // カメラで撮影
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.AddPhotoClick) },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("Condition_AddPhotoButton")
                        ) {
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
                PhotoGrid(
                    photos = photos,
                    isEditable = true,
                    onPhotoClick = {},
                    onDeletePhoto = onDeletePhotoRequest
                )
            }

            // 撮影ボタン（強調用）
            if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                Button(onClick = { onAction(PersonConditionUiAction.AddPhotoClick) }, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
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
 * @param modifier 修飾子
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onPhotoClick(photo) }
                                    .testTag("ConditionPhoto_${photo.id}"),
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
    onAction: (PersonConditionUiAction) -> Unit,
    modifier: Modifier = Modifier,
    unassignedPhotoCount: Int = 0,
    onReattachRequest: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .testTag("ConditionDetailPane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp), // ナビゲーションバー等との重なり防止のため下部余白を拡充
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
                        onClick = { onAction(PersonConditionUiAction.SelectedIdChanged(null)) },
                        modifier = Modifier
                            .offset(x = (-12).dp)
                            .testTag("Condition_DisplayBackButton")
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
                IconButton(onClick = { onAction(PersonConditionUiAction.EditClick) }) {
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
                    if (unassignedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onReattachRequest, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = stringResource(R.string.common_unassigned_photo_reattach_title), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = { onAction(PersonConditionUiAction.PickPhotoClick) }, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.condition_btn_gallery_desc), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT) {
                        IconButton(
                            onClick = { onAction(PersonConditionUiAction.AddPhotoClick) },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("Condition_AddPhotoButton")
                        ) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = stringResource(R.string.condition_btn_camera_desc), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (photos.isEmpty()) {
                Text(stringResource(R.string.common_no_photos), color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = false,
                    onPhotoClick = { onAction(PersonConditionUiAction.NavigateToPhotoFullScreen(it.id, it.conditionId)) },
                    onDeletePhoto = {}
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * [2-3] UnassignedPhotoSelectionDialog
 * データベースの不整合により、親となる記録IDが失われた「未割り当て写真」を
 * 現在の記録に再紐付けするための選択ダイアログです。
 *
 * @param unassignedPhotos 未割り当て写真情報のリスト
 * @param modifier 修飾子
 * @param onDismiss キャンセル時のコールバック
 * @param onSelect 写真選択時のコールバック
 */
@Composable
private fun UnassignedPhotoSelectionDialog(
    unassignedPhotos: ImmutableList<jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSelect: (jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo) -> Unit
) {
    val context = LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.common_unassigned_photo_reattach_title)) },
        text = {
            AppDialogContent {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.common_unassigned_photo_reattach_msg), style = MaterialTheme.typography.bodySmall)

                    // 未割り当て写真を2列グリッドで提示
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 400.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unassignedPhotos.size) { index ->
                            val info = unassignedPhotos[index]
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
                                    text = stringResource(info.descriptionResId),
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
            uiState = PersonConditionUiState(
                selectedConditionId = state.selectedRecordId,
                records = state.records,
                isLoading = state.isLoading
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, name = "編集フォーム")
@Composable
private fun PreviewConditionRecordEditFormDirect() {
    MaterialTheme {
        ConditionRecordEditForm(
            conditionId = "new",
            editInput = ConditionEditInput(
                title = MockData.condition.title ?: "",
                author = MockData.condition.author,
                condition = MockData.condition.condition ?: "",
                recordTime = Instant.now()
            ),
            initialRecordTime = Instant.now(),
            photos = persistentListOf(),
            isProcessing = false,
            isSaveEnabled = true,
            isChanged = false,
            fieldErrors = emptyMap(),
            unassignedPhotoCount = 0,
            onAction = {},
            onDeletePhotoRequest = {},
            onCancelRequest = {},
            onReattachRequest = {}
        )
    }
}
