package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoOperation
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoScreenState
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoUiState
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoViewEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel：UnassignedPhotoViewModel
 *
 * 【役割】
 * データベースとの紐付けが失われた「未割り当て写真」の検出および削除操作を制御します。
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

    private var actionJob: Job? = null

    override fun copyWithLoadingState(state: UnassignedPhotoUiState, isLoading: Boolean, category: LoadingCategory): UnassignedPhotoUiState {
        return when (category) {
            is LoadingCategory.Structural -> {
                val nextScreenState = if (!isLoading) {
                    (state.screenState as? UnassignedPhotoScreenState.Error) ?: UnassignedPhotoScreenState.Active
                } else {
                    (state.screenState as? UnassignedPhotoScreenState.Active) ?: UnassignedPhotoScreenState.Loading
                }
                state.copy(screenState = nextScreenState)
            }
            is LoadingCategory.Operation -> {
                if (!isLoading) state.copy(operation = UnassignedPhotoOperation.Idle) else state
            }
            else -> state.copy(isLoading = isLoading)
        }
    }

    init {
        scope.launch {
            loadUnassignedPhotos()
        }
    }

    fun loadUnassignedPhotos() {
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(operation = "loadUnassignedPhotos", loadingCategory = LoadingCategory.Structural) {
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

        updateUiState { it.copy(operation = UnassignedPhotoOperation.Deleting) }

        actionJob = safeLaunch(operation = "deletePhoto", loadingCategory = LoadingCategory.Operation) {
            info.photoId?.let {
                conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
            }
            conditionRepository.deletePhotoFiles(info.photoFileName, info.thumbnailFileName)
            
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
