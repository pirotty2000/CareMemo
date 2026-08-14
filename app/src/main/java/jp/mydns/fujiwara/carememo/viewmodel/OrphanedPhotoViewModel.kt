package jp.mydns.fujiwara.carememo.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UI State：OrphanedPhotoUiState
 */
@Immutable
data class OrphanedPhotoUiState(
    val orphanedPhotos: ImmutableList<OrphanedPhotoInfo> = persistentListOf(),
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

    /** 処理実行用の Job */
    private var actionJob: Job? = null

    override fun copyWithLoadingState(state: OrphanedPhotoUiState, isLoading: Boolean): OrphanedPhotoUiState {
        return state.copy(isLoading = isLoading)
    }

    init {
        // 初期化完了後にロード
        scope.launch {
            loadOrphanedPhotos()
        }
    }

    fun loadOrphanedPhotos() {
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(operation = "loadOrphanedPhotos", loadingState = loadingStateProxy) {
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

            val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            updateUiState { it.copy(orphanedPhotos = orphaned.toImmutableList()) }
        }
    }

    fun deletePhoto(info: OrphanedPhotoInfo) {
        if (actionJob?.isActive == true) return

        actionJob = safeLaunch(operation = "deletePhoto", loadingState = loadingStateProxy) {
            info.photoId?.let {
                conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
            }
            ImageUtils.deleteImageFiles(context, info.photoFileName, info.thumbnailFileName)
            
            // リロード処理をインラインで実行（actionJob が自分自身なので、loadOrphanedPhotos() 呼び出しは skip されるため）
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

            val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            updateUiState { it.copy(orphanedPhotos = orphaned.toImmutableList()) }
        }
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
