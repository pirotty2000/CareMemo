package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * UI State：DeleteOrRestorePersonUiState
 *
 * 【役割】
 * 利用者の復帰・抹消画面（DeleteOrRestorePersonScreen）全体の表示状態を保持します。
 *
 * @param screenState 構造的状態 (Loading / Active / Error)
 * @param operation 実行中の操作状態 (Idle / Restoring / Deleting)
 * @param mode 現在の操作モード（復帰：RESTORE / 抹消：DELETE）
 * @param archivedPersons アーカイブ（利用終了）された利用者のリスト (Domain Content)
 * @param selectedIds 現在チェックボックスで選択されている利用者のIDセット (UI Details)
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）が有効か
 */
@Immutable
data class DeleteOrRestorePersonUiState(
    val screenState: DeleteOrRestorePersonScreenState = DeleteOrRestorePersonScreenState.Loading,
    val operation: DeleteOrRestorePersonOperation = DeleteOrRestorePersonOperation.Idle,

    val mode: DeleteOrRestorePersonViewModel.OperationMode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE,
    val archivedPersons: ImmutableList<Person> = persistentListOf(),
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    val isNameMaskingEnabled: Boolean = true,

    @Deprecated("Use screenState")
    val isLoading: Boolean = false
)

/**
 * 構造的状態 (Structural State)
 */
sealed interface DeleteOrRestorePersonScreenState {
    data object Loading : DeleteOrRestorePersonScreenState
    data object Active : DeleteOrRestorePersonScreenState
    data class Error(val throwable: Throwable) : DeleteOrRestorePersonScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface DeleteOrRestorePersonOperation {
    data object Idle : DeleteOrRestorePersonOperation
    data object Restoring : DeleteOrRestorePersonOperation
    data object Deleting : DeleteOrRestorePersonOperation
}

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
     * 【設計意図】
     * UI 境界の外側である Logic クラスの戻り値には、標準の [List] を使用します。
     *
     * @param persons 全利用者リスト
     * @param selectedIds 選択されている ID のセット
     * @return 処理対象 Paige 利用者リスト
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
