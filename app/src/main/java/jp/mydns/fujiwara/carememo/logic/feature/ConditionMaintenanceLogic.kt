package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File
import java.time.Instant

/**
 * UI State：UnassignedPhotoUiState
 */
@Immutable
data class UnassignedPhotoUiState(
    val screenState: UnassignedPhotoScreenState = UnassignedPhotoScreenState.Loading,
    val operation: UnassignedPhotoOperation = UnassignedPhotoOperation.Idle,

    val unassignedPhotos: ImmutableList<UnassignedPhotoInfo> = persistentListOf(),

    @Deprecated("Use screenState")
    val isLoading: Boolean = false
)

/**
 * 構造的状態 (Structural State)
 */
sealed interface UnassignedPhotoScreenState {
    data object Loading : UnassignedPhotoScreenState
    data object Active : UnassignedPhotoScreenState
    data class Error(val throwable: Throwable) : UnassignedPhotoScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface UnassignedPhotoOperation {
    data object Idle : UnassignedPhotoOperation
    data object Deleting : UnassignedPhotoOperation
}

/**
 * View Event：UnassignedPhotoViewEvent
 */
sealed interface UnassignedPhotoViewEvent {
    data object NavigateBack : UnassignedPhotoViewEvent
}

/**
 * Logic：ConditionMaintenanceLogic
 *
 * 【役割】
 * 所見メモに関連するデータの整合性維持（メンテナンス）に関するドメインロジックを提供します。
 */
object ConditionMaintenanceLogic {

    /**
     * DBレコードと物理ファイルを突き合わせ、未割り当て写真を特定・分類します。
     */
    fun identifyUnassignedPhotos(
        dbPhotos: List<ConditionPhoto>,
        existingConditionIds: Set<String>,
        physicalFiles: List<File>
    ): List<UnassignedPhotoInfo> {
        val results = mutableListOf<UnassignedPhotoInfo>()
        val dbPhotoNames = dbPhotos.map { it.photoFileName }.toSet()

        // 1. DBレコードベースの分類 (TEMPORARY, UNASSIGNED_RECORD)
        dbPhotos.forEach { dbPhoto ->
            val type = when {
                IdLogic.isNew(dbPhoto.conditionId) -> UnassignedPhotoType.TEMPORARY
                dbPhoto.conditionId !in existingConditionIds -> UnassignedPhotoType.UNASSIGNED_RECORD
                else -> null // 正常な紐付け
            }

            if (type != null) {
                results.add(
                    UnassignedPhotoInfo(
                        type = type,
                        photoId = dbPhoto.id,
                        personId = dbPhoto.personId,
                        photoFileName = dbPhoto.photoFileName,
                        thumbnailFileName = dbPhoto.thumbnailFileName,
                        capturedAt = dbPhoto.capturedAt,
                        descriptionResId = when (type) {
                            UnassignedPhotoType.TEMPORARY -> R.string.unassigned_photo_type_temporary
                            UnassignedPhotoType.UNASSIGNED_RECORD -> R.string.unassigned_photo_type_unassigned
                            else -> 0
                        }
                    )
                )
            }
        }

        // 2. 物理ファイルベースの分類 (FILE_ONLY)
        physicalFiles.filter { it.name.startsWith("img_") }.forEach { file ->
            if (file.name !in dbPhotoNames) {
                results.add(
                    UnassignedPhotoInfo(
                        type = UnassignedPhotoType.FILE_ONLY,
                        photoId = null,
                        personId = null,
                        photoFileName = file.name,
                        thumbnailFileName = "thumb_" + file.name.removePrefix("img_"),
                        capturedAt = Instant.ofEpochMilli(file.lastModified()),
                        descriptionResId = R.string.unassigned_photo_db_unregistered
                    )
                )
            }
        }

        return results.sortedByDescending { it.capturedAt }
    }
}
