package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogLogic
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * ViewModel：AuditLogViewModel
 *
 * 【役割】
 * 操作ログ（監査ログ）閲覧画面における状態管理と実行制御を担当します。
 * アプリ内で行われた重要な操作履歴の取得、フィルタリング、ソート、および表示用メタデータの構築を行います。
 *
 * 【主要な機能】
 * ・監査ログの継続的な購読と UI 状態への反映。
 * ・機能名（Feature）や処理結果（Result）によるログの動的フィルタリング。
 * ・日時に基づく昇順・降順のソート切り替え。
 * ・ログ内に存在するユニークな機能一覧・結果一覧の自動抽出（フィルタ選択肢用）。
 *
 * 【依存している Repository】
 * ・AuditLogRepository: 保存された操作ログデータの取得。
 * ・UserSettingsRepository: 氏名のマスキング設定等の参照（BaseUiStateViewModel 経由）。
 *
 * 【依存している Logic】
 * ・AuditLogLogic: 大量のログデータに対するフィルタリング、ソート、選択肢抽出の純粋ロジック。
 *
 * 【設計指針】
 * 1. リアクティブ：`auditLogRepository.allLogs` と `uiState` 内のフィルタ条件を `combine` し、
 *    いずれかが変更された際に自動的に再フィルタリングが行われるフローを構築する。
 * 2. 効率性：フィルタリングや抽出ロジックは `safeCollect` 内のコルーチン上で実行し、
 *    メインスレッド（UI描画）を妨げないようにする。
 */
class AuditLogViewModel(
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseUiStateViewModel<AuditLogUiState, AuditLogViewEvent>(
    userSettingsRepository,
    AuditLogUiState()
) {

    companion object {
        /** 監査ログ・例外用：機能名 */
        private const val FEATURE_NAME = "AuditLog"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        // ログ閲覧自体でエラーが発生した場合の通知に使用
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // ログの購読とフィルタリングの統合フロー
        // フィルタ条件（UI状態の特定プロパティ）または ログ本体（Repository）のいずれかが更新されたら再計算を行う
        safeCollect(
            operation = "auditLogsFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = "audit_log" },
            flowProvider = {
                // 再計算のトリガーを「フィルタ条件の変更」のみに限定し、無限ループと無駄な計算を防止する
                val filterParamsFlow = uiState.map {
                    Triple(it.selectedFeature, it.selectedResult, it.isAscending)
                }.distinctUntilChanged()

                combine(
                    auditLogRepository.allLogs,
                    filterParamsFlow
                ) { logs, (feature, result, isAscending) ->
                    // 1. ロジック層へフィルタリングとソートを委譲
                    val filtered = AuditLogLogic.filterAndSortLogs(
                        logs,
                        feature,
                        result,
                        isAscending
                    )
                    // 2. フィルタ選択肢として使用するユニークな機能名・結果タイプを抽出
                    val features = AuditLogLogic.extractAvailableFeatures(logs)
                    val results = AuditLogLogic.extractAvailableResults(logs)

                    // 3. 処理結果をまとめて返す
                    Triple(filtered, features, results)
                }
            }
        ) { (filtered, features, results) ->
            // 計算結果を原子的に UI 状態へ反映
            updateUiState { current ->
                current.copy(
                    auditLogs = filtered,
                    availableFeatures = features,
                    availableResults = results
                )
            }
        }
    }

    override fun copyWithLoadingState(state: AuditLogUiState, isLoading: Boolean): AuditLogUiState {
        // ローディング状態のコピーを作成
        return state.copy(isLoading = isLoading)
    }

    /**
     * 機能名によるフィルタ条件を設定します。
     *
     * @param feature 機能名。null の場合は全表示。
     */
    fun setFeatureFilter(feature: String?) {
        updateUiState { it.copy(selectedFeature = feature) }
    }

    /**
     * 実行結果によるフィルタ条件を設定します。
     *
     * @param result 結果タイプ（SUCCESS, ERROR 等）。null の場合は全表示。
     */
    fun setResultFilter(result: String?) {
        updateUiState { it.copy(selectedResult = result) }
    }

    /**
     * 日時のソート順（昇順/降順）を切り替えます。
     */
    fun toggleSortOrder() {
        updateUiState { it.copy(isAscending = !it.isAscending) }
    }

    /**
     * 全てのフィルタ条件（機能名・実行結果）をリセットします。
     */
    fun clearFilters() {
        updateUiState { it.copy(selectedFeature = null, selectedResult = null) }
    }

    /**
     * AuditLogViewModel を生成するための Factory クラス。
     */
    class Factory(
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuditLogViewModel(auditLogRepository, userSettingsRepository) as T
        }
    }
}
