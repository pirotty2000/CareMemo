package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.EmergencyContactRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.EmergencyContactLogic
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI State：EmergencyContactUiState
 *
 * 【役割】
 * 緊急連絡先管理画面（一覧・編集ダイアログ）の表示状態を一元管理します。
 *
 * @param isLoading 全体のデータ読み込み・処理中フラグ
 * @param contacts 当該利用者に紐付く登録済みの全緊急連絡先リスト
 * @param editingContact 現在ダイアログで編集・入力中の連絡先情報（未編集時は null）
 * @param initialContact 編集開始時の初期状態。変更検知（isChanged）の比較元として使用
 * @param isEditing 編集・登録用ダイアログが表示されているかどうか
 * @param personName 画面ヘッダー等に表示する対象利用者の氏名
 * @param isNameMaskingEnabled 氏名のマスキング（伏せ字）設定の現在値
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
    /** 入力内容が初期状態から変更されているか（保存ボタンの活性制御等に使用） */
    val isChanged: Boolean get() = EmergencyContactLogic.isChanged(editingContact, initialContact)
    /** 現在の入力内容がバリデーション（必須項目チェック等）を通過しているか */
    val isValid: Boolean get() = EmergencyContactLogic.isValid(editingContact)
}

/**
 * View Event：EmergencyContactViewEvent
 *
 * 【役割】
 * 緊急連絡先画面における、一過性のアクション通知（保存・削除の成功等）を定義します。
 */
sealed interface EmergencyContactViewEvent {
    /** 保存（新規・更新）が正常に完了したことを通知 */
    object SaveSuccess : EmergencyContactViewEvent
    /** 削除が正常に完了したことを通知 */
    object DeleteSuccess : EmergencyContactViewEvent
}

/**
 * ViewModel：EmergencyContactEditViewModel
 *
 * 【役割】
 * 利用者の緊急連絡先管理（SCR-M-003）および登録・編集（SCR-M-004）における状態管理と実行制御を担当します。
 * 主治医、訪問看護ステーション、家族などの重要な連絡先情報を安全に管理する機能を提供します。
 *
 * 【主要な機能】
 * ・特定の利用者に紐付く緊急連絡先リストの購読と UI 状態への反映。
 * ・連絡先の新規登録・編集ダイアログの表示状態および入力状態の制御。
 * ・入力内容に対するリアルタイムなバリデーションと変更検知の実施。
 * ・DB への保存（INSERT/UPDATE）および削除処理の実行、および操作証跡の記録。
 *
 * 【依存している Repository】
 * ・EmergencyContactRepository: 連絡先データの永続化と取得。
 * ・PersonRepository: 対象利用者の基本情報の参照。
 * ・AuditLogRepository: 破壊的・重要な操作の監査ログ記録（例外ハンドラ初期化に使用）。
 * ・UserSettingsRepository: 共通設定（氏名マスキング）の購読。
 *
 * 【依存している Logic】
 * ・EmergencyContactLogic: エンティティ生成、バリデーション、変更判定の純粋ロジック。
 */
class EmergencyContactEditViewModel(
    private val personId: String,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val personRepository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseUiStateViewModel<EmergencyContactUiState, EmergencyContactViewEvent>(
    userSettingsRepository,
    EmergencyContactUiState()
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "MedicalContact"
        /** 監査ログ用：保存操作名 */
        private const val OP_SAVE = "saveContact"
        /** 監査ログ用：削除操作名 */
        private const val OP_DELETE = "deleteContact"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_NAME = "emergency_contact_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // 利用者の基本情報をロードして表示名を確定
        loadPersonInfo()
        // 連絡先リストの継続的な購読を開始
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
     * 指定された利用者に紐付く緊急連絡先リストの購読を開始します。
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
     * 新規追加モードで編集ダイアログを表示します。
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
     * 既存データの編集モードでダイアログを表示します。
     *
     * @param contact 編集対象の既存連絡先 Entity
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
     * ダイアログでの入力内容を一時状態（editingContact）に反映します。
     *
     * @param reducer 現在の編集内容を受け取り、変更後の Entity を返すラムダ
     */
    fun updateEditingContact(reducer: (EmergencyContact) -> EmergencyContact) {
        updateUiState { current ->
            current.editingContact?.let {
                current.copy(editingContact = reducer(it))
            } ?: current
        }
    }

    /**
     * 編集・登録ダイアログを閉じ、入力中の一時状態を破棄します。
     */
    fun dismissEdit() {
        updateUiState { it.copy(isEditing = false, editingContact = null, initialContact = null) }
    }

    /**
     * 現在入力中の連絡先情報を DB に保存（新規または更新）します。
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
            // 保存直前にデータの正規化を実行（電話番号の整形など）
            val contactToSave = EmergencyContactLogic.createSaveEntity(contact)
            
            // IdLogic に基づき、新規登録か更新かを判定
            if (IdLogic.isNew(contactToSave.id)) {
                emergencyContactRepository.insertContact(contactToSave, featureName, OP_SAVE)
            } else {
                emergencyContactRepository.updateContact(contactToSave, featureName, OP_SAVE)
            }
            
            // UI への完了通知と後処理
            sendViewEvent(EmergencyContactViewEvent.SaveSuccess)
            dismissEdit()
        }
    }

    /**
     * 指定された緊急連絡先を DB から物理削除します。
     *
     * @param contact 削除対象の連絡先 Entity
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
            // 削除成功を通知
            sendViewEvent(EmergencyContactViewEvent.DeleteSuccess)
        }
    }

    override fun copyWithLoadingState(state: EmergencyContactUiState, isLoading: Boolean): EmergencyContactUiState {
        return state.copy(isLoading = isLoading)
    }

    /**
     * EmergencyContactEditViewModel を生成するための Factory クラス。
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
