package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthInputValidationResult
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputCategory
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputUiState
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.BatchInputViewEvent
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel：BatchInputViewModel
 *
 * 【役割】
 * 健康記録の一括入力画面（SCR-PH-002）における状態管理と保存実行を制御します。
 * 身長体重、バイタル、血糖値の複数カテゴリにわたる入力内容を同時に扱い、整合性を保ちながら保存します。
 *
 * 【主要な機能】
 * ・複数カテゴリ（身長体重、バイタル、血糖）の入力状態の保持。
 * ・全カテゴリ一括でのバリデーション（範囲チェック等）の実施。
 * ・保存前の「同一日時データ」の重複チェック（上書き防止仕様）。
 * ・成功時の一括クリア処理と、UI側への演出イベント通知。
 *
 * 【依存している Repository】
 * ・HealthRepository: 各健康データの取得（重複チェック）および保存。
 * ・PersonRepository: 対象利用者の基本情報取得（PersonBaseUiStateViewModel 経由）。
 * ・PersonSummaryRepository: 既存データの記録状況サマリー取得。
 * ・UserSettingsRepository: 共通設定の参照。
 * ・AuditLogRepository: 操作履歴の記録。
 *
 * 【依存している Logic】
 * ・BatchInputLogic: 一括入力固有の相関バリデーション、Entity 生成、変更検知。
 * ・HealthLogic: 個別の数値項目に対する範囲チェック。
 *
 * 【設計指針】
 * 1. 整合性の優先：保存前に、入力された全カテゴリに対して同一日時の既存レコードがないかを確認する。
 *    一括入力画面は「新規追記」を主目的とするため、既存データがある場合は保存をブロック（重複エラー）する。
 * 2. 単純性の維持：DAO 層に複数テーブルを跨ぐ insertAll が存在しないため、現状は ViewModel 内でループして
 *    順次 insert を実行する。
 * 3. 将来の課題：ループ保存中に一部が失敗した場合のロールバックを実現するため、
 *    将来的にリポジトリ層で DB トランザクションを張る仕組みへの移行を検討する。
 */
