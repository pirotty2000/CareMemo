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
import jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo
import jp.mydns.fujiwara.carememo.utils.ImageUtils
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
 * 2. プラットフォーム依存（注意）: コンストラクタで `Context` を受け取っており、本来はリポジトリまたは
 *    ファイル操作ユーティリティに隠蔽されるべきプラットフォーム依存が露出しています。将来のリファクタリング対象です。
 *
 * 【この ViewModel では行わないこと】
 * ・未割り当て写真の具体的な検出アルゴリズム（ConditionMaintenanceLogic が担当）。
 */
class UnassignedPhotoViewModel(
    userSettingsRepository: UserSettingsRepository,
    private val conditionRepository: ConditionRepository,
    @param:SuppressLint("StaticFieldLeak")
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context
) : BaseUiStateViewModel<UnassignedPhotoUiState, UnassignedPhotoViewEvent>(userSettingsRepository, UnassignedPhotoUiState()) {

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
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

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
            ImageUtils.deleteImageFiles(context, info.photoFileName, info.thumbnailFileName)
            
            // リロード処理をインラインで実行（actionJob が自分自身なので、loadUnassignedPhotos() 呼び出しは skip されるため）
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val photosDir = ImageUtils.getPhotosDirPublic(context)
            val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

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
        private val conditionRepository: ConditionRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return UnassignedPhotoViewModel(
                userSettingsRepository,
                conditionRepository,
                context
            ) as T
        }
    }
}
