package jp.mydns.fujiwara.carememo.logic.feature

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
     * 保存可能かどうかを判定します。
     */
    fun isValid(current: PersonConditionUiState): Boolean {
        return current.author.isNotBlank() && 
                current.condition.isNotBlank() && 
                current.recordTime != null
    }

    /**
     * UI状態から保存用の ConditionAtVisit Entity を構築します。
     */
    fun createRecord(personId: Int, conditionId: Int, state: PersonConditionUiState): ConditionAtVisit? {
        val time = state.recordTime ?: return null
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
