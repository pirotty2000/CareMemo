package jp.mydns.fujiwara.carememo.logic.feature

import android.net.Uri
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant

/**
 * UI State：PersonConditionUiState
 *
 * 【役割】
 * 所見メモ（カテゴリB）画面における、すべての動的な表示状態を保持します。
 * フォームの入力値、DBから取得したレコードリスト、検索クエリ、および「迷子写真」の管理情報を含みます。
 *
 * @param title 入力中のタイトル
 * @param condition 入力中の所見本文
 * @param author 入力中の記録者名
 * @param recordTime 記録日時
 * @param personId 対象者のID
 * @param currentCategory 現在のカテゴリ（常に CONDITION_AT_VISIT）
 * @param records 全ての所見レコードリスト
 * @param filteredRecords 検索キーワード等で絞り込まれたレコードリスト
 * @param searchQuery 検索キーワード
 * @param selectedConditionId 現在選択（閲覧・編集）されているレコードのID
 * @param currentConditionPhotos 選択されたレコードに紐付く写真リスト
 * @param conditionPhotoMap レコードIDごとの写真有無マップ（履歴リストのアイコン表示に使用）
 * @param orphanedPhotoCount 再紐付け可能な「迷子写真」の総数
 * @param availableOrphanedPhotos 再紐付け可能な迷子写真情報のリスト
 * @param isProcessing 保存や削除などの非同期処理中フラグ
 * @param errorMessage エラーメッセージ
 * @param isLoading 初期読み込み中フラグ
 */
