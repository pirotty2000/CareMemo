package jp.mydns.fujiwara.carememo.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.utils.ImageUtils

/**
 * 迷子写真管理画面の UI 状態
 */
data class OrphanedPhotoUiState(
    val orphanedPhotos: List<OrphanedPhotoInfo> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * 迷子写真管理画面の ViewModel (SCR-S-004)
 */
class OrphanedPhotoViewModel(
    userSettingsRepository: UserSettingsRepository,
    private val conditionRepository: ConditionRepository,
    @SuppressLint("StaticFieldLeak") private val context: Context // アプリケーションコンテキストを想定
) : BaseUiStateViewModel<OrphanedPhotoUiState, Unit>(userSettingsRepository, OrphanedPhotoUiState()) {

    override val featureName: String = "OrphanedPhotoManagement"

    override fun copyWithLoadingState(state: OrphanedPhotoUiState, isLoading: Boolean): OrphanedPhotoUiState {
        return state.copy(isLoading = isLoading)
    }

    init {
        loadOrphanedPhotos()
    }

    /**
     * 迷子写真をロードして特定します。
     */
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

    /**
     * 迷子写真を物理削除します。
     */
    fun deletePhoto(info: OrphanedPhotoInfo) = safeLaunch(operation = "deletePhoto", loadingState = loadingStateProxy) {
        // 1. DBから削除 (レコードがある場合)
        info.photoId?.let {
            conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
        }

        // 2. 物理ファイルを削除
        ImageUtils.deleteImageFiles(context, info.photoFileName, info.thumbnailFileName)

        // リロード
        loadOrphanedPhotos()
    }
}
