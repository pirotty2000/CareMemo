package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.EmergencyContactLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI State：EmergencyContactUiState
 *
 * 【役割】
 * 緊急連絡先管理画面（一覧・編集）の表示状態を保持します。
 *
 * @param isLoading 全体の読み込み中フラグ
 * @param contacts 利用者に紐付く全連絡先のリスト
 * @param editingContact 現在編集中の連絡先情報（新規作成時も含む）
 * @param initialContact 編集開始時の初期状態（変更検知用）
 * @param isEditing 編集・登録用ダイアログが表示されているかどうか
 * @param personName 対象利用者の氏名（表示用）
 * @param isNameMaskingEnabled 氏名のマスキング設定
 */
data class EmergencyContactUiState(
    val isLoading: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList(),
    val editingContact: EmergencyContact? = null,
    val initialContact: EmergencyContact? = null,
    val isEditing: Boolean = false,
    val personName: String = "",
    val isNameMaskingEnabled: Boolean = true
) {
    /** 初期状態から内容が変更されているか */
    val isChanged: Boolean get() = EmergencyContactLogic.isChanged(editingContact, initialContact)
    /** 入力内容がバリデーションを通過しているか */
    val isValid: Boolean get() = EmergencyContactLogic.isValid(editingContact)
}

/**
 * View Event：EmergencyContactViewEvent
 *
 * 【役割】
 * 緊急連絡先画面における、一過性のアクション通知を定義します。
 */
sealed interface EmergencyContactViewEvent {
    /** 保存成功通知 */
    object SaveSuccess : EmergencyContactViewEvent
    /** 削除成功通知 */
    object DeleteSuccess : EmergencyContactViewEvent
}

/**
 * ViewModel：EmergencyContactEditViewModel
 *
 * 【役割】
 * 利用者の緊急連絡先管理（SCR-M-003）および登録・編集（SCR-M-004）における状態管理と実行制御を担当します。
 *
 * 【主要な機能】
 * ・特定の利用者に紐付く緊急連絡先リストの購読。
 * ・連絡先の新規登録・編集ダイアログの表示状態制御。
 * ・入力内容のリアルタイムバリデーションと変更検知。
 * ・保存（INSERT/UPDATE）および削除処理の実行と、証跡の記録。
 *
 * 【依存している Repository】
 * ・EmergencyContactRepository: 連絡先データの CRUD 操作。
 * ・PersonRepository: 対象利用者の基本情報（氏名）取得。
 * ・UserSettingsRepository: 共通設定の参照。
 * ・AuditLogRepository: 連絡先変更の証跡記録。
 *
 * 【依存している Logic】
 * ・EmergencyContactLogic: バリデーション、初期データ生成、変更判定。
 *
 * 【設計指針】
 * 1. 利用者文脈の維持：`personId` をコンストラクタで受け取り、常に特定の利用者に固定された操作を行う。
 * 2. データの正規化：保存実行直前に Logic 層を通じてデータのクレンジング（電話番号の数字のみ抽出等）を行う。
 * 3. 監査性の確保：重要な連絡先情報の変更は、詳細内容とともに監査ログへ記録する。
 */
class EmergencyContactEditViewModel(
    private val personId: String,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    private val auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<EmergencyContactUiState, EmergencyContactViewEvent>(
    userSettingsRepository,
    EmergencyContactUiState()
) {

    companion object {
        /** 監査ログ用機能名 */
        private const val FEATURE_NAME = "MedicalContact"
        /** 監査ログ用操作名：保存 */
        private const val OP_SAVE = "saveContact"
        /** 監査ログ用操作名：削除 */
        private const val OP_DELETE = "deleteContact"
        /** 監査ログ用対象テーブル */
        private const val TABLE_NAME = "emergency_contact_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定（氏名マスキング）の同期
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // 利用者情報の取得と初期表示名の設定
        loadPersonInfo()
        // 連絡先リストの継続的な購読
        loadEmergencyContacts()
    }

    /**
     * 利用者の基本情報を取得し、表示用の氏名をセットします。
     */
    private fun loadPersonInfo() {
        safeLaunch(
            operation = "loadPersonInfo",
            contextBuilder = { tableName = "person_db"; affectedId = personId }
        ) {
            val person = personRepository.getPersonById(personId).first()
            updateUiState { it.copy(personName = person?.getMaskedName(it.isNameMaskingEnabled) ?: "") }
        }
    }

    /**
     * 連絡先リストの購読を開始します。
     */
    private fun loadEmergencyContacts() {
        safeCollect(
            operation = "loadEmergencyContacts",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_NAME; affectedId = personId },
            flowProvider = { emergencyContactRepository.getContactsByPersonId(personId) }
        ) { contacts ->
            updateUiState { it.copy(contacts = contacts) }
        }
    }

    /**
     * 新規追加の準備を行います（初期値を設定してダイアログフラグを立てる）。
     */
    fun startAdd() {
        val initial = EmergencyContactLogic.createInitialEntity(personId)
        updateUiState {
            it.copy(
                editingContact = initial,
                initialContact = initial,
                isEditing = true
            )
        }
    }

    /**
     * 既存編集モードでダイアログを表示します。
     *
     * @param contact 編集対象の連絡先
     */
    fun startEdit(contact: EmergencyContact) {
        updateUiState {
            it.copy(
                editingContact = contact,
                initialContact = contact,
                isEditing = true
            )
        }
    }

    /**
     * 編集内容を更新します（入力フィールドからのコールバック用）。
     *
     * @param reducer 現在の Entity を受け取り、新しい Entity を返すラムダ
     */
    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateUiState { current ->
            current.editingContact?.let {
                current.copy(editingContact = reducer(it))
            } ?: current
        }
    }

    /**
     * 編集・登録ダイアログを閉じます。
     */
    fun dismissEdit() {
        updateUiState { it.copy(isEditing = false, editingContact = null, initialContact = null) }
    }

    /**
     * 現在編集中の連絡先を保存または更新します。
     */
    fun saveContact() {
        val contact = currentState.editingContact ?: return
        
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            // 保存直前にデータの正規化を実行
            val contactToSave = EmergencyContactLogic.createSaveEntity(contact)
            
            if (contactToSave.id.isEmpty()) {
                emergencyContactRepository.insertContact(contactToSave, featureName, OP_SAVE)
            } else {
                emergencyContactRepository.updateContact(contactToSave, featureName, OP_SAVE)
            }
            
            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
            dismissEdit()
        }
    }

    /**
     * 指定された連絡先を物理削除します。
     *
     * @param contact 削除対象の連絡先
     */
    fun deleteContact(contact: EmergencyContact) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_NAME
                affectedId = contact.id
            }
        ) {
            emergencyContactRepository.deleteContact(contact, featureName, OP_DELETE)
            sendViewEvent(EmergencyContactViewEvent.DeleteSuccess)
        }
    }

    override fun copyWithLoadingState(state: EmergencyContactUiState, isLoading: Boolean): EmergencyContactUiState {
        return state.copy(isLoading = isLoading)
    }

    /**
     * EmergencyContactEditViewModel 生成用の Factory クラス。
     */
    class Factory(
        private val personId: String,
        private val emergencyContactRepository: EmergencyContactRepository,
        private val personRepository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EmergencyContactEditViewModel(
                personId,
                emergencyContactRepository,
                personRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
