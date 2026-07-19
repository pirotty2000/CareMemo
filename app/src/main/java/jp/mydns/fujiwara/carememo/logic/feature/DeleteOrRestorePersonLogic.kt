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
    // 現在は ViewModel 内のロジックがシンプルであるため、必要に応じてここに抽出する
}
