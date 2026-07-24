package jp.mydns.fujiwara.carememo.logic.feature

import android.net.Uri
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import java.time.Instant

/**
 * 所見メモ画面用の UI 状態
 */
data class PersonConditionUiState(
    // --- 入力フィールド (詳細パネル/編集フォーム用) ---
    val title: String = "",
    val condition: String = "",
    val author: String = "",
    val recordTime: Instant? = null,

    // --- 集約された状態 ---
    val personId: Int? = null,
    override val currentCategory: Category = Category.CONDITION_AT_VISIT,

    val records: List<ConditionAtVisit> = emptyList(),
    val filteredRecords: List<ConditionAtVisit> = emptyList(),
    val searchQuery: String = "",
    val selectedConditionId: Int? = null,
    val currentConditionPhotos: List<ConditionPhoto> = emptyList(),
    val conditionPhotoMap: Map<Int, Boolean> = emptyMap(),

    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 所見メモ画面固有のイベント
 */
sealed interface PersonConditionViewEvent {
    /** 写真撮影後のプレビュー遷移 (NAV-PC-002) */
    data class NavigateToPhotoPreview(val uri: Uri, val personId: Int, val conditionId: Int) : PersonConditionViewEvent
    /** 写真タップ時の全画面表示遷移 (NAV-PC-004) */
    data class NavigateToPhotoFullScreen(val photoId: Int, val conditionId: Int) : PersonConditionViewEvent
}

/**
 * 所見メモのバリデーション結果
 */
enum class PersonConditionValidationResult {
    SUCCESS,
    EMPTY_CONDITION,
    EMPTY_AUTHOR,
    INVALID_TIME,
    CONDITION_TOO_LONG,
    TITLE_TOO_LONG
}

/**
 * 所見メモ画面固有のドメインロジック
 */
object PersonConditionLogic {

    /**
     * 現在の入力内容が初期状態から変更されているかどうかを判定します。
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
     * 入力内容の妥当性を判定し、詳細な「事実」を返します。
     */
    fun validate(current: PersonConditionUiState): PersonConditionValidationResult {
        if (current.condition.isBlank()) return PersonConditionValidationResult.EMPTY_CONDITION
        if (current.author.isBlank()) return PersonConditionValidationResult.EMPTY_AUTHOR
        if (current.recordTime == null) return PersonConditionValidationResult.INVALID_TIME
        if (current.condition.length > AppSpecifications.Condition.Validation.MAX_LENGTH_MEMO) return PersonConditionValidationResult.CONDITION_TOO_LONG
        if (current.title.length > AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE) return PersonConditionValidationResult.TITLE_TOO_LONG

        return PersonConditionValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します（UI用）。
     */
    fun isValid(current: PersonConditionUiState): Boolean {
        return validate(current) == PersonConditionValidationResult.SUCCESS
    }

    /**
     * Entity を構築します。
     */
    fun createRecord(personId: Int, conditionId: Int, state: PersonConditionUiState): ConditionAtVisit {
        val time = state.recordTime ?: throw IllegalArgumentException("Invalid record time")
        return ConditionAtVisit(
            id = conditionId,
            personId = personId,
            title = state.title.trim(),
            condition = state.condition.trim(),
            author = state.author.trim(),
            recordTime = time
        )
    }
}
