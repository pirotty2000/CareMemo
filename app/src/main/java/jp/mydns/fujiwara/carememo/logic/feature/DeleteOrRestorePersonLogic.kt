package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel

/**
 * 利用者復帰・抹消画面全体の表示状態
 */
data class DeleteOrRestorePersonUiState(
    val isLoading: Boolean = false,
    val mode: DeleteOrRestorePersonViewModel.OperationMode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
    val archivedPersons: List<Person> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val isNameMaskingEnabled: Boolean = true
)

/**
 * 利用者復帰・抹消画面固有のイベント
 */
sealed interface DeleteOrRestorePersonViewEvent {
    // 将来的な拡張用
}

/**
 * 利用者復帰・抹消画面に関するドメインロジック。
 */
object DeleteOrRestorePersonLogic {

    /**
     * 利用者の選択状態を切り替えます。
     *
     * @param currentIds 現在選択されている ID のセット
     * @param personId 切り替え対象の利用者 ID
     * @return 新しい選択 ID のセット
     */
    fun toggleSelection(currentIds: Set<Int>, personId: Int): Set<Int> {
        return if (currentIds.contains(personId)) {
            currentIds - personId
        } else {
            currentIds + personId
        }
    }

    /**
     * リスト内のすべての利用者を全選択した状態の ID セットを生成します。
     *
     * @param persons 利用者リスト
     * @return すべての ID を含むセット
     */
    fun selectAll(persons: List<Person>): Set<Int> {
        return persons.map { it.id }.toSet()
    }

    /**
     * 現在の選択状態に基づき、処理（復帰・抹消）の対象となる利用者を抽出します。
     *
     * @param persons 全利用者リスト
     * @param selectedIds 選択されている ID のセット
     * @return 処理対象の利用者リスト
     */
    fun filterTargets(persons: List<Person>, selectedIds: Set<Int>): List<Person> {
        return persons.filter { selectedIds.contains(it.id) }
    }

    /**
     * バリデーション結果を示す Enum
     */
    enum class DeleteOrRestoreValidationResult {
        SUCCESS,
        NO_SELECTION
    }

    /**
     * 選択状態のバリデーションを行います。
     *
     * @param selectedIds 選択されている ID のセット
     * @return バリデーション結果
     */
    fun validate(selectedIds: Set<Int>): DeleteOrRestoreValidationResult {
        return if (selectedIds.isEmpty()) {
            DeleteOrRestoreValidationResult.NO_SELECTION
        } else {
            DeleteOrRestoreValidationResult.SUCCESS
        }
    }
}