class BatchInputViewModel(
    private val healthRepository: HealthRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : PersonBaseUiStateViewModel<BatchInputUiState, BatchInputViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    auditLogRepository,
    BatchInputUiState()
) {

    companion object {
        /** 監査ログ用機能名 */
        private const val FEATURE_NAME = "BatchInput"
        /** 監査ログ用操作名：一括保存 */
        private const val OP_SAVE_BATCH = "OP_SAVE_BATCH"
        /** 監査ログ用対象テーブル（概念的なグループ名） */
        private const val TABLE_HEALTH = "health_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 共通設定（氏名マスキング）の購読と UI 状態への反映
        scope.launch {
            isNameMaskingEnabled.collect { enabled ->
                updateUiState { it.copy(isNameMaskingEnabled = enabled) }
            }
        }
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
        // 利用者が切り替わった場合は入力をリセットし、日時を現在時刻にする
        val isDifferentPerson = state.personId != person.id
        val next = if (isDifferentPerson) {
            val now = Instant.now()
            state.copy(
                personId = person.id,
                person = person,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary,
                height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                recordTime = now,
                initialRecordTime = now
            )
        } else {
            state.copy(
                personId = person.id,
                currentPersonName = person.getMaskedName(state.isNameMaskingEnabled),
                personSummary = summary
            )
        }
        // 派生状態（isValid, isChanged）を再計算
        return next.copy(
            isValid = BatchInputLogic.isValid(next),
            isChanged = BatchInputLogic.isChanged(next)
        )
    }

    // --- UI 入力更新用メソッド群 (原子的な一括更新) ---

    fun setRecordTime(time: Instant) = updateState { it.copy(recordTime = time) }
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
     * 状態更新時に自動的に派生状態（バリデーション成否、変更有無）を計算します。
     */
    private fun updateState(reducer: (BatchInputUiState) -> BatchInputUiState) {
        updateUiState { current ->
            val next = reducer(current)
            next.copy(
                isValid = BatchInputLogic.isValid(next),
                isChanged = BatchInputLogic.isChanged(next)
            )
        }
    }

    /**
     * 入力された全データを一括保存します。
     * 
     * 実行順序：
     * 1. 共通バリデーション (BatchInputLogic.validate)
     * 2. 重複チェック (既存レコードの存在確認)
     * 3. 個別 Entity の保存 (healthRepository 内でのループ実行)
     * 4. 成功通知および UI 状態のリセット
     * 
     * 【AuditLog の取り扱い】
     * ・Repository 内で各項目（身長、バイタル等）ごとに個別の INSERT ログが発行されます。
     * ・affectedId には "PersonId" が設定されますが、将来的には
     *   "PersonId|Count:3|Types:H,V,G" のように詳細なサマリーを含めることを検討してください。
     */
    fun saveBatch() {
        val state = currentState
        val time = state.recordTime

        safeLaunch(
            operation = OP_SAVE_BATCH,
            loadingState = loadingStateProxy,
            contextBuilder = {
                tableName = TABLE_HEALTH
                affectedId = requiredPersonId
            }
        ) {
            // 1. バリデーション（事実の判定）
            val validationResult = BatchInputLogic.validate(state)

            // 2. 失敗時の翻訳と例外スロー
            if (validationResult != BatchInputValidationResult.SUCCESS) {
                translateValidationResult(validationResult, state)
            }

            // 3. 重複チェック（画面仕様：既存データがある場合は上書きせずブロックする）
            val effectiveCategories = BatchInputLogic.getEffectiveCategories(state)
            val duplicateResIds = mutableListOf<Int>()

            effectiveCategories.forEach { category ->
                val isDuplicate = when (category) {
                    BatchInputCategory.HEIGHT_WEIGHT -> healthRepository.findHeightAndWeightAtTime(requiredPersonId, time) != null
                    BatchInputCategory.VITAL -> healthRepository.findBpAndPulseAtTime(requiredPersonId, time) != null
                    BatchInputCategory.GLUCOSE -> healthRepository.findGlucoseAndHbA1cAtTime(requiredPersonId, time) != null
                }
                if (isDuplicate) {
                    duplicateResIds.add(
                        when (category) {
                            BatchInputCategory.HEIGHT_WEIGHT -> R.string.common_category_height_weight
                            BatchInputCategory.VITAL -> R.string.common_category_vital
                            BatchInputCategory.GLUCOSE -> R.string.common_category_glucose
                        }
                    )
                }
            }

            // いずれかのカテゴリで重複があればエラーとして中断
            if (duplicateResIds.isNotEmpty()) {
                val categoryNames = duplicateResIds.joinToString("、") { "__RES__$it" }
                throw AppValidationException(
                    titleResId = R.string.common_error_title_save,
                    messageResId = R.string.batch_err_duplicate_blocked,
                    args = listOf(categoryNames),
                    logMessage = "Duplicate detected in categories index: $duplicateResIds"
                )
            }
            
            // 4. 保存実行（各 Repository メソッドを順次呼び出し）
            // 内部で Repository が AuditLog.log (SUCCESS) を発行します。
            val entities = BatchInputLogic.createEntities(requiredPersonId, time, state)
            entities.forEach { entity ->
                when (entity) {
                    is HeightAndWeight -> healthRepository.insertHeightAndWeight(entity, featureName, OP_SAVE_BATCH)
                    is BpAndPulse -> healthRepository.insertBpAndPulse(entity, featureName, OP_SAVE_BATCH)
                    is GlucoseAndHbA1c -> healthRepository.insertGlucoseAndHbA1c(entity, featureName, OP_SAVE_BATCH)
                }
            }

            // 全て成功：成功イベント通知とスナックバー表示
            sendViewEvent(BatchInputViewEvent.SaveSuccessEffects)
            sendUiEvent(UiEvent.SaveSuccess)
            showSnackbar(R.string.batch_msg_save_success)
            
            // 保存後のリセット（日時は保持、数値はクリア、変更基準点を現在に更新）
            updateUiState { current ->
                current.copy(
                    height = "", weight = "", bpSystolic = "", bpDiastolic = "",
                    sat = "", pulse = "", bodyTemperature = "", glucose = "", hba1c = "",
                    initialRecordTime = current.recordTime,
                    isChanged = false,
                    isValid = false
                )
            }
        }
    }

    /**
     * バリデーション失敗理由をリソース ID および詳細メッセージに翻訳します。
     */
    private fun translateValidationResult(result: BatchInputValidationResult, state: BatchInputUiState) {
        val messageRes = when (result) {
            BatchInputValidationResult.EMPTY_ALL -> R.string.p_detail_empty_records
            else -> R.string.common_error_save
        }

        val args = if (result == BatchInputValidationResult.INVALID_VALUE) {
            val details = mutableListOf<String>()
            if (HealthLogic.validateHeightAndWeight(state.height, state.weight) == HealthInputValidationResult.OUT_OF_RANGE) details.add("身長・体重が範囲外です")
            if (HealthLogic.validateBpAndPulse(state.bpSystolic, state.bpDiastolic, state.sat, state.pulse, state.bodyTemperature) == HealthInputValidationResult.OUT_OF_RANGE) details.add("バイタルが範囲外です")
            if (HealthLogic.validateGlucoseAndHbA1c(state.glucose, state.hba1c) == HealthInputValidationResult.OUT_OF_RANGE) details.add("血糖値が範囲外です")
            
            if (details.isEmpty()) listOf("入力値が正しくありません") else listOf(details.joinToString("\n"))
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
     * BatchInputViewModel 生成用の Factory クラス。
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
            return BatchInputViewModel(
                healthRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                auditLogRepository
            ) as T
        }
    }
}
