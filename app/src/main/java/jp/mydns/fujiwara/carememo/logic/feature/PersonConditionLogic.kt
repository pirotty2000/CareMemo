package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.util.UUID

/**
 * UI State：PersonConditionUiState
 *
 * 【役割】
 * 所見メモ（カテゴリB）画面における、すべての動的な表示状態を保持します。
 * 
 * @param screenState 構造的状態 (Loading / Active / Error)
 * @param operation 実行中の操作状態 (Idle / Refreshing / Saving / Deleting / PhotoProcessing)
 * @param personId 対象者のID
 * @param currentCategory 現在のカテゴリ（常に CONDITION_AT_VISIT）
 * @param records 全ての所見レコードリスト (Domain Content)
 * @param filteredRecords 検索キーワード等で絞り込まれたレコードリスト (Domain Content)
 * @param searchQuery 検索キーワード (UI Details)
 * @param editSession 編集セッションの詳細状態 (UI Content Details)
 */
@Immutable
data class PersonConditionUiState(
    val screenState: PersonConditionScreenState = PersonConditionScreenState.Loading,
    val operation: PersonConditionOperation = PersonConditionOperation.Idle,

    override val personId: String? = null,
    override val currentCategory: Category = Category.CONDITION_AT_VISIT,

    val records: ImmutableList<ConditionAtVisit> = persistentListOf(),
    val filteredRecords: ImmutableList<ConditionAtVisit> = persistentListOf(),
    val searchQuery: String = "",
    
    val editSession: ConditionEditSession = ConditionEditSession(),

    // 写真操作関連の一時状態 (UI Details)
    val initialPhotoId: String? = null,
    val previewUri: String? = null,
    val previewCaption: String = "",
    val currentConditionPhotos: ImmutableList<ConditionPhoto> = persistentListOf(),
    val conditionPhotoMap: Map<String, Boolean> = emptyMap(),
    val unassignedPhotoCount: Int = 0,
    val availableUnassignedPhotos: ImmutableList<UnassignedPhotoInfo> = persistentListOf(),

    @Deprecated("Use screenState and operation")
    override val isLoading: Boolean = false,
    @Deprecated("Use operation")
    val isProcessing: Boolean = false,
    @Deprecated("Use screenState")
    val errorMessage: String? = null
) : PersonAwareState

/**
 * 構造的状態 (Structural State)
 */
sealed interface PersonConditionScreenState {
    data object Loading : PersonConditionScreenState
    data object Active : PersonConditionScreenState
    data class Error(val throwable: Throwable) : PersonConditionScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface PersonConditionOperation {
    data object Idle : PersonConditionOperation
    data object Refreshing : PersonConditionOperation
    data object Saving : PersonConditionOperation
    data class Deleting(val recordId: String) : PersonConditionOperation
    data object PhotoProcessing : PersonConditionOperation
}

/**
 * 編集セッション状態 (UI Content Details)
 */
@Immutable
data class ConditionEditSession(
    val isEditing: Boolean = false,
    val selectedConditionId: String? = null,
    val editInput: ConditionEditInput = ConditionEditInput(),
    val initialRecordTime: Instant? = null,
    val initialSnapshot: ConditionEditInput? = null,
    val isChanged: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val fieldErrors: Map<String, Int?> = emptyMap(),
    val touchedFields: Set<String> = emptySet()
)

/**
 * UI Input：ConditionEditInput
 * 所見メモ編集画面の入力フォームの状態。
 */
@Immutable
data class ConditionEditInput(
    val title: String = "",
    val condition: String = "",
    val author: String = "",
    val recordTime: Instant? = Instant.now()
)

/**
 * View Event：PersonConditionViewEvent
 */
sealed interface PersonConditionViewEvent {
    /** 写真撮影の要求 */
    data class LaunchCamera(val photoFileName: String) : PersonConditionViewEvent
    /** 写真ギャラリー選択の要求 */
    data object OpenPhotoPicker : PersonConditionViewEvent
    /** 写真プレビュー画面への遷移要求 */
    data class NavigateToPhotoPreview(val uri: String, val conditionId: String) : PersonConditionViewEvent
    /** 写真全画面表示への遷移要求 */
    data class NavigateToPhotoFullScreen(val conditionId: String, val photoId: String) : PersonConditionViewEvent
}

/**
 * バリデーション結果
 */
enum class PersonConditionValidationResult {
    SUCCESS,
    EMPTY_CONDITION,
    EMPTY_AUTHOR,
    TITLE_TOO_LONG,
    CONDITION_TOO_LONG,
    INVALID_TIME
}

/**
 * Logic：PersonConditionLogic
 *
 * 【役割】
 * 所見メモに関連するビジネスロジック（バリデーション、変更検知、レコード構築）を提供します。
 *
 * 【設計指針：UI 境界の責務】
 * Logic レイヤーの純粋性を保つため、戻り値には標準の型を使用します。
 */
object PersonConditionLogic {

