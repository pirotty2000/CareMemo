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
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.MedicationLogic
import jp.mydns.fujiwara.carememo.logic.common.MedicationValidationResult
import jp.mydns.fujiwara.carememo.logic.common.SyncAction
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationViewEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonMedicationUiState, PersonMedicationViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
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
    }

    override val featureName: String = FEATURE_NAME

    /** 指定月のレコード購読用 Job */
    private var monthlyRecordsJob: Job? = null
    /** 全レコード購読用 Job */
    private var allRecordsJob: Job? = null

    /** 同期処理（保存・削除）用の Job */
    private var syncJob: Job? = null

    init {
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

    override fun copyWithLoadingState(state: PersonMedicationUiState, isLoading: Boolean): PersonMedicationUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonMedicationUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonMedicationUiState {
        // 利用者情報ロード時に、初期表示月（現在月）を設定し購読を開始する
        val next = state.copy(
            personId = person.id,
            selectedMonth = YearMonth.now()
        )
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
        updateUiState { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        refreshMonthlyRecords(currentState)
    }

    /** 表示対象月を前月に戻します。 */
    fun previousMonth() {
        updateUiState { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        refreshMonthlyRecords(currentState)
    }

    /**
     * 特定の日の服薬状況を一括同期（保存・削除）します。
     *
     * UI 上で各スロット（朝・昼など）のチェック状態が確定した際に呼び出されます。
     * 既存の DB レコードと入力されたスロットの状態を比較し、必要な差分更新のみを実行します。
     *
     * @param date 同期対象の日付 (yyyy-MM-dd)
     * @param slotRecords 各スロットの服薬記録オブジェクトのリスト（未チェック時は null）
     */
    fun syncMedicationDay(date: String, slotRecords: List<MedicationRecord?>) {
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
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
