package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.AuditLog
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.AuditLogLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 操作ログ（監査ログ）閲覧画面用の ViewModel
 */
class AuditLogViewModel(
    auditLogRepository: AuditLogRepository,
    userSettingsRepository: UserSettingsRepository,
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "AuditLog"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    // 絞り込み・並び替え状態
    private val _selectedFeature = MutableStateFlow<String?>(null)
    val selectedFeature = _selectedFeature.asStateFlow()

    private val _selectedResult = MutableStateFlow<String?>(null)
    val selectedResult = _selectedResult.asStateFlow()

    private val _isAscending = MutableStateFlow(false)
    val isAscending = _isAscending.asStateFlow()

    // 絞り込み・並び替え済みのログリスト
    val auditLogs: StateFlow<List<AuditLog>> = combine(
        auditLogRepository.allLogs,
        _selectedFeature,
        _selectedResult,
        _isAscending,
    ) { logs, feature, result, ascending ->
        AuditLogLogic.filterAuditLogs(logs, feature, result, ascending)
    }.catch { e ->
        if (e is CancellationException) throw e
        coroutineErrorHandler.handleException(e, ErrorContext(featureName, "auditLogsFlow", "audit_log"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 存在する項目の一覧（フィルター選択用）
    val availableFeatures: StateFlow<List<String>> = auditLogRepository.allLogs
        .map { logs ->
            AuditLogLogic.extractAvailableFeatures(logs)
        }
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "availableFeaturesFlow"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableResults: StateFlow<List<String>> = auditLogRepository.allLogs
        .map { logs ->
            AuditLogLogic.extractAvailableResults(logs)
        }
        .catch { e ->
            if (e is CancellationException) throw e
            coroutineErrorHandler.handleException(e, ErrorContext(featureName, "availableResultsFlow"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFeatureFilter(feature: String?) {
        _selectedFeature.value = feature
    }

    fun setResultFilter(result: String?) {
        _selectedResult.value = result
    }

    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
    }

    fun clearFilters() {
        _selectedFeature.value = null
        _selectedResult.value = null
    }

    class Factory(
        private val auditLogRepository: AuditLogRepository,
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuditLogViewModel::class.java)) {
                return AuditLogViewModel(auditLogRepository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
