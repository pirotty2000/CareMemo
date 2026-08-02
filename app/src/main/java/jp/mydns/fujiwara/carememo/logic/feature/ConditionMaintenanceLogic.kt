package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import java.io.File
import java.time.Instant

/**
 * Logic：ConditionMaintenanceLogic
 *
 * 【役割】
 * 所見メモに関連するデータの整合性維持（メンテナンス）に関するドメインロジックを提供します。
 * 主に、DBレコードと物理ストレージ上のファイルの不整合（迷子写真）を検出し、分類する役割を担います。
 *
 * 【主な機能】
 * ・DBレコードと物理ファイルの突き合わせによる「迷子写真」の特定。
 * ・不整合の原因に応じた分類（一時保存の放置、親記録の消失、未登録ファイル）。
 * ・UI表示用のメタデータ（説明文、撮影日時）の構築。
 *
 * 【設計指針】
 * 1. 本クラスは純粋なロジックのみを扱い、実際のファイル入出力やDBアクセスは行わない（引数として結果を受け取る）。
 * 2. 物理ファイルのスキャンは、命名規則（img_ で始まる等）に基づき、メイン画像とサムネイルのペアを考慮して行う。
 */
object ConditionMaintenanceLogic {

    /**
     * DBレコードと物理ファイルを突き合わせ、迷子写真を特定・分類します。
     *
     * 分類ルール：
     * 1. [OrphanedPhotoType.TEMPORARY]: DBにあるが、親の所見記録IDが空。
     * 2. [OrphanedPhotoType.ORPHANED_RECORD]: DBにあるが、紐付け先の所見記録が既に削除されている。
     * 3. [OrphanedPhotoType.FILE_ONLY]: ストレージにファイルはあるが、DBにレコードが存在しない。
     *
     * @param dbPhotos DBから取得された全写真レコードのリスト
     * @param existingConditionIds 現在DBに存在する全所見記録のIDセット
     * @param physicalFiles アプリの内部ストレージ（写真ディレクトリ）内の全ファイルリスト
     * @return 検出された迷子写真情報のリスト（撮影日時の降順）
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
                else -> null // 正常な紐付け
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
        // メイン画像 (img_xxx.jpg) のみを基準にスキャンし、DBに名前がないものを抽出
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

        // 最新のものが上に来るようにソート
        return results.sortedByDescending { it.capturedAt }
    }
}
