package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel

/**
 * UI State：DeleteOrRestorePersonUiState
 *
 * 【役割】
 * 利用者の復帰・抹消画面（DeleteOrRestorePersonScreen）全体の表示状態を保持します。
 *
 * @param isLoading 全体の読み込み中フラグ
 * @param mode 現在の操作モード（復帰：RESTORE / 抹消：DELETE）
 * @param archivedPersons アーカイブ（利用終了）された利用者のリスト
 * @param selectedIds 現在チェックボックスで選択されている利用者のIDセット
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 */
data class DeleteOrRestorePersonUiState(
    val isLoading: Boolean = false,
    val mode: DeleteOrRestorePersonViewModel.OperationMode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
    val archivedPersons: List<Person> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isNameMaskingEnabled: Boolean = true
)

/**
 * View Event：DeleteOrRestorePersonViewEvent
 *
 * 【役割】
 * 利用者復帰・抹消画面において、一過性のイベント（画面終了等）を定義します。
 */
sealed interface DeleteOrRestorePersonViewEvent {
    /** 処理完了後の画面終了を要求 */
    data object Finish : DeleteOrRestorePersonViewEvent
    /** 前の画面に戻る */
    data object NavigateBack : DeleteOrRestorePersonViewEvent
}

/**
 * Logic：DeleteOrRestorePersonLogic
 *
 * 【役割】
 * 利用者復帰・抹消画面に関するドメインロジック（選択状態の管理、フィルタリング、バリデーション）を提供します。
 */
object DeleteOrRestorePersonLogic {

    /**
     * 利用者の選択状態を切り替えます。
     *
     * @param currentIds 現在選択されている ID のセット
     * @param personId 切り替え対象の利用者 ID
     * @return 新しい選択 ID のセット
     */
    fun toggleSelection(currentIds: Set<String>, personId: String): Set<String> {
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
    fun selectAll(persons: List<Person>): Set<String> {
        return persons.map { it.id }.toSet()
    }

    /**
     * 現在の選択状態に基づき、処理（復帰・抹消）の対象となる利用者を抽出します。
     *
     * @param persons 全利用者リスト
     * @param selectedIds 選択されている ID のセット
     * @return 処理対象の利用者リスト
     */
    fun filterTargets(persons: List<Person>, selectedIds: Set<String>): List<Person> {
        return persons.filter { selectedIds.contains(it.id) }
    }

    /**
     * バリデーション結果を示す Enum。
     */
    enum class DeleteOrRestoreValidationResult {
        /** 正常 */
        SUCCESS,
        /** 選択なし */
        NO_SELECTION
    }

    /**
     * 選択状態のバリデーションを行います。
     *
     * @param selectedIds 選択されている ID のセット
     * @return バリデーション結果
     */
    fun validate(selectedIds: Set<String>): DeleteOrRestoreValidationResult {
        return if (selectedIds.isEmpty()) {
            DeleteOrRestoreValidationResult.NO_SELECTION
        } else {
            DeleteOrRestoreValidationResult.SUCCESS
        }
    }
}
