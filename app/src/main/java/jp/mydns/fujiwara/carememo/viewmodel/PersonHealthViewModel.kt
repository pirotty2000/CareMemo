package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.HealthValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthViewEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel：PersonHealthViewModel
 *
 * 【役割】
 * 利用者の健康記録（身長・体重、血圧・脈拍、血糖値・HbA1c）画面における状態管理と実行制御を担当します。
 * 各種バイタルデータの履歴管理、グラフ表示のためのデータ提供、および新規登録・編集・削除機能を集約します。
 *
 * 【主要な機能】
 * ・健康記録データのカテゴリ別購読と UI 状態への反映。
 * ・履歴表示とグラフ表示の切り替え状態の保持および永続化。
 * ・各種健康記録のバリデーション、重複チェックを伴う保存・更新処理。
 * ・記録の削除およびそれに伴う UI 通知。
 * ・拡大表示画面等の外部向けデータストリームの提供。
 *
 * 【依存している Repository】
 * ・HealthRepository: 健康記録データ（3系統）の取得、保存、削除。
 * ・PersonRepository / PersonSummaryRepository: 利用者情報およびサマリーの管理（基底クラスで使用）。
 * ・AuditLogRepository: 操作の証跡記録（基底クラスの例外ハンドラ経由）。
 * ・UserSettingsRepository: 表示モード（履歴/グラフ）等のユーザー設定の管理。
 *
 * 【設計指針】
 * 1. カテゴリの動的切り替え：選択されたカテゴリに応じて購読する Flow を切り替え、常に最新のデータを表示する。
 * 2. 型安全なデータ処理：内部的には `HistoryRecord` インターフェース等を活用しつつ、保存時は具象型に応じたリポジトリメソッドを呼び出す。
 * 3. ユーザー設定の尊重：表示モード等の好みを自動的に購読・反映し、再開時にも同じ表示を維持する。
 */
class PersonHealthViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseUiStateViewModel<PersonHealthUiState, PersonHealthViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    PersonHealthUiState()
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "PersonHealth"
        /** 監査ログ用：保存操作名 */
        private const val OP_SAVE = "saveRecord"
        /** 監査ログ用：削除操作名 */
        private const val OP_DELETE = "deleteRecord"
        /** 監査ログ用：リスト購読操作名 */
        private const val OP_RECORDS_FLOW = "recordsFlow"
        /** 監査ログ用：対象テーブル名 */
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    /** 健康記録リスト購読用 Job */
    private var recordsJob: Job? = null

    init {
        // 表示モード（履歴優先かグラフ優先か）のユーザー設定を購読し、初期状態および変更時に反映する
        scope.launch {
            userSettingsRepository.healthDisplayModeIsHistory.collect { isHistory ->
                updateUiState { it.copy(preferredShowHistory = isHistory) }
            }
        }
    }

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: PersonHealthUiState, isLoading: Boolean): PersonHealthUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: PersonHealthUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): PersonHealthUiState {
        // 利用者情報がロードされた際、その利用者の現在のカテゴリに応じたデータ購読を開始する
        val next = state.copy(personId = person.id)
        refreshRecords(next.personId, next.currentCategory)
        return next
    }

    override fun onPrepareLoadPerson(state: PersonHealthUiState): PersonHealthUiState {
        // 利用者が切り替わる（一覧から入り直す）際はデータをクリアし、
        // 初期表示モードを「履歴」にリセットします。
        return state.copy(
            personId = null,
            records = emptyList(),
            selectedRecordId = null,
            preferredShowHistory = true
        )
    }

    /**
     * 履歴表示またはグラフ表示の優先設定を更新し、永続化します。
     *
     * @param preferredShowHistory 履歴を優先して表示する場合は true
     */
    fun updatePreferredShowHistory(preferredShowHistory: Boolean) {
        scope.launch {
            userSettingsRepository.setHealthDisplayModeIsHistory(preferredShowHistory)
        }
    }

    /**
     * 表示対象の健康カテゴリを設定し、該当するデータの購読を再開します。
     *
     * @param category 身長体重、血圧脈拍、血糖値HbA1c のいずれか
     */
    fun setCategory(category: Category) {
        if (currentState.currentCategory != category) {
            updateUiState { it.copy(currentCategory = category, selectedRecordId = null) }
            refreshRecords(currentState.personId, category)
        }
    }

    /**
     * 詳細表示または編集対象として選択されたレコードの ID を設定します。
     *
     * @param id レコードID。選択解除時は null。
     */
    fun setSelectedRecordId(id: String?) {
        updateUiState { it.copy(selectedRecordId = id) }
    }

    /**
     * 指定されたカテゴリと人物に基づき、リポジトリから履歴データの継続的な購読を開始します。
     * データに変更があった場合、自動的に UI 状態の `records` が更新されます。
     */
    private fun refreshRecords(personId: String?, category: Category) {
        if (personId == null) return

        recordsJob?.cancel()
        recordsJob = safeCollect(
            operation = OP_RECORDS_FLOW,
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = TABLE_HEALTH },
            flowProvider = {
                when (category) {
                    Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(personId)
                    Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(personId)
                    Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(personId)
                    else -> flowOf(emptyList())
                }
            }
        ) { records ->
            updateUiState { it.copy(records = records) }
        }
    }

    /**
     * 特定のカテゴリの履歴データを外部（拡大画面等）へ提供するためのストリームを生成します。
     *
     * @param category 取得対象のカテゴリ
     * @return 履歴リストの StateFlow
     */
    fun getHealthRecords(category: Category): StateFlow<List<HistoryRecord>> {
        val personId = currentState.personId ?: return flowOf(emptyList<HistoryRecord>()).stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

        return when (category) {
            Category.HEIGHT_AND_WEIGHT -> healthRepository.getHeightAndWeightByPersonId(personId)
            Category.BP_AND_PULSE -> healthRepository.getBpAndPulseByPersonId(personId)
            Category.GLUCOSE_AND_HBA1C -> healthRepository.getGlucoseAndHbA1cByPersonId(personId)
            else -> flowOf(emptyList())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * 入力された健康記録データをバリデーションし、DB へ保存（新規または更新）します。
     *
     * @param category 記録対象のカテゴリ
     * @param recordId レコードID（新規時はプレースホルダ）
     * @param recordTime 記録日時
     * @param values 入力された値のマップ
     */
    fun saveRecord(category: Category, recordId: String, recordTime: Instant, values: Map<String, Any?>) {
        safeLaunch(
            operation = OP_SAVE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = recordId
            }
        ) {
            // 1. ロジック層へ委譲して Entity 構築
            val record = PersonHealthLogic.createEntity(category, requiredPersonId, recordId, recordTime, values) as HistoryRecord

            // 2. 入力値のバリデーション実行
            val validationResult = PersonHealthLogic.validate(record)
            translateValidationResult(validationResult)

            val isUpdate = !IdLogic.isNew(recordId)

            // 3. 同時刻の既存データとの重複チェック
            val existing = when (record) {
                is HeightAndWeight -> healthRepository.findHeightAndWeightAtTime(record.personId, record.recordTime)
                is BpAndPulse -> healthRepository.findBpAndPulseAtTime(record.personId, record.recordTime)
                is GlucoseAndHbA1c -> healthRepository.findGlucoseAndHbA1cAtTime(record.personId, record.recordTime)
                else -> null
            }

            val duplicateResult = PersonHealthLogic.validateDuplicate(record, existing)
            translateValidationResult(duplicateResult)

            // 4. 保存の実行と通知
            performSave(record, isUpdate)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(if (isUpdate) R.string.p_health_msg_update_success else R.string.p_health_msg_save_success)
        }
    }

    /** 健康記録固有のバリデーション結果を例外に変換し、エラーダイアログの表示とログ出力をトリガーします。 */
    private fun translateValidationResult(result: HealthValidationResult) {
        if (result == HealthValidationResult.SUCCESS) return

        val messageRes = when (result) {
            HealthValidationResult.INVALID_VALUE -> R.string.common_error_save
            HealthValidationResult.DUPLICATE_TIME -> R.string.common_err_duplicate_blocked_simple
            else -> R.string.common_error_save
        }

        val args = when (result) {
            HealthValidationResult.INVALID_VALUE -> listOf("入力値が範囲外です。正しい数値を入力してください。")
            else -> emptyList()
        }

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
    }

    /** 具象型に応じたリポジトリの保存メソッドを呼び出します。 */
    private suspend fun performSave(record: Any, isUpdate: Boolean) = when (record) {
        is HeightAndWeight -> healthRepository.insertHeightAndWeight(record, featureName, OP_SAVE, isUpdate)
        is BpAndPulse -> healthRepository.insertBpAndPulse(record, featureName, OP_SAVE, isUpdate)
        is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(record, featureName, OP_SAVE, isUpdate)
        else -> {}
    }

    /**
     * 指定された健康記録レコードを物理削除します。
     *
     * @param record 削除対象のレコード Entity
     */
    fun deleteRecord(record: Any) {
        safeLaunch(
            operation = OP_DELETE,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = (record as? HistoryRecord)?.id
            }
        ) {
            performDelete(record)
            showSnackbar(R.string.p_health_msg_delete_success)
        }
    }

    /** 具象型に応じたリポジトリの削除メソッドを呼び出します。 */
    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> healthRepository.deleteHeightAndWeight(record, featureName, OP_DELETE)
        is BpAndPulse -> healthRepository.deleteBpAndPulse(record, featureName, OP_DELETE)
        is GlucoseAndHbA1c -> healthRepository.deleteGlucoseAndHbA1c(record, featureName, OP_DELETE)
        else -> {}
    }

    /**
     * PersonHealthViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonHealthViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
