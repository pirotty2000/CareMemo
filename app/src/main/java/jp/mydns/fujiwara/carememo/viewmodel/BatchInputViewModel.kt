package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import jp.mydns.fujiwara.carememo.logic.feature.HealthProcessorRegistry
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * ViewModel：BatchInputViewModel
 *
 * 【役割】
 * 健康記録の一括入力画面（SCR-PH-002）における状態管理と保存実行を制御します。
 * 身長体重、バイタル（血圧・脈拍等）、血糖値の複数カテゴリにわたる入力内容を同時に扱い、
 * 一つの画面で効率的に記録できる機能を提供します。
 *
 * 【主要な機能】
 * ・複数カテゴリの入力状態の一元保持。
 * ・全項目に対するバリデーション（範囲チェックおよび相関チェック）の実施。
 * ・保存前の「同一日時データ」の重複チェック（上書き防止仕様）。
 * ・保存成功時のアニメーション演出イベントの通知および入力値のクリア。
 *
 * 【依存している Repository】
 * ・HealthRepository: 各健康データの取得（重複確認）および保存。
 * ・PersonRepository / PersonSummaryRepository: 利用者情報およびサマリーの管理（基底クラスで使用）。
 * ・AuditLogRepository: 保存・バリデーション失敗等の操作証跡を記録。
 * ・UserSettingsRepository: 共通設定（氏名のマスキング等）の参照。
 *
 * 【設計指針】
 * 1. 整合性の優先：保存前に全カテゴリに対して同一日時の既存レコードがないかを確認し、不整合な上書きを防止する。
 * 2. 入力効率の最大化：各項目の更新時に即座に `isValid` や `isChanged` を計算し、保存ボタンの活性状態を制御する。
 * 3. 単一方向データフロー：`PersonBaseUiStateViewModel` の仕組みを利用し、利用者コンテキストに基づいたデータ管理を行う。
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<BatchInputUiState, BatchInputViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    BatchInputUiState(),
    savedStateHandle
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "BatchInput"
        /** 監査ログ用：一括保存操作名 */
        private const val OP_SAVE_BATCH = "OP_SAVE_BATCH"
        /** 監査ログ用：対象概念テーブル名 */
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 共通設定（氏名マスキング）の変更を購読し、UI 状態へ反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }

        // 最後に監視を開始 (featureName が初期化された後)
        startObservePersonId()
    }

    // --- 基底クラスの抽象メソッド実装 ---

    override fun copyWithLoadingState(state: BatchInputUiState, isLoading: Boolean): BatchInputUiState {
        return state.copy(isLoading = isLoading)
    }

    override fun updateWithPersonData(
        state: BatchInputUiState,
        person: Person,
        summary: PersonCategorySummary?
    ): BatchInputUiState {
        // 利用者が切り替わった場合、または初回ロード時は、入力をリセットし、記録日時を現在時刻に設定する
        val isFirstLoad = state.personId == null
        val isDifferentPerson = state.personId != person.id
        
        val next = if (isFirstLoad || isDifferentPerson) {
            val now = Instant.now().atZone(ZoneId.systemDefault())
            val y = now.year.toString()
            val m = now.monthValue.toString()
            val d = now.dayOfMonth.toString()
            val h = "%02d".format(now.hour)
            val min = "%02d".format(now.minute)

            state.copy(
                personId = person.id,
                person = person,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary,
                height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                year = y, month = m, day = d, hour = h, minute = min,
                initialYear = y, initialMonth = m, initialDay = d, initialHour = h, initialMinute = min
            )
        } else {
            // 同一利用者の再ロード時は、基本情報とサマリーのみ更新し、入力中の日時は維持する
            state.copy(
                personId = person.id,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary
            )
        }
        
        // 最新の状態に基づき、バリデーションと変更有無を再計算して返す
        return next.copy(
            isValid = BatchInputLogic.isValid(next),
            isChanged = BatchInputLogic.isChanged(next)
        )
    }

    // --- UI 入力更新用メソッド群 ---
    // 各項目の更新は updateState ヘルパーを介して原子的に行われ、派生状態も同時に更新されます。

    fun updateYear(v: String) = updateState { it.copy(year = v) }
    fun updateMonth(v: String) = updateState { it.copy(month = v) }
    fun updateDay(v: String) = updateState { it.copy(day = v) }
    fun updateHour(v: String) = updateState { it.copy(hour = v) }
    fun updateMinute(v: String) = updateState { it.copy(minute = v) }

    fun updateHeight(v: String) = updateState { it.copy(height = v) }
    fun updateWeight(v: String) = updateState { it.copy(weight = v) }
    fun updateBpSystolic(v: String) = updateState { it.copy(bpSystolic = v) }
    fun updateBpDiastolic(v: String) = updateState { it.copy(bpDiastolic = v) }
    fun updateSat(v: String) = updateState { it.copy(sat = v) }
    fun updatePulse(v: String) = updateState { it.copy(pulse = v) }
    fun updateBodyTemp(v: String) = updateState { it.copy(bodyTemperature = v) }
    fun updateGlucose(v: String) = updateState { it.copy(glucose = v) }
    fun updateHbA1c(v: String) = updateState { it.copy(hba1c = v) }

    /**
     * UiState の更新と同時に、バリデーション (isValid) および 変更検知 (isChanged) を実行するヘルパー。
     */
    private fun updateState(reducer: (BatchInputUiState) -> BatchInputUiState) {
        updateUiState { current ->
            val partialNext = reducer(current)
            // 論理的なバリデーション結果を算出し、State に同期反映する
            val finalIsValid = BatchInputLogic.isValid(partialNext)
            val finalIsChanged = BatchInputLogic.isChanged(partialNext)
            
            partialNext.copy(
                isValid = finalIsValid,
                isChanged = finalIsChanged
            )
        }
    }

    /**
     * 入力された全カテゴリのデータを一括保存します。
     * 
     * 処理フロー：
     * 1. 入力値の形式・範囲バリデーションの実施。
     * 2. 指定された記録日時における既存データの重複チェック（上書き防止）。
     * 3. ロジック層での各カテゴリ Entity の生成。
     * 4. 各カテゴリのリポジトリメソッドを順次呼び出して保存を実行。
     * 5. 成功時の UI 通知（エフェクト送出・スナックバー表示）および入力値のクリア。
     */
    fun saveBatch() {
        val state = currentState
        val time = state.recordTime ?: return

        safeLaunch(
            operation = OP_SAVE_BATCH,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = requiredPersonId
            }
        ) {
            // 1. 全体バリデーション実行
            val validationResult = BatchInputLogic.validate(state)

            // 2. バリデーションエラーがある場合は例外を送出（ハンドラで UI 通知される）
            if (validationResult != BatchInputValidationResult.SUCCESS) {
                translateValidationResult(validationResult, state)
            }

            // 3. 重複チェック：同一日時の既存レコードがあるカテゴリを特定する
            val duplicateResIds = HealthProcessorRegistry.getAll()
                .filter { !it.isEmpty(state) }
                .filter { it.findExisting(healthRepository, requiredPersonId, time) != null }
                .map { it.categoryNameResId }

            // 重複がある場合は保存をブロックし、重複カテゴリ名をメッセージに含めて通知する
            if (duplicateResIds.isNotEmpty()) {
                val categoryNames = duplicateResIds.joinToString("、") { "__RES__$it" }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = R.string.batch_err_duplicate_blocked,
                    args = listOf(categoryNames),
                    logMessage = "Duplicate detected in categories index: $duplicateResIds"
                )
            }
            
            // 4. 保存の実行：入力のあるカテゴリの Entity を作成し、一括で保存する（トランザクション対応）
            val entities = BatchInputLogic.createEntities(requiredPersonId, time, state)
            healthRepository.insertHealthDataBatch(entities, featureName, OP_SAVE_BATCH)

            // 全保存成功時のイベント通知
            sendViewEvent(BatchInputViewEvent.SaveSuccessEffects)
            sendUiEvent(UiEvent.SaveSuccess())
            showSnackbar(R.string.batch_msg_save_success)
            
            // 入力値をクリアし、変更基準点を現在の入力値（保存に成功した日時）に更新して次の入力に備える
            updateUiState { current ->
                current.copy(
                    height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                    sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                    initialYear = current.year,
                    initialMonth = current.month,
                    initialDay = current.day,
                    initialHour = current.hour,
                    initialMinute = current.minute,
                    isChanged = false,
                    isValid = false
                )
            }
        }
    }

    /**
     * 一括入力特有のバリデーション結果を、適切なリソース ID と引数に翻訳して例外を送出します。
     */
    private fun translateValidationResult(result: BatchInputValidationResult, state: BatchInputUiState) {
        val messageRes = when (result) {
            BatchInputValidationResult.EMPTY_ALL -> R.string.p_detail_empty_records
            else -> R.string.common_error_save
        }

        val args = if (result == BatchInputValidationResult.INVALID_VALUE) {
            val details = HealthProcessorRegistry.getAll()
                .filter { !it.isEmpty(state) && it.validate(state) == HealthInputValidationResult.OUT_OF_RANGE }
                .map { "__RES__${it.outOfRangeErrorResId}" }
            
            if (details.isEmpty()) listOf("__RES__${R.string.common_error_invalid_input}") else listOf(details.joinToString("、"))
        } else {
            emptyList()
        }

        throw AppValidationException(
            titleResId = R.string.common_error_title_save,
            messageResId = messageRes,
            args = args,
            logMessage = "Validation failed: $result"
        )
    }

    /**
     * 前の画面に戻ります。
     */
    fun navigateBack() {
        sendViewEvent(BatchInputViewEvent.NavigateBack)
    }

    /**
     * BatchInputViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return BatchInputViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
