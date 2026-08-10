package jp.mydns.fujiwara.carememo.ui.preview

import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 健康記録画面のプレビュー用状態
 */
data class PersonHealthPreviewState(
    val category: Category = Category.BP_AND_PULSE,
    val records: ImmutableList<HistoryRecord> = persistentListOf(),
    val isLoading: Boolean = false,
    val person: Person? = MockData.person,
    val summary: PersonCategorySummary? = null,
    val selectedRecordId: String? = null,
    val preferredShowHistory: Boolean = true
)

/**
 * 所見メモ画面のプレビュー用状態
 */
data class PersonConditionPreviewState(
    val records: ImmutableList<jp.mydns.fujiwara.carememo.data.ConditionAtVisit> = persistentListOf(),
    val isLoading: Boolean = false,
    val selectedRecordId: String? = null,
    val isExpanded: Boolean = false
)
