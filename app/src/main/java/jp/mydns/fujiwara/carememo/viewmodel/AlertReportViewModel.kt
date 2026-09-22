package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.feature.*
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import kotlinx.collections.immutable.toImmutableList

/**
 * ViewModel：AlertReportViewModel
 *
 * 【役割】
 * アラート・レポート画面の状態管理と実行制御を担当します。
 * 全利用者の健康データから異常をスキャンし、フィルタリングされたアラート一覧を提供します。
 */
class AlertReportViewModel(
    savedStateHandle: SavedStateHandle,
    private val personRepository: PersonRepository,
    private val healthRepository: HealthRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
) : BaseUiStateViewModel<AlertReportUiState, AlertReportViewEvent>(
    userSettingsRepository,
    securitySession,
    AlertReportUiState()
) {
    override val featureName: String = "AlertReport"

    /** 特定の利用者に絞り込むための ID (null なら全件) */
    private val personId: String? = try {
        savedStateHandle.toRoute<Destination.AlertReport>().personId
    } catch (e: Exception) {
        // 万が一 toRoute が失敗した場合のフォールバック
        android.util.Log.w("AlertReportVM", "Failed to extract personId via toRoute", e)
        null
    }

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
        refreshReport()
    }

    /**
     * アラートの一覧を再取得（スキャン）します。
     */
    fun refreshReport() {
        safeLaunch(operation = "refreshReport", loadingCategory = LoadingCategory.Structural) {
            val logic = AlertReportLogic(personRepository, healthRepository)
            val isMasking = isNameMaskingEnabled.value
            
            // スキャン
            val allAlerts = logic.scanAlerts(isNameMaskingEnabled = isMasking)
            
            // 絞り込み (URL 引数で指定された ID があれば適用)
            val baseAlerts = if (personId != null) {
                allAlerts.filter { it.personId == personId }
            } else {
                allAlerts
            }

            updateUiState { 
                it.copy(
                    screenState = AlertReportScreenState.Active,
                    alerts = baseAlerts.toImmutableList(),
                    filteredAlerts = filterAlerts(baseAlerts, it.filterType).toImmutableList()
                )
            }
        }
    }

    /**
     * フィルタ種別を変更します。
     */
    fun setFilterType(filterType: AlertFilterType) {
        updateUiState { 
            it.copy(
                filterType = filterType,
                filteredAlerts = filterAlerts(it.alerts, filterType).toImmutableList()
            )
        }
    }

    /**
     * 指定されたアラートの詳細（健康記録画面等）へ遷移します。
     */
    fun navigateToDetail(alert: AlertItem) {
        sendViewEvent(AlertReportViewEvent.NavigateToDetail(alert.personId, alert.category))
    }

    private fun filterAlerts(alerts: List<AlertItem>, filterType: AlertFilterType): List<AlertItem> {
        return when (filterType) {
            AlertFilterType.ALL -> alerts
            AlertFilterType.VITAL -> alerts.filter { it.category == Category.BP_AND_PULSE }
            AlertFilterType.WEIGHT -> alerts.filter { it.category == Category.HEIGHT_AND_WEIGHT }
            AlertFilterType.GLUCOSE -> alerts.filter { it.category == Category.GLUCOSE_AND_HBA1C }
        }
    }

    override fun copyWithLoadingState(state: AlertReportUiState, isLoading: Boolean, category: LoadingCategory): AlertReportUiState {
        return when (category) {
            is LoadingCategory.Structural -> state.copy(screenState = if (isLoading) AlertReportScreenState.Loading else state.screenState)
            is LoadingCategory.Operation -> state.copy(operation = if (isLoading) AlertReportOperation.Scanning else AlertReportOperation.Idle)
            else -> state
        }
    }

    /**
     * Factory：AlertReportViewModel.Factory
     */
    class Factory(
        private val personRepository: PersonRepository,
        private val healthRepository: HealthRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val handle = extras.createSavedStateHandle()
            return AlertReportViewModel(
                handle,
                personRepository,
                healthRepository,
                userSettingsRepository,
                securitySession,
                auditLogRepository
            ) as T
        }
    }
}
