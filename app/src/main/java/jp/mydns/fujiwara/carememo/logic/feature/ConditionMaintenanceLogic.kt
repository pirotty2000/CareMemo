package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import java.io.File
import java.time.Instant

/**
 * 迷子写真の分類
 */
enum class OrphanedPhotoType {
    /** DBレコードはあるが、condition_id が空（一時保存のまま放置） */
    TEMPORARY,
    /** DBレコードはあるが、親の所見メモが存在しない */
    ORPHANED_RECORD,
    /** 物理ファイルはあるが、DBレコードが存在しない */
    FILE_ONLY
}

/**
 * 迷子写真の情報
 */
data class OrphanedPhotoInfo(
    val type: OrphanedPhotoType,
    val photoId: String?,           // DBにある場合のみ
    val personId: String?,          // DBにある場合のみ
    val photoFileName: String,      // 物理ファイル名
    val thumbnailFileName: String?,
    val capturedAt: Instant,
    val description: String
)

/**
 * メンテナンス機能（迷子写真管理）のドメインロジック
 */
object ConditionMaintenanceLogic {

    /**
     * DBレコードと物理ファイルを突き合わせ、迷子写真を特定・分類します。
     * ※このメソッドは純粋ロジックであり、I/O結果を引数として受け取ります。
     */
    fun identifyOrphanedPhotos(
        dbPhotos: List<ConditionPhoto>,
        existingConditionIds: Set<String>,
        physicalFiles: List<File>
    ): List<OrphanedPhotoInfo> {
        val results = mutableListOf<OrphanedPhotoInfo>()
        val dbPhotoNames = dbPhotos.map { it.photoFileName }.toSet()

        // 1. DBレコードベースの分類 (TEMPORARY, ORPHANED_RECORD)
        dbPhotos.forEach { dbPhoto ->
            val type = when {
                dbPhoto.conditionId.isEmpty() -> OrphanedPhotoType.TEMPORARY
                dbPhoto.conditionId !in existingConditionIds -> OrphanedPhotoType.ORPHANED_RECORD
                else -> null // 正常
            }

            if (type != null) {
                results.add(
                    OrphanedPhotoInfo(
                        type = type,
                        photoId = dbPhoto.id,
                        personId = dbPhoto.personId,
                        photoFileName = dbPhoto.photoFileName,
                        thumbnailFileName = dbPhoto.thumbnailFileName,
                        capturedAt = dbPhoto.capturedAt,
                        description = when (type) {
                            OrphanedPhotoType.TEMPORARY -> "一時保存中の写真"
                            OrphanedPhotoType.ORPHANED_RECORD -> "紐付け先が消失した写真"
                            else -> ""
                        }
                    )
                )
            }
        }

        // 2. 物理ファイルベースの分類 (FILE_ONLY)
        // メイン画像 (img_xxx.jpg) のみを基準にスキャン
        physicalFiles.filter { it.name.startsWith("img_") }.forEach { file ->
            if (file.name !in dbPhotoNames) {
                results.add(
                    OrphanedPhotoInfo(
                        type = OrphanedPhotoType.FILE_ONLY,
                        photoId = null,
                        personId = null,
                        photoFileName = file.name,
                        thumbnailFileName = "thumb_" + file.name.removePrefix("img_"),
                        capturedAt = Instant.ofEpochMilli(file.lastModified()),
                        description = "DB未登録の画像ファイル"
                    )
                )
            }
        }

        return results.sortedByDescending { it.capturedAt }
    }
}
