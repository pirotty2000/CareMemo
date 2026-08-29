package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.logic.common.MedicationValidationResult
import jp.mydns.fujiwara.carememo.logic.common.SyncAction
import jp.mydns.fujiwara.carememo.logic.common.MedicationTimeSlot
import jp.mydns.fujiwara.carememo.logic.common.MedicationStatus
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationViewEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * ViewModel：PersonMedicationViewModel
 *
 * 【役割】
 * 利用者の服薬管理画面における状態管理と実行制御を担当します。
 * 日々の服薬状況（朝・昼・夕・寝る前など）をカレンダー形式またはリスト形式で管理する機能を提供します。
 *
 * 【設計指針：UI 境界の責務】
 * 1. 状態の不変化：UI に公開する服薬記録リスト (`monthlyRecords`) およびグルーピング済みマップ (`recordsByDate`) は、
 *    UI 境界において ImmutableList / ImmutableMap へ変換し、不変性を保証します。
 * 2. グルーピングロジックの提供：カレンダー表示を効率化するため、日付ごとのグルーピング処理を行い、
 *    UI 層が O(1) で特定日のデータを取得できる構造を提供します。
 *
 * 【この ViewModel では行わないこと】
 * ・カレンダーの表示用日付リスト生成（MedicationLogic が担当）。
 * ・DB レコードと入力値の差分に基づく同期アクションの判定（MedicationLogic が担当）。
 */
