package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogLogic
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel：AuditLogViewModel
 */
class AuditLogViewModel(
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseUiStateViewModel<AuditLogUiState, AuditLogViewEvent>(
    userSettingsRepository,
    AuditLogUiState()
) {

    companion object {
        private const val FEATURE_NAME = "AuditLog"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }

        // 初期化完了後にデータ購読を開始
        scope.launch {
            startLogsObservation()
        }
    }

    private fun startLogsObservation() {
        safeCollect(
            operation = "auditLogsFlow",
            mode = CollectMode.INITIAL,
            loadingState = loadingStateProxy,
            contextBuilder = { tableName = "audit_log" },
            flowProvider = {
                val filterParamsFlow = uiState.map {
                    Triple(it.selectedFeature, it.selectedResult, it.isAscending)
                }.distinctUntilChanged()

                combine(
                    auditLogRepository.allLogs,
                    filterParamsFlow
                ) { logs, (feature, result, isAscending) ->
                    val filtered = AuditLogLogic.filterAndSortLogs(logs, feature, result, isAscending)
                    val features = AuditLogLogic.extractAvailableFeatures(logs)
                    val results = AuditLogLogic.extractAvailableResults(logs)
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

    fun navigateBack() {
        sendViewEvent(AuditLogViewEvent.NavigateBack)
    }

    class Factory(
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return AuditLogViewModel(auditLogRepository, userSettingsRepository) as T
        }
    }
}