data class PersonConditionUiState(
    // --- 入力フィールド (詳細パネル/編集フォーム用) ---
    val title: String = "",
    val condition: String = "",
    val author: String = "",
    val recordTime: Instant? = null,

    // --- 集約された状態 ---
    override val personId: String? = null,
    override val currentCategory: Category = Category.CONDITION_AT_VISIT,

    val records: List<ConditionAtVisit> = emptyList(),
    val filteredRecords: List<ConditionAtVisit> = emptyList(),
    val searchQuery: String = "",
    val selectedConditionId: String? = null,
    val initialPhotoId: String? = null,
    val previewUri: String? = null,
    val currentConditionPhotos: List<ConditionPhoto> = emptyList(),
    val conditionPhotoMap: Map<String, Boolean> = emptyMap(),
    val orphanedPhotoCount: Int = 0,
    val availableOrphanedPhotos: List<OrphanedPhotoInfo> = emptyList(),

    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 所見メモ画面固有のナビゲーションイベント。
 */
sealed interface PersonConditionViewEvent {
    /** 写真撮影後のプレビュー遷移 */
    data class NavigateToPhotoPreview(val uri: Uri, val personId: String, val conditionId: String) : PersonConditionViewEvent
    /** 写真タップ時の全画面表示遷移 */
    data class NavigateToPhotoFullScreen(val photoId: String, val conditionId: String) : PersonConditionViewEvent
    /** 一覧画面へ戻る */
    object NavigateBackToMain : PersonConditionViewEvent
}

/**
 * 所見メモのバリデーション結果（事実）。
 */
enum class PersonConditionValidationResult {
    /** バリデーション成功 */
    SUCCESS,
    /** 所見本文が未入力（必須） */
    EMPTY_CONDITION,
    /** 記録者名が未入力（必須） */
    EMPTY_AUTHOR,
    /** 記録日時が不正 */
    INVALID_TIME,
    /** 本文が制限文字数（AppSpecifications 参照）を超過 */
    CONDITION_TOO_LONG,
    /** タイトルが制限文字数（AppSpecifications 参照）を超過 */
    TITLE_TOO_LONG
}

/**
 * Logic：PersonConditionLogic
 *
 * 【役割】
 * 所見記録画面（カテゴリB）における状態判定、バリデーション、および Entity 生成のドメインロジックを提供します。
 *
 * 【主な機能】
 * ・入力内容の変更検知（初期状態との比較による「破棄確認ダイアログ」の制御）。
 * ・保存前バリデーション（必須項目・文字数制限）。
 * ・UI状態（UiState）から永続化用エンティティ（ConditionAtVisit）への変換。
 *
 * 【設計指針】
 * 1. ビジネスルール（必須チェック等）と、システム制約（文字数制限等）を統合してバリデーションを行う。
 * 2. 変更検知は、新規作成時（initial=null）と編集時で期待される「初期値（デフォルト記録者等）」が異なることを考慮する。
 * 3. データの正規化（trim）は、保存の直前であるこのレイヤーで責任を持って行う。
 */
object PersonConditionLogic {

    /**
     * 現在の入力内容が初期状態から変更されているかどうかを判定します。
     *
     * @param current 現在のUI状態
     * @param initial DBから取得された元のレコード情報（新規時は null）
     * @param defaultAuthor デフォルトの記録者名
     * @return 変更がある場合は true（1箇所でも異なれば変更ありとみなす）
     */
    fun isChanged(current: PersonConditionUiState, initial: ConditionAtVisit?, defaultAuthor: String): Boolean {
        val initialTitle = initial?.title ?: ""
        val initialCondition = initial?.condition ?: ""
        val initialAuthor = initial?.author ?: defaultAuthor
        val initialTime = initial?.recordTime

        return current.title != initialTitle ||
                current.condition != initialCondition ||
                current.author != initialAuthor ||
                current.recordTime != initialTime
    }

    /**
     * 入力内容の妥当性を詳細に判定します。
     *
     * @param current 検証対象のUI状態
     * @return 判定結果（SUCCESS 以外はエラー原因を特定可能）
     */
    fun validate(current: PersonConditionUiState): PersonConditionValidationResult {
        // 必須チェック
        if (current.condition.isBlank()) return PersonConditionValidationResult.EMPTY_CONDITION
        if (current.author.isBlank()) return PersonConditionValidationResult.EMPTY_AUTHOR
        if (current.recordTime == null) return PersonConditionValidationResult.INVALID_TIME
        
        // 文字数制限チェック（AppSpecifications を参照）
        val spec = AppSpecifications.Condition.Validation
        if (current.condition.length > spec.MAX_LENGTH_MEMO) return PersonConditionValidationResult.CONDITION_TOO_LONG
        if (current.title.length > spec.MAX_LENGTH_TITLE) return PersonConditionValidationResult.TITLE_TOO_LONG

        return PersonConditionValidationResult.SUCCESS
    }

    /**
     * 保存ボタンを活性化して良いかどうかを判定します。
     *
     * @param current 現在のUI状態
     * @return バリデーションを通過している場合は true
     */
    fun isValid(current: PersonConditionUiState): Boolean {
        return validate(current) == PersonConditionValidationResult.SUCCESS
    }

    /**
     * UI状態と指定されたIDに基づき、DB保存用の Entity (ConditionAtVisit) を構築します。
     *
     * @param personId 利用者ID
     * @param conditionId レコードID（新規なら新規用定数、既存ならそのIDを維持）
     * @param state 現在のUI状態
     * @return 構築および正規化済みの ConditionAtVisit インスタンス
     */
    fun createRecord(personId: String, conditionId: String, state: PersonConditionUiState): ConditionAtVisit {
        val time = state.recordTime ?: throw IllegalArgumentException("Invalid record time")
        
        // 新規作成時のみ新しい UUID を発行する。既存編集時はIDを維持。
        val finalId = if (IdLogic.isNew(conditionId)) java.util.UUID.randomUUID().toString() else conditionId
        
        return ConditionAtVisit(
            id = finalId,
            personId = personId,
            title = state.title.trim(),
            condition = state.condition.trim(),
            author = state.author.trim(),
            recordTime = time
        )
    }
}

/**
 * 迷子写真の情報。
 * データベースとの不整合（親記録の削除失敗やアプリの異常終了など）により、
 * 紐付けが失われたままストレージやDBに残っている写真を表します。
 */
data class OrphanedPhotoInfo(
    /** 迷子の発生原因/分類 */
    val type: OrphanedPhotoType,
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
    /** UIに表示する説明文（「一時保存中の写真」などの補足） */
    val description: String
)

/**
 * 迷子写真の分類。
 */
enum class OrphanedPhotoType {
    /** DBレコードはあるが、親の所見メモ（condition_id）が空（一時保存のまま放置） */
    TEMPORARY,
    /** DBレコードはあるが、紐付け先の所見メモが既に存在しない（整合性エラー） */
    ORPHANED_RECORD,
    /** 物理ファイルはあるが、DBレコードが存在しない（未登録ファイル） */
    FILE_ONLY
}
