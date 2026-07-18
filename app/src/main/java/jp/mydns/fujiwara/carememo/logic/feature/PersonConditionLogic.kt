package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import java.time.Instant

/**
 * 所見メモ画面用の UI 状態
 */
data class PersonConditionUiState(
    val title: String = "",
    val condition: String = "",
    val author: String = "",
    val recordTime: Instant? = null
)

/**
 * 所見メモのバリデーション結果
 */
enum class PersonConditionValidationResult {
    SUCCESS,
    EMPTY_CONDITION,
    EMPTY_AUTHOR,
    INVALID_TIME,
    CONDITION_TOO_LONG
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
        if (current.condition.length > AppThresholds.CONDITION_MAX_LENGTH) return PersonConditionValidationResult.CONDITION_TOO_LONG

        return PersonConditionValidationResult.SUCCESS
    }

    /**
     * 保存可能かどうかを判定します（UI用）。
     */
    fun isValid(current: PersonConditionUiState): Boolean {
        return validate(current) == PersonConditionValidationResult.SUCCESS
    }

    /**
     * UI状態から保存用の ConditionAtVisit Entity を構築します。
     * バリデーションに失敗している場合は例外をスローします。
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
