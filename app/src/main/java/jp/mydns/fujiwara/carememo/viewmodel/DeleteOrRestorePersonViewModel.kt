package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonLogic
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonUiState
import jp.mydns.fujiwara.carememo.logic.feature.DeleteOrRestorePersonViewEvent
import kotlinx.coroutines.launch

/**
 * ViewModel：DeleteOrRestorePersonViewModel
 *
 * 【役割】
 * 利用終了者（アーカイブ）の管理画面（SCR-S-003）における状態管理と実行制御を担当します。
 * データの「復帰（論理削除解除）」と「完全抹消（物理削除）」という、性質の異なる2つの操作を安全に切り替えて実行します。
 *
 * 【主要な機能】
 * ・アーカイブ済み利用者一覧の購読と UI 状態への反映。
 * ・操作モード（復帰 / 抹消）の切り替え管理。
 * ・複数利用者の選択（チェックボックス）管理。
 * ・選択された利用者に対する一括復帰処理、または一括抹消処理の実行。
 *
 * 【依存している Repository】
 * ・DeleteOrRestorePersonRepository: アーカイブデータの取得、復帰・抹消の実行。
 * ・UserSettingsRepository: 共通設定（氏名のマスキング等）の参照。
 * ・AuditLogRepository: 破壊的・重要な操作の証跡記録。
 *
 * 【依存している Logic】
 * ・DeleteOrRestorePersonLogic: 選択状態の管理、対象の抽出、バリデーション。
 *
 * 【設計指針】
 * 1. 視覚的区別：モード（RESTORE / DELETE）を明確に定義し、UI 側での警告表示や配色変更の根拠とする。
 * 2. 破壊的変更の保護：抹消操作においてはバリデーションを厳格に行い、不完全な選択状態での実行を防止する。
 * 3. 透明性：すべての復帰・抹消操作は、個別の利用者 ID とともに監査ログに記録し、後からの証跡確認を可能にする。
 */
class DeleteOrRestorePersonViewModel(
    private val repository: DeleteOrRestorePersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<DeleteOrRestorePersonUiState, DeleteOrRestorePersonViewEvent>(
    userSettingsRepository,
    DeleteOrRestorePersonUiState()
) {

    companion object {
        /** 監査ログ用機能名 */
        private const val FEATURE_NAME = "DeleteOrRestorePerson"
        /** 監査ログ用操作名：一括復帰 */
        private const val OP_RESTORE = "restoreSelectedPersons"
        /** 監査ログ用操作名：一括抹消 */
        private const val OP_DELETE = "deleteSelectedPersons"
        /** 監査ログ用対象テーブル */
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    /**
     * アーカイブ管理の操作モード定義。
     */
    enum class OperationMode {
        /** 復帰モード：アーカイブからアクティブ一覧に戻す */
        RESTORE,
        /** 完全抹消モード：データベースから物理削除する（復元不可） */
        DELETE
    }

    init {
        // (B)系統標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定（氏名マスキング）の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // アーカイブ済み利用者リストの継続的な購読
        safeCollect(
            operation = "archivedPersonListFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_PERSON },
            flowProvider = { repository.getArchivedPersons() }
        ) { newList ->
            updateUiState { it.copy(archivedPersons = newList) }
        }
    }

    override fun copyWithLoadingState(state: DeleteOrRestorePersonUiState, isLoading: Boolean): DeleteOrRestorePersonUiState {
        return state.copy(isLoading = isLoading)
    }

    /**
     * 操作モードを設定します。
     * 誤操作防止のため、モード変更時には現在の選択状態をクリアします。
     *
     * @param newMode 設定するモード (RESTORE または DELETE)
     */
    fun setMode(newMode: OperationMode) {
        updateUiState { 
            it.copy(
                mode = newMode,
                selectedIds = emptySet()
            )
        }
    }

    /**
     * 指定された利用者の選択状態を反転（選択 ↔ 未選択）させます。
     *
     * @param personId 利用者ID
     */
    fun toggleSelection(personId: String) {
        updateUiState { current ->
            val nextIds = DeleteOrRestorePersonLogic.toggleSelection(current.selectedIds, personId)
            current.copy(selectedIds = nextIds)
        }
    }

    /**
     * 表示されているすべての利用者を一括選択します。
     * ※ 誤操作による全抹消を防ぐため、RESTORE モード時のみ動作します。
     *
     * @param persons 対象の利用者リスト
     */
    fun selectAll(persons: List<Person>) {
        // DELETE モード時は全選択を許可しない（UI 側の制限とあわせた ViewModel 側でのガード）
        if (currentState.mode == OperationMode.DELETE) return

        updateUiState { current -> 
            current.copy(selectedIds = DeleteOrRestorePersonLogic.selectAll(persons))
        }
    }

    /**
     * 現在の選択状態をすべて解除します。
     */
    fun clearSelection() {
        updateUiState { it.copy(selectedIds = emptySet()) }
    }

    /**
     * 選択された利用者をアクティブ（一覧）に復元します。
     * 内部でカスケード復元（関連する健康記録、所見メモ、服薬記録の復旧）が行われます。
     *
     * @param persons 画面に表示されている全アーカイブ利用者リスト
     */
    fun restoreSelectedPersons(persons: List<Person>) {
        safeLaunch(
            operation = OP_RESTORE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // 1. バリデーション：1名以上選択されているか
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(
                    messageResId = R.string.archive_err_no_selection,
                    logMessage = "No persons selected for restore"
                )
            }

            // 2. 実行：対象を抽出し、リポジトリ経由で復元
            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            targets.forEach { 
                repository.restorePerson(it.id, featureName, OP_RESTORE) 
            }
            
            // 3. 通知とクリーンアップ
            showSnackbar(R.string.archive_msg_restored, targets.size)
            clearSelection()
        }
    }

    /**
     * 選択された利用者を完全に抹消（物理削除）します。
     * 内部でカスケード物理削除が行われます。この操作は取り消せません。
     *
     * @param persons 画面に表示されている全アーカイブ利用者リスト
     */
    fun deleteSelectedPersons(persons: List<Person>) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_PERSON
            }
        ) {
            // 1. バリデーション：1名以上選択されているか
            val validationResult = DeleteOrRestorePersonLogic.validate(currentState.selectedIds)
            if (validationResult != DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS) {
                throw AppValidationException(
                    messageResId = R.string.archive_err_no_selection,
                    logMessage = "No persons selected for delete"
                )
            }

            // 2. 実行：対象を抽出し、リポジトリ経由で物理削除（抹消）
            val targets = DeleteOrRestorePersonLogic.filterTargets(persons, currentState.selectedIds)
            targets.forEach { 
                repository.permanentlyDeletePerson(it.id, featureName, OP_DELETE)
            }
            
            // 3. 通知とクリーンアップ
            showSnackbar(R.string.archive_msg_deleted, targets.size)
            clearSelection()
        }
    }

    /**
     * DeleteOrRestorePersonViewModel 生成用の Factory クラス。
     */
    class Factory(
        private val repository: DeleteOrRestorePersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeleteOrRestorePersonViewModel(repository, userSettingsRepository, auditLogRepository) as T
        }
    }
}
