package jp.mydns.fujiwara.carememo.ui.components.condition

/**
 * Component：PersonConditionComponents
 *
 * 【役割】：
 * 所見記録（カテゴリB）に関連する履歴リスト、および詳細表示・編集用のパネルと
 * 写真管理（撮影・表示・削除）のための共通パーツ群を提供する。
 *
 * 【主な機能】：
 * ・履歴リスト（ConditionList）：時系列データの表示とスワイプ削除の基盤提供。
 * ・詳細パネル（ConditionDetailPane）：閲覧モードと編集モードの動的な切り替え。
 * ・閲覧表示（ConditionRecordDisplayCard）：テキストと写真の見やすいレイアウト提供。
 * ・編集フォーム（ConditionRecordEditForm）：テキスト入力と写真撮影の統合。
 * ・写真表示（PhotoGrid）：3列固定のレスポンシブなサムネイルグリッド表示。
 *
 * 【想定する利用場所】：
 * ・PersonConditionScreenContent（所見記録のメインコンテンツ領域）
 *
 * 【このコンポーネントでは行わないこと】：
 * ・データベースへの直接アクセス（すべて引数またはラムダ経由で外部から操作）
 * ・OSレベルのカメラ起動ロジック（親の Screen 層が担当）
 *
 * 【公開composable】：
 * ・ConditionList
 * ・ConditionDetailPane
 *
 * ---
 * 最終更新日: 2026/07/20 (UUID対応)
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
import java.time.Instant
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

/**
 * 全体像：利用者所見記録（Condition）
 *
 * ■ ui/screens/condition/PersonConditionScreenContent.kt の PersonConditionScreenContent (画面全体の器)
 * ├─【一覧セクション】
 * │  └─ [1] ConditionList (所見記録リスト：PersonConditionComponents.kt)
 * │       └─ ■ ui/components/common/HistoryComponents.kt の PersonHistoryList (共通履歴リストの枠)
 * │            └─ [1-1] ConditionMemoContent (履歴1行分の要約：タイトル・本文・写真アイコン)
 * └─【詳細セクション】
 *      └─ [2] ConditionDetailPane (詳細・編集パネル：PersonConditionComponents.kt)
 *           ├─ [2-1] ConditionRecordEditForm (【編集モード】入力フォーム)
 *           │    ├─ DateTimeInputFields (日時入力)
 *           │    ├─ AppTextField (タイトル、記録者、本文/音声入力対応)
 *           │    ├─ [2-1-1] PhotoGrid (写真一覧：削除ボタンあり)
 *           │    └─ <アクション> キャンセル、保存ボタン
 *           ├─ [2-2] ConditionRecordDisplayCard (【閲覧モード】詳細表示用)
 *           │    ├─ <ヘッダー> 戻るボタン、タイトル、編集ボタン
 *           │    ├─ <内容部> 記録日時、タイトル、本文、記録者名
 *           │    └─ [2-2-1] PhotoGrid (写真一覧：閲覧・フルスクリーン遷移)
 *           └─ [2-3] OrphanedPhotoSelectionDialog (迷子写真の再登録用ダイアログ)
 */

////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * [1] ConditionList
 * 所見メモ用の履歴リスト。
 */
@Composable
fun ConditionList(
    records: List<ConditionAtVisit>,
    selectedId: String,
    conditionPhotoMap: Map<String, Boolean>,
    isAnyDialogOpen: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (HistoryRecord) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    // 所見メモ・履歴一覧の枠
    // ui/components/common/HistoryComponents.kt
    PersonHistoryList(
        records = records,
        selectedRecordId = selectedId,
        onItemClick = { onSelect(it.id) },
        onDeleteSwipe = onDelete,
        isAnyDialogOpen = isAnyDialogOpen,
        lazyListState = lazyListState
    ) { record ->
        (record as? ConditionAtVisit)?.let {
            // [1-1] ConditionMemoContent
            // 履歴を1件ずつ渡して一覧表示
            ConditionMemoContent(it, conditionPhotoMap[it.id] == true)
        }
    }
}

/**
 * [1-1] ConditionMemoContent
 * 所見メモの一覧表示 (リストアイテム用)
 */
