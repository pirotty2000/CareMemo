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
 * 最終更新日: 2026/07/04
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils.formatTime
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import jp.mydns.fujiwara.carememo.ui.components.base.DeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.LoadingScreen
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputFields
import jp.mydns.fujiwara.carememo.ui.components.common.DateTimeInputState
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHistoryList
import jp.mydns.fujiwara.carememo.ui.components.common.rememberDateTimeInputState

/**
 * 所見メモの一覧表示 (リストアイテム用)
 */
@Composable
private fun ConditionMemoContent(record: ConditionAtVisit, hasPhoto: Boolean) {
    Column {
        // 所見メモタイトル
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
        // 所見メモ本体(最大maxLines行表示)
        Text(
            text = record.condition ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 記録者
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhoto) {
                // 写真があれば、記録者の左にカメラアイコン
                Icon(
                    imageVector = Icons.Rounded.AddAPhoto,
                    contentDescription = "写真あり",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            // 記録者：○○○
            Text(
                text = "記録者: ${record.author}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * 所見メモ用の履歴リスト。
 * 汎用的な [PersonHistoryList] を使用して、時系列の記録一覧を表示する。
 */
@Composable
fun ConditionList(
    records: List<Any>,
    selectedId: Int,
    conditionPhotoMap: Map<Int, Boolean>,
    onSelect: (Int) -> Unit,
    onDelete: (HistoryRecord) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    PersonHistoryList(
        records = records.filterIsInstance<HistoryRecord>(),
        selectedRecordId = selectedId,
        onItemClick = { onSelect(it.id) },
        onDeleteSwipe = onDelete,
        isAnyDialogOpen = false,
        lazyListState = lazyListState
    ) { record ->
        (record as? ConditionAtVisit)?.let {
            ConditionMemoContent(it, conditionPhotoMap[it.id] == true)
        }
    }
}

/**
 * 所見メモ詳細ペイン。
 * 選択された記録のIDに応じて「詳細表示」と「編集・新規作成フォーム」を切り替えて管理する。
 */
@Composable
fun ConditionDetailPane(
    personId: Int,
    conditionId: Int,
    records: List<ConditionAtVisit>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    defaultRecorderName: String,
    onSaveRecord: (ConditionAtVisit, (Int) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onSelectedIdChange: (Int) -> Unit,
    onAddPhotoClick: () -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    val memo = remember(records, conditionId) {
        records.find { it.id == conditionId }
    }

    // 記録が見つからない場合の待機（新規作成時は除く）
    if (memo == null && conditionId > 0) {
        LoadingScreen()
        return
    }

    // 状態の初期化を remember(conditionId) に集約してブランキングを抑制
    var isEditing by remember(conditionId) { mutableStateOf(conditionId == 0) }
    val dateTimeState = rememberDateTimeInputState(initialInstant = memo?.recordTime)
    
    var title by remember(conditionId) { mutableStateOf(memo?.title ?: "") }
    var condition by remember(conditionId) { mutableStateOf(memo?.condition ?: "") }
    var author by remember(conditionId) { 
        mutableStateOf(memo?.author ?: defaultRecorderName) 
    }

    // デフォルトの記録者が変更された場合の補完（新規作成時のみ）
    LaunchedEffect(defaultRecorderName) {
        if (conditionId == 0 && author.isBlank()) {
            author = defaultRecorderName
        }
    }

    var photoToDelete by remember { mutableStateOf<ConditionPhoto?>(null) }

    val isDateTimeValid by remember(dateTimeState) {
        derivedStateOf { dateTimeState.toInstant() != null }
    }

    // 所見メモが一つもない場合の初期表示
    if (conditionId == -1) {
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
         // ---------- 所見メモ・編集画面 ----------
         ConditionRecordEditForm(
            personId = personId,
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
            isDateTimeValid = isDateTimeValid,
            onSave = {
                dateTimeState.toInstant()?.let { recordTime ->
                    val newMemo = ConditionAtVisit(
                        id = conditionId,
                        personId = personId,
                        title = title,
                        condition = condition,
                        author = author,
                        recordTime = recordTime
                    )
                    onSaveRecord(newMemo) { newId ->
                        onSelectedIdChange(newId)
                        isEditing = false
                    }
                }
            },
            onCancel = { if (conditionId != 0) isEditing = false },
            onAddPhotoClick = onAddPhotoClick,
            onDeletePhoto = { photoToDelete = it }
        )
    } else {
        // ---------- 所見メモ・履歴一覧 ----------
        ConditionRecordDisplayCard(
            memo = memo,
            photos = photos,
            isProcessing = isProcessing,
            onEditClick = { isEditing = true },
            onPhotoClick = { onNavigateToFullScreen(it.photoFileName, it.caption) },
            onAddPhotoClick = onAddPhotoClick
        )
    }

    // 写真を削除する操作をしたときのダイアログ表示
    if (photoToDelete != null) {
        DeleteConfirmDialog(
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
 * 登録済みの所見内容を表示する詳細カード。
 * テキスト情報に加え、写真セクションのタイトル横にあるカメラアイコンから直接撮影が可能。
 */
@Composable
private fun ConditionRecordDisplayCard(
    memo: ConditionAtVisit?,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    onEditClick: () -> Unit,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onAddPhotoClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------- 記録の詳細・ヘッダー ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---------- 記録の詳細・タイトル ----------
            Text(
                text = "記録の詳細",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            // ---------- 記録の詳細・鉛筆アイコン ----------
            IconButton(onClick = onEditClick) {
                Icon(Icons.Rounded.EditNote, contentDescription = "編集")
            }
        }

        // ---------- 記録の詳細・本体 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                memo?.let { m ->
                    // 時刻
                    Text(text = formatTime(m.recordTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    // タイトル
                    if (!m.title.isNullOrBlank()) {
                        Text(text = m.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    // 所見メモの本文
                    Text(text = m.condition ?: "", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    // 記録者
                    Text(text = "記録者: ${m.author}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.End),
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        // ---------- 写真 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 写真（○/○）
            Text(text = "写真 (${photos.size}/${AppThresholds.CONDITION_PHOTO_MAX_COUNT})",
                style = MaterialTheme.typography.titleMedium)
            // 写真がまだ撮れる時、カメラアイコンを表示して撮影できるようにする
            if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT) {
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
        if (photos.isEmpty()) {
            Text("写真はありません", color = MaterialTheme.colorScheme.outline)
        } else {
            PhotoGrid(
                photos = photos,
                isEditable = false,
                onPhotoClick = onPhotoClick,
                onDeletePhoto = {}
            )
        }
    }
}

/**
 * 所見内容を入力・編集するためのフォーム。
 * 新規作成時は「写真は保存後に...」のガイドを表示し、既存編集時は直接撮影・削除が可能。
 */
@Composable
private fun ConditionRecordEditForm(
    personId: Int,
    conditionId: Int,
    dateTimeState: DateTimeInputState,
    title: String,
    onTitleChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    condition: String,
    onConditionChange: (String) -> Unit,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isDateTimeValid: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (conditionId == 0) "新規作成" else "記録の編集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeInputFields(state = dateTimeState)
                HorizontalDivider(thickness = 0.5.dp)
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("タイトル (任意)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = onAuthorChange,
                    label = { Text("記録者") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = condition,
                    onValueChange = onConditionChange,
                    label = { Text("所見メモ") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = conditionId != 0
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = author.isNotBlank() && condition.isNotBlank() && isDateTimeValid
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "写真 (${photos.size}/${AppThresholds.CONDITION_PHOTO_MAX_COUNT})", style = MaterialTheme.typography.titleMedium)
            
            if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT && conditionId != 0) {
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
        if (conditionId == 0) {
            Text(
                text = stringResource(R.string.condition_photo_add_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (photos.isEmpty()) {
            Text("写真はありません", color = MaterialTheme.colorScheme.outline)
        } else {
            PhotoGrid(
                photos = photos,
                isEditable = true,
                onPhotoClick = {},
                onDeletePhoto = onDeletePhoto
            )
        }

        if (photos.size < AppThresholds.CONDITION_PHOTO_MAX_COUNT && conditionId != 0) {
            Button(
                onClick = onAddPhotoClick,
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("撮影")
            }
        }
    }
}

/**
 * 撮影された写真のサムネイルを最大 [AppThresholds.CONDITION_PHOTO_MAX_COUNT] 枚まで横並びで表示するグリッド。
 * 編集モード時は写真右上のゴミ箱アイコンから削除操作が可能。
 */
@Composable
private fun PhotoGrid(
    photos: List<ConditionPhoto>,
    isEditable: Boolean,
    onPhotoClick: (ConditionPhoto) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
) {
    val context = LocalContext.current
    // 3列ずつのチャンクに分割
    val rows = photos.chunked(3)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowPhotos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPhotos.forEach { photo ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            AsyncImage(
                                model = ImageUtils.getPhotoFile(context, photo.thumbnailFileName),
                                contentDescription = photo.caption,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onPhotoClick(photo) },
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
                        Text(
                            text = photo.caption,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 3枚に満たない行の空きスペースを埋めて、サイズを一定に保つ
                repeat(3 - rowPhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewConditionDetailPane() {
    MaterialTheme {
        ConditionDetailPane(
            personId = 1,
            conditionId = 1,
            records = listOf(
                ConditionAtVisit(
                    id = 1,
                    personId = 1,
                    title = "サンプルタイトル",
                    condition = "サンプルの所見内容です。",
                    author = "記録者A",
                    recordTime = Instant.now()
                )
            ),
            photos = emptyList(),
            isProcessing = false,
            defaultRecorderName = "デフォルト記録者",
            onSaveRecord = { _, _ -> },
            onDeletePhoto = {},
            onSelectedIdChange = {},
            onAddPhotoClick = {},
            onNavigateToFullScreen = { _, _ -> }
        )
    }
}
