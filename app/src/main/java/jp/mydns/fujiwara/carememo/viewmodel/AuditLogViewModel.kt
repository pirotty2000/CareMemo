package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogLogic
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import kotlinx.coroutines.flow.combine

/**
 * 操作ログ（監査ログ）閲覧画面用の ViewModel
 */
class AuditLogViewModel(
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseUiStateViewModel<AuditLogUiState, AuditLogViewEvent>(
    userSettingsRepository,
    AuditLogUiState()
) {

    companion object {
        private const val FEATURE_NAME = "AuditLog"
    }

    override val featureName: String = FEATURE_NAME

    init {
        // 標準のエラーハンドラをセットアップ
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // ログの購読とフィルタリングの統合フロー
        safeCollect(
            operation = "auditLogsFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = "audit_log" },
            flowProvider = {
                combine(
                    auditLogRepository.allLogs,
                    uiState // selectedFeature, selectedResult, isAscending を含む
                ) { logs, state ->
                    val filtered = AuditLogLogic.filterAndSortLogs(
                        logs,
                        state.selectedFeature,
                        state.selectedResult,
                        state.isAscending
                    )
                    val features = AuditLogLogic.extractAvailableFeatures(logs)
                    val results = AuditLogLogic.extractAvailableResults(logs)
                    
                    // 内部データを一括で構築
                    Triple(filtered, features, results)
                }
            }
        ) { (filtered, features, results) ->
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
        return state.copy(isLoading = isLoading)
    }

    fun setFeatureFilter(feature: String?) {
        updateUiState { it.copy(selectedFeature = feature) }
    }

    fun setResultFilter(result: String?) {
        updateUiState { it.copy(selectedResult = result) }
    }

    fun toggleSortOrder() {
        updateUiState { it.copy(isAscending = !it.isAscending) }
    }

    fun clearFilters() {
        updateUiState { it.copy(selectedFeature = null, selectedResult = null) }
    }

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