@Composable
private fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
        // 所見メモ・タイトル
        if (!record.title.isNullOrBlank()) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Spacer
        Spacer(modifier = Modifier.height(4.dp))
        // 所見メモ・メモ本文
        Text(
            text = record.condition ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        // Spacer
        Spacer(modifier = Modifier.height(4.dp))
        // 所見メモ・記録者とカメラアイコン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhoto) {
                Icon(
                    imageVector = Icons.Rounded.AddAPhoto,
                    contentDescription = "写真あり",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "記録者: ${record.author}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * [2] ConditionDetailPane
 * 所見メモ詳細ペイン。
 */
@Composable
fun ConditionDetailPane(
    conditionId: String,
    records: List<ConditionAtVisit>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    defaultRecorderName: String,
    onSaveRecord: (String, PersonConditionUiState, (String) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onSelectedIdChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit = {},
    onReattachPhoto: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit = {},
    orphanedPhotos: List<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo> = emptyList(),
    onNavigateToFullScreen: (String, String) -> Unit,
    onMicClick: () -> Unit,
) {
    val memo = remember(records, conditionId) {
        if (IdLogic.isNew(conditionId)) null
        else records.find { it.id == conditionId }
    }

    // conditionId が UUID であっても、データが見つからない場合はロード中とする（新規作成 ID の場合はスキップ）
    if (memo == null && !IdLogic.isNew(conditionId)) {
        LoadingScreen()
        return
    }

    // Condition は閲覧を優先するため、新規作成 ID の場合のみ編集モードから開始する。
    var isEditing by remember(conditionId) { mutableStateOf(IdLogic.isNew(conditionId)) }
    val dateTimeState = rememberDateTimeInputState(initialInstant = memo?.recordTime)

    var title by remember(conditionId) { mutableStateOf(memo?.title ?: "") }
    var condition by remember(conditionId) { mutableStateOf(memo?.condition ?: "") }
    var author by remember(conditionId, defaultRecorderName) {
        mutableStateOf(memo?.author ?: defaultRecorderName)
    }

    // 変更検知用の初期状態（UI初期化直後のスナップショットを保持）
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

    // システム戻るボタンの制御
    androidx.activity.compose.BackHandler(enabled = isEditing && isChanged) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_discard_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    type = AppDialogActionType.DELETE,
                    onClick = {
                        showDiscardDialog = false
                        if (IdLogic.isNew(conditionId)) onSelectedIdChange("") else isEditing = false
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

    if (conditionId.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("左のリストから記録を選択してください", color = MaterialTheme.colorScheme.outline)
                Text("右上の「＋」から新しい記録を追加できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    if (isEditing) {
        // [2-1] ConditionRecordEditForm(記録の編集)
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
            onSave = {
                val currentState = PersonConditionUiState(title, condition, author, dateTimeState.toInstant())
                onSaveRecord(conditionId, currentState) { newId ->
                    onSelectedIdChange(newId)
                    isEditing = false
                }
            },
            onCancel = {
                if (isChanged) {
                    showDiscardDialog = true
                } else {
                    if (!IdLogic.isNew(conditionId)) isEditing = false else onSelectedIdChange("")
                }
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
        // [2-2] ConditionRecordDisplayCard
        ConditionRecordDisplayCard(
            memo = memo,
            photos = photos,
            isProcessing = isProcessing,
            onCancel = onCancel,
            onEditClick = { isEditing = true },
            onPhotoClick = { onNavigateToFullScreen(it.conditionId, it.id) },
            onAddPhotoClick = onAddPhotoClick,
            onPickPhotoClick = onPickPhotoClick,
            onReattachClick = { showOrphanedSelectDialog = true },
            orphanedPhotoCount = orphanedPhotos.size
        )
    }

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

    if (photoToDelete != null) {
        AppDeleteConfirmDialog(
            onDismiss = { photoToDelete = null },
            onDelete = {
                photoToDelete?.let { onDeletePhoto(it) }
                photoToDelete = null
            },
            title = "写真の削除",
            message = "この写真を削除してもよろしいですか？"
        )
    }
}

/**
 * [2-1] ConditionRecordEditForm
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
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
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
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                val newCondition = "$condition$spokenText。\n"
                onConditionChange(newCondition)
            }
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // キーボード回避
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
                text = if (IdLogic.isNew(conditionId)) "新規作成" else "記録の編集",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateTimeInputFields(state = dateTimeState)
                    HorizontalDivider(thickness = 0.5.dp)
                    AppTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("タイトル (任意)") },
                        maxLength = AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = author,
                        onValueChange = onAuthorChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("記録者") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = condition,
                        onValueChange = onConditionChange,
                        type = AppTextFieldType.TEXT,
                        label = { Text("所見メモ") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("Condition_MemoInput"),
                        singleLine = false,
                        trailingIcon = {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "音声入力を開始します")
                                }
                                onMicClick()
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Rounded.Mic, contentDescription = "音声入力", tint = MaterialTheme.colorScheme.primary)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = ConditionDisplayMapper.getPhotoCountLabel(photos.size), style = MaterialTheme.typography.titleMedium)

                Row {
                    // 迷子写真の再登録ボタン (動的表示)
                    if (orphanedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onReattachClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload, // フォルダに矢印的なものを期待
                                contentDescription = "迷子写真を再登録",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onPickPhotoClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = "ギャラリーから追加",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                        IconButton(onClick = onAddPhotoClick, enabled = !isProcessing) {
                            Icon(imageVector = Icons.Rounded.AddAPhoto, contentDescription = "写真を撮影", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (IdLogic.isNew(conditionId)) {
                Text(
                    text = stringResource(R.string.condition_photo_add_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (photos.isEmpty()) {
                Text("写真がありません", color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(photos = photos, isEditable = true, onPhotoClick = {}, onDeletePhoto = onDeletePhoto)
            }

            if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && !IdLogic.isNew(conditionId)) {
                Button(onClick = onAddPhotoClick, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("撮影")
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * [2-1-1] PhotoGrid (写真一覧：削除ボタンあり)
 * [2-2-1] PhotoGrid (写真一覧：閲覧・フルスクリーン遷移)
 */
@Composable
private fun PhotoGrid(
    photos: List<ConditionPhoto>,
    isEditable: Boolean,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current
    val rows = photos.chunked(3)
    Column(modifier = Modifier.fillMaxWidth().testTag("Condition_PhotoList"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowPhotos ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { photo ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                                contentDescription = photo.caption,
                                modifier = Modifier.fillMaxSize().clickable { onPhotoClick(photo) },
                                contentScale = ContentScale.Crop
                            )
                            if (isEditable) {
                                IconButton(
                                    onClick = { onDeletePhoto(photo) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = photo.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - rowPhotos.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * [2-2] ConditionRecordDisplayCard
 */
@Composable
private fun ConditionRecordDisplayCard(
    memo: ConditionAtVisit?,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
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
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // キーボード回避
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.offset(x = (-12).dp) // 左端に寄せるためのオフセットを追加
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                    }
                    Text(
                        text = "記録の詳細",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-8).dp) // アイコンに合わせてテキストも少し左へ
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "編集")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    memo?.let { m ->
                        Text(text = DateTimeUtils.formatRecordTime(m.recordTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!m.title.isNullOrBlank()) {
                            Text(text = m.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "記録者: ${m.author}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = ConditionDisplayMapper.getPhotoCountLabel(photos.size),
                    style = MaterialTheme.typography.titleMedium)

                Row {
                    // 迷子写真の再登録ボタン
                    if (orphanedPhotoCount > 0 && photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onReattachClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = "迷子写真を再登録",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT && memo != null) {
                        IconButton(onClick = onPickPhotoClick, enabled = !isProcessing) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = "ギャラリーから追加",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (photos.size < AppSpecifications.Condition.Photo.MAX_COUNT) {
                        IconButton(
                            onClick = onAddPhotoClick,
                            enabled = !isProcessing
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddAPhoto,
                                contentDescription = "写真を撮影",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            if (photos.isEmpty()) {
                Text("写真がありません", color = MaterialTheme.colorScheme.outline)
            } else {
                PhotoGrid(
                    photos = photos,
                    isEditable = false,
                    onPhotoClick = onPhotoClick,
                    onDeletePhoto = {}
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
        VerticalScrollIndicator(scrollState = scrollState)
    }
}

/**
 * [2-3] OrphanedPhotoSelectionDialog (迷子写真の再登録用ダイアログ)
 */
@Composable
private fun OrphanedPhotoSelectionDialog(
    orphanedPhotos: List<jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo>,
    onDismiss: () -> Unit,
    onSelect: (jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo) -> Unit
) {
    val context = LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text("迷子写真の再登録") },
        text = {
            AppDialogContent {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("再登録する写真を選択してください。", style = MaterialTheme.typography.bodySmall)

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
        confirmButton = {},
        dismissButton = {
            AppDialogDismissButton(text = "キャンセル", onClick = onDismiss)
        }
    )
}

////////////////////////////////////////////////////////////////////////////////////////////////////

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun PreviewConditionDetailPane() {
    MaterialTheme {
        ConditionDetailPane(
            conditionId = "1",
            records = listOf(ConditionAtVisit(id = "1", personId = "1", title = "サンプル", condition = "内容", author = "A", recordTime = Instant.now())),
            photos = emptyList(),
            isProcessing = false,
            defaultRecorderName = "A",
            onSaveRecord = { _, _, _ -> },
            onDeletePhoto = {},
            onSelectedIdChange = {},
            onCancel = {},
            onAddPhotoClick = {},
            onPickPhotoClick = {},
            onReattachPhoto = {},
            orphanedPhotos = emptyList(),
            onNavigateToFullScreen = { _, _ -> },
            onMicClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, name = "編集フォーム - 迷子写真あり")
@Composable
private fun PreviewConditionRecordEditFormDirect() {
    MaterialTheme {
        ConditionRecordEditForm(
            conditionId = "1",
            dateTimeState = rememberDateTimeInputState(),
            title = "サンプル所見",
            onTitleChange = {},
            author = "記録 太郎",
            onAuthorChange = {},
            condition = "経過良好です。",
            onConditionChange = {},
            photos = emptyList(),
            isProcessing = false,
            orphanedPhotoCount = 2, // 迷子写真あり
            onSave = {},
            onCancel = {},
            onAddPhotoClick = {},
            onPickPhotoClick = {},
            onReattachClick = {},
            onDeletePhoto = {},
            onMicClick = {},
            isChanged = false
        )
    }
}
