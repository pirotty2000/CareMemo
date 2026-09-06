package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogLogic
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogScreenState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogUiState
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogViewEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel：AuditLogViewModel
 *
 * 【役割】
 * 監査ログ（操作履歴）の表示、フィルタリング、およびソート状態を管理します。
 */
class AuditLogViewModel(
    private val auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession
) : BaseUiStateViewModel<AuditLogUiState, AuditLogViewEvent>(
    userSettingsRepository,
    securitySession,
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
            loadingCategory = LoadingCategory.Structural,
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
                }.catch { e ->
                    updateUiState { it.copy(screenState = AuditLogScreenState.Error(e)) }
                    throw e
                }
            }
        ) { (filtered, features, results) ->
            updateUiState { current ->
                current.copy(
                    filteredLogs = filtered.toImmutableList(),
                    availableFeatures = features.toImmutableList(),
                    availableResults = results.toImmutableList()
                )
            }
        }
    }

    override fun copyWithLoadingState(state: AuditLogUiState, isLoading: Boolean, category: LoadingCategory): AuditLogUiState {
        return when (category) {
            is LoadingCategory.Structural -> {
                val nextScreenState = if (!isLoading) {
                    if (state.screenState is AuditLogScreenState.Error) state.screenState else AuditLogScreenState.Active
                } else {
                    if (state.screenState is AuditLogScreenState.Active) state.screenState else AuditLogScreenState.Loading
                }
                state.copy(screenState = nextScreenState)
            }
            else -> state.copy(isLoading = isLoading)
        }
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
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return AuditLogViewModel(
                auditLogRepository,
                userSettingsRepository,
                securitySession
            ) as T
        }
    }
}