    fun validate(input: ConditionEditInput): PersonConditionValidationResult {
        val spec = AppSpecifications.Condition.Validation
        if (input.condition.isBlank()) return PersonConditionValidationResult.EMPTY_CONDITION
        if (input.author.isBlank()) return PersonConditionValidationResult.EMPTY_AUTHOR
        if (input.recordTime == null) return PersonConditionValidationResult.INVALID_TIME
        
        if (input.title.length > spec.MAX_LENGTH_TITLE) return PersonConditionValidationResult.TITLE_TOO_LONG
        if (input.condition.length > spec.MAX_LENGTH_MEMO) return PersonConditionValidationResult.CONDITION_TOO_LONG

        return PersonConditionValidationResult.SUCCESS
    }

    fun isValid(input: ConditionEditInput): Boolean {
        return validate(input) == PersonConditionValidationResult.SUCCESS
    }

    fun isChanged(input: ConditionEditInput, snapshot: ConditionEditInput?): Boolean {
        if (snapshot == null) return false
        return input != snapshot
    }

    fun createRecord(personId: String, id: String, input: ConditionEditInput): ConditionAtVisit {
        val recordTime = input.recordTime ?: throw IllegalArgumentException("Record time is required")
        val title = input.title.trim()
        val condition = input.condition.trim()
        val author = input.author.trim()

        val finalId = if (IdLogic.isNew(id)) UUID.randomUUID().toString() else id

        return ConditionAtVisit(
            id = finalId,
            personId = personId,
            title = title,
            condition = condition,
            author = author,
            recordTime = recordTime
        )
    }
}

/**
 * 未割り当て写真の情報。
 * データベースとの不整合（親記録の削除失敗やアプリの異常終了など）により、
 * 紐付けが失われたままストレージやDBに残っている写真を表します。
 */
@Immutable
data class UnassignedPhotoInfo(
    /** 未割り当ての発生原因/分類 */
    val type: UnassignedPhotoType,
    /** 写真ID（DBレコードが存在する場合のみ） */
    val photoId: String?,
    /** 利用者ID（DBレコードが存在する場合のみ） */
    val personId: String?,
    /** 物理ファイル名（画像本体） */
    val photoFileName: String,
    /** 物理ファイル名（サムネイル） */
    val thumbnailFileName: String?,
    /** 撮影日時（ファイル更新日時またはレコード記録日時） */
    val capturedAt: Instant,
    /** UIに表示する説明文のリソースID */
    val descriptionResId: Int
)

/**
 * 未割り当て写真の分類。
 */
enum class UnassignedPhotoType {
    /** DBレコードはあるが、親の所見メモ（condition_id）が空（一時保存のまま放置） */
    TEMPORARY,
    /** DBレコードはあるが、紐付け先の所見メモが既に存在しない（整合性エラー） */
    UNASSIGNED_RECORD,
    /** 物理ファイルはあるが、DBレコードが存在しない（未登録ファイル） */
    FILE_ONLY
}