class PersonMedicationViewModel(
    private val medicationRepository: MedicationRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonMedicationUiState, PersonMedicationViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    securitySession,
    auditLogRepository,
    PersonMedicationUiState(),
    savedStateHandle
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonMedication"
        /** 監査ログ用：同期操作名 */
        private const val OP_SYNC = "syncMedicationDay"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_MEDICATION = "medication_db"

        // --- Restoration Keys ---
        private const val KEY_SELECTED_MONTH = "restoration_selected_month"
        private const val KEY_DIALOG_DATE = "restoration_dialog_date"
        private const val KEY_DIALOG_RECORDS = "restoration_dialog_records"
    }

    override val featureName: String = FEATURE_NAME

    /** 復元中であることを示すフラグ */
    private var isRestoring = false

    /** 指定月のレコード購読用 Job */
    private var monthlyRecordsJob: Job? = null
    /** 全レコード購読用 Job */
    private var allRecordsJob: Job? = null

    /** 同期処理（保存・削除）用の Job */
    private var syncJob: Job? = null

    init {
        // --- State Restoration ---
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        // 引数（categoryName）の変更を購読
        scope.launch {
            savedStateHandle.getStateFlow<String?>(KEY_CATEGORY_NAME, null).collect { name ->
                if (name != null) {
                    try {
                        setCategory(Category.valueOf(name))
                    } catch (_: Exception) {
                        // 無視
                    }
                }
            }
        }

        // 最後に監視を開始 (featureName が初期化された後)
        startObservePersonId()
    }

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val handle = savedStateHandle ?: return
        val monthStr = handle.get<String>(KEY_SELECTED_MONTH)
        val selectedMonth = monthStr?.let { YearMonth.parse(it) } ?: YearMonth.now()
        val dateStr = handle.get<String>(KEY_DIALOG_DATE)
        val selectedDialogDate = dateStr?.let { LocalDate.parse(it) }
        val dialogRecords = handle.get<List<MedicationRecord?>>(KEY_DIALOG_RECORDS) ?: persistentListOf(null, null, null, null)

        updateUiState {
            it.copy(
                selectedMonth = selectedMonth,
                selectedDialogDate = selectedDialogDate,
                dialogTempRecords = dialogRecords.toImmutableList()
            )
        }
    }

    /**
     * 復元対象の状態をバックアップします。
     */
    private fun backupRestorableState(state: PersonMedicationUiState) {
        val handle = savedStateHandle ?: return
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_SELECTED_MONTH] = state.selectedMonth.toString()
        handle[KEY_DIALOG_DATE] = state.selectedDialogDate?.toString()
        handle[KEY_DIALOG_RECORDS] = state.dialogTempRecords.toList()
    }

    override fun copyWithLoadingState(state: PersonMedicationUiState, isLoading: Boolean): PersonMedicationUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonMedicationUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonMedicationUiState {
        // 復元中の場合は、初期化によるリセットをスキップして現在の状態を維持する
        if (isRestoring) {
            isRestoring = false // 復元処理を消費
            refreshMonthlyRecords(state)
            refreshAllRecords(state)
            return state
        }

        // 利用者情報ロード時に、初期表示月（現在月）を設定し購読を開始する
        val next = state.copy(
            personId = person.id,
            selectedMonth = YearMonth.now()
        )
        backupRestorableState(next)
        refreshMonthlyRecords(next)
        refreshAllRecords(next)
        return next
    }

    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category) }
        }
    }

    // --- 購読ロジック (原子的な反映) ---

    /**
     * 指定された年月の服薬記録の購読を開始・更新します。
     *
     * @param state 現在の UI 状態
     */
    private fun refreshMonthlyRecords(state: PersonMedicationUiState) {
        val personId = state.personId ?: return
        val month = state.selectedMonth

        monthlyRecordsJob?.cancel()
        monthlyRecordsJob = safeCollect(
            operation = "monthlyRecordsFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_MEDICATION },
            flowProvider = { medicationRepository.getMedicationRecordsByMonth(personId, month.toString()) }
        ) { records ->
            updateUiState { current ->
                // UI 境界において ImmutableList / ImmutableMap へ変換し、不変性と描画安定性を確保する
                current.copy(
                    monthlyRecords = records.toImmutableList(),
                    // 日付ごとにグループ化したマップを作成し、カレンダー表示を効率化する
                    recordsByDate = PersonMedicationLogic.groupRecordsByDate(records)
                        .mapValues { it.value.toImmutableList() }
                        .toImmutableMap()
                )
            }
        }
    }

    /**
     * 全期間の服薬記録の購読を開始します（統計表示用など）。
     *
     * @param state 現在の UI 状態
     */
    private fun refreshAllRecords(state: PersonMedicationUiState) {
        val personId = state.personId ?: return
        allRecordsJob?.cancel()
        allRecordsJob = safeCollect(
            operation = "allRecordsFlow",
            mode = CollectMode.INITIAL,
            contextBuilder = { tableName = TABLE_MEDICATION },
            flowProvider = { medicationRepository.getMedicationRecords(personId) }
        ) { records ->
            updateUiState { it.copy(allRecords = records.toImmutableList()) }
        }
    }

    // --- UI アクション ---

    /** 表示対象月を翌月に進めます。 */
    fun nextMonth() {
        updateUiState { 
            val nextMonth = it.selectedMonth.plusMonths(1)
            val next = it.copy(selectedMonth = nextMonth)
            backupRestorableState(next)
            next
        }
        refreshMonthlyRecords(currentState)
    }

    /** 表示対象月を前月に戻します。 */
    fun previousMonth() {
        updateUiState { 
            val prevMonth = it.selectedMonth.minusMonths(1)
            val next = it.copy(selectedMonth = prevMonth)
            backupRestorableState(next)
            next
        }
        refreshMonthlyRecords(currentState)
    }

    /**
     * カレンダーの日付がクリックされた際の処理。
     * ダイアログを表示し、その日の初期データをセットします。
     */
    fun onDayClick(date: LocalDate) {
        updateUiState { current ->
            val dateStr = date.toString()
            val initialRecords = MedicationTimeSlot.entries.map { slot ->
                current.recordsByDate[dateStr]?.find { it.timeSlot == slot.index }
            }.toImmutableList()
            
            val next = current.copy(
                selectedDialogDate = date,
                dialogTempRecords = initialRecords
            )
            backupRestorableState(next)
            next
        }
    }

    /**
     * ダイアログを閉じます。
     */
    fun dismissDialog() {
        updateUiState { current ->
            val next = current.copy(selectedDialogDate = null)
            clearRestorableState(KEY_DIALOG_DATE, KEY_DIALOG_RECORDS)
            next
        }
    }

    /**
     * ダイアログ内での服薬ステータスの変更を反映します。
     */
    fun updateDialogRecord(slotIndex: Int, status: MedicationStatus, recordTime: Instant) {
        updateUiState { current ->
            val date = current.selectedDialogDate ?: return@updateUiState current
            val existing = current.dialogTempRecords[slotIndex]
            
            val nextRecords = current.dialogTempRecords.toMutableList().apply {
                if (existing?.status == status.code) {
                    // トグル：同じステータスなら解除
                    set(slotIndex, null)
                } else {
                    set(slotIndex, (existing?.copy(status = status.code, recordTime = recordTime)
                        ?: MedicationRecord(
                            id = jp.mydns.fujiwara.carememo.data.AppSpecifications.Id.NEW_RECORD_ID,
                            personId = requiredPersonId,
                            dosageDate = date.toString(),
                            timeSlot = slotIndex,
                            status = status.code,
                            recordTime = recordTime
                        )))
                }
            }.toImmutableList()
            
            val next = current.copy(dialogTempRecords = nextRecords)
            backupRestorableState(next)
            next
        }
    }

    /**
     * 特定の日の服薬状況を一括同期（保存・削除）します。
     */
    fun syncMedicationDay() {
        val date = currentState.selectedDialogDate?.toString() ?: return
        val slotRecords = currentState.dialogTempRecords

        // 二重実行防止
        if (syncJob?.isActive == true) return

        syncJob = safeLaunch(
            operation = OP_SYNC,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_MEDICATION
                affectedId = requiredPersonId
            }
        ) {
            // 1. 各レコードのバリデーションを実行
            slotRecords.filterNotNull().forEach { record ->
                val validationResult = MedicationLogic.validateMedication(record)
                translateValidationResult(validationResult)
            }

            val currentDayRecords = currentState.recordsByDate[date] ?: emptyList()
            
            // 2. ロジック層で、DB との差分に基づく同期アクション（Insert/Delete）を判定
            val actions = MedicationLogic.determineSyncActions(currentDayRecords, slotRecords)

            // 3. 判定されたアクションを順次実行
            actions.forEach { action ->
                when (action) {
                    is SyncAction.Insert -> {
                        val isUpdate = currentDayRecords.any { it.timeSlot == action.record.timeSlot }
                        medicationRepository.saveMedicationRecord(action.record, isUpdate, featureName, OP_SYNC)
                    }
                    is SyncAction.Delete -> medicationRepository.deleteMedicationRecord(action.record, featureName, OP_SYNC)
                    SyncAction.None -> {}
                }
            }
            
            // 何らかの更新があった場合のみ通知を表示
            if (actions.any { it !is SyncAction.None }) {
                showSnackbar(R.string.p_med_msg_update_success)
            }

            dismissDialog()
        }
    }

    /**
     * 服薬管理固有のバリデーション結果を例外に変換し、エラー通知フローをトリガーします。
     */
    private fun translateValidationResult(result: MedicationValidationResult) {
        if (result == MedicationValidationResult.SUCCESS) return
        val messageRes = when (result) {
            MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED -> R.string.common_error_save
            else -> R.string.common_error_save
        }
        val args = if (result == MedicationValidationResult.FUTURE_DATE_NOT_ALLOWED) listOf("未来の日付には記録できません") else emptyList()
        
        throw AppValidationException(R.string.common_error_title_save, messageRes, args, "Validation failed: $result")
    }

    /*
    /**
     * 一覧画面へ戻ります。
     * 現在はシステム側の戻るボタンやナビゲーションバーでの遷移が主であるため、
     * 画面内に専用の「戻る」ボタンを配置してロジックを呼ぶ必要が生じた際に復活させるため保持。
     */
    fun navigateBackToMain() {
        sendViewEvent(PersonMedicationViewEvent.NavigateBackToMain)
    }
    */

    /**
     * PersonMedicationViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val medicationRepository: MedicationRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonMedicationViewModel(
                medicationRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
