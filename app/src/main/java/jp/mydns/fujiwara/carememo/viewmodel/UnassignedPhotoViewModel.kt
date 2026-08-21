package jp.mydns.fujiwara.carememo.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UI State：UnassignedPhotoUiState
 */
@Immutable
data class UnassignedPhotoUiState(
    val unassignedPhotos: ImmutableList<UnassignedPhotoInfo> = persistentListOf(),
    val isLoading: Boolean = false
)

/**
 * View Event：UnassignedPhotoViewEvent
 */
sealed interface UnassignedPhotoViewEvent {
    data object NavigateBack : UnassignedPhotoViewEvent
}

/**
 * ViewModel：UnassignedPhotoViewModel
 *
 * 【役割】
 * データベースとの紐付けが失われた「未割り当て写真」の検出および削除操作を制御します。
 *
 * 【設計指針：レイヤー責務と課題】
 * 1. メンテナンス制御：ファイルシステムと DB レコードの不整合を解消するための特殊な操作を安全に実行します。
 * 2. 疎結合：ファイルシステムへの直接アクセスをリポジトリ層に委譲し、ViewModel はプラットフォーム（Android Context 等）に依存しない純粋な業務ロジックに集中します。
 *
 * 【この ViewModel では行わないこと】
 * ・未割り当て写真の具体的な検出アルゴリズム（ConditionMaintenanceLogic が担当）。
 */
class UnassignedPhotoViewModel(
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    private val conditionRepository: ConditionRepository
) : BaseUiStateViewModel<UnassignedPhotoUiState, UnassignedPhotoViewEvent>(
    userSettingsRepository,
    securitySession,
    UnassignedPhotoUiState()
) {

    override val featureName: String = "UnassignedPhotoManagement"

    /** 処理実行用の Job */
    private var actionJob: Job? = null

    override fun copyWithLoadingState(state: UnassignedPhotoUiState, isLoading: Boolean): UnassignedPhotoUiState {
        return state.copy(isLoading = isLoading)
    }

    init {
        // 初期化完了後にロード
        scope.launch {
            loadUnassignedPhotos()
        }
    }

    fun loadUnassignedPhotos() {
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(operation = "loadUnassignedPhotos", loadingState = loadingStateProxy) {
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val physicalFiles = conditionRepository.getPhotoPhysicalFiles()

            val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            updateUiState { it.copy(unassignedPhotos = unassigned.toImmutableList()) }
        }
    }

    fun deletePhoto(info: UnassignedPhotoInfo) {
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(operation = "deletePhoto", loadingState = loadingStateProxy) {
            info.photoId?.let {
                conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
            }
            conditionRepository.deletePhotoFiles(info.photoFileName, info.thumbnailFileName)
            
            // リロード処理をインラインで実行
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val physicalFiles = conditionRepository.getPhotoPhysicalFiles()

            val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            updateUiState { it.copy(unassignedPhotos = unassigned.toImmutableList()) }
        }
    }

    fun navigateBack() {
        sendViewEvent(UnassignedPhotoViewEvent.NavigateBack)
    }

    class Factory(
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val conditionRepository: ConditionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return UnassignedPhotoViewModel(
                userSettingsRepository,
                securitySession,
                conditionRepository
            ) as T
        }
    }
}
