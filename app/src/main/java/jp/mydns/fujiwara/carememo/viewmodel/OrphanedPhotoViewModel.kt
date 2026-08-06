package jp.mydns.fujiwara.carememo.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.coroutines.launch

/**
 * UI State：OrphanedPhotoUiState
 */
data class OrphanedPhotoUiState(
    val orphanedPhotos: List<OrphanedPhotoInfo> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * View Event：OrphanedPhotoViewEvent
 */
sealed interface OrphanedPhotoViewEvent {
    data object NavigateBack : OrphanedPhotoViewEvent
}

/**
 * ViewModel：OrphanedPhotoViewModel
 */
class OrphanedPhotoViewModel(
    userSettingsRepository: UserSettingsRepository,
    private val conditionRepository: ConditionRepository,
    @param:SuppressLint("StaticFieldLeak")
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context
) : BaseUiStateViewModel<OrphanedPhotoUiState, OrphanedPhotoViewEvent>(userSettingsRepository, OrphanedPhotoUiState()) {

    override val featureName: String = "OrphanedPhotoManagement"

    override fun copyWithLoadingState(state: OrphanedPhotoUiState, isLoading: Boolean): OrphanedPhotoUiState {
        return state.copy(isLoading = isLoading)
    }

    init {
        // 初期化完了後にロード
        scope.launch {
            loadOrphanedPhotos()
        }
    }

    fun loadOrphanedPhotos() = safeLaunch(operation = "loadOrphanedPhotos", loadingState = loadingStateProxy) {
        val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
        val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(
            dbPhotos = dbPhotos,
            existingConditionIds = existingConditionIds,
            physicalFiles = physicalFiles
        )

        updateUiState { it.copy(orphanedPhotos = orphaned) }
    }

    fun deletePhoto(info: OrphanedPhotoInfo) = safeLaunch(operation = "deletePhoto", loadingState = loadingStateProxy) {
        info.photoId?.let {
            conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
        }
        ImageUtils.deleteImageFiles(context, info.photoFileName, info.thumbnailFileName)
        loadOrphanedPhotos()
    }

    fun navigateBack() {
        sendViewEvent(OrphanedPhotoViewEvent.NavigateBack)
    }

    class Factory(
        private val userSettingsRepository: UserSettingsRepository,
        private val conditionRepository: ConditionRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return OrphanedPhotoViewModel(
                userSettingsRepository,
                conditionRepository,
                context
            ) as T
        }
    }
}
