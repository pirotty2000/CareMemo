package jp.mydns.fujiwara.carememo.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic
import jp.mydns.fujiwara.carememo.logic.feature.OrphanedPhotoInfo
import jp.mydns.fujiwara.carememo.utils.ImageUtils

/**
 * UI State：OrphanedPhotoUiState
 *
 * 【役割】
 * 迷子写真（データベースの整合性が取れていない写真ファイル）管理画面の表示状態を保持します。
 *
 * @param orphanedPhotos 特定された迷子写真情報のリスト
 * @param isLoading 全体の読み込み中フラグ
 */
data class OrphanedPhotoUiState(
    val orphanedPhotos: List<OrphanedPhotoInfo> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * ViewModel：OrphanedPhotoViewModel
 *
 * 【役割】
 * 迷子写真管理画面（SCR-S-004）における状態管理と実行制御を担当します。
 * データベースのレコードと物理ファイルを照合し、整合性が取れていない不要な写真ファイルを特定・削除します。
 *
 * 【主要な機能】
 * ・整合性チェックによる迷子写真（DBレコードのみ存在、または物理ファイルのみ存在）の特定。
 * ・特定された迷子写真情報の一覧表示。
 * ・対象の迷子写真に関連する物理ファイルおよび DB レコードの削除。
 *
 * 【依存している Repository】
 * ・ConditionRepository: 経過記録および写真データの取得・削除。
 * ・UserSettingsRepository: 共通設定の参照（BaseUiStateViewModel 経由）。
 *
 * 【依存している Logic】
 * ・ConditionMaintenanceLogic: DBとファイルシステムの突合および迷子写真特定の判定ロジック。
 *
 * 【設計指針】
 * 1. 整合性の維持：物理ファイルの削除と DB レコードの削除を連動させ、浮いたデータを残さない。
 * 2. 実行の安全性：削除や読み込み処理は `safeLaunch` を使用し、非同期実行と例外時の UI 通知を行う。
 * 3. パフォーマンス：ファイルシステムのスキャンやリストの照合をワーカースレッドで行い、UI スレッドをブロックしない。
 */
class OrphanedPhotoViewModel(
    userSettingsRepository: UserSettingsRepository,
    private val conditionRepository: ConditionRepository,
    @param:SuppressLint("StaticFieldLeak")
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context, // アプリケーションコンテキストを想定
) : BaseUiStateViewModel<OrphanedPhotoUiState, Unit>(userSettingsRepository, OrphanedPhotoUiState()) {

    override val featureName: String = "OrphanedPhotoManagement"

    override fun copyWithLoadingState(state: OrphanedPhotoUiState, isLoading: Boolean): OrphanedPhotoUiState {
        return state.copy(isLoading = isLoading)
    }

    init {
        // 初期化時に迷子写真の特定処理を実行
        loadOrphanedPhotos()
    }

    /**
     * 迷子写真をロードして特定します。
     *
     * DB内の写真情報と、ストレージ上の物理ファイルを比較し、
     * どちらか一方しか存在しない「迷子状態」の写真をリストアップします。
     */
    fun loadOrphanedPhotos() = safeLaunch(operation = "loadOrphanedPhotos", loadingState = loadingStateProxy) {
        // 1. DBから全ての写真レコードを取得
        val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
        // 2. DBから全ての経過記録IDを取得（紐付け確認用）
        val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
        // 3. ストレージ上の公開写真ディレクトリを取得
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val physicalFiles = photosDir.listFiles()?.toList() ?: emptyList()

        // 4. ロジック層で照合を実行
        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(
            dbPhotos = dbPhotos,
            existingConditionIds = existingConditionIds,
            physicalFiles = physicalFiles
        )

        // 5. UI状態を更新
        updateUiState { it.copy(orphanedPhotos = orphaned) }
    }

    /**
     * 指定された迷子写真を物理的および論理的に削除します。
     *
     * @param info 削除対象の迷子写真情報
     */
    fun deletePhoto(info: OrphanedPhotoInfo) = safeLaunch(operation = "deletePhoto", loadingState = loadingStateProxy) {
        // 1. DBから削除 (レコードが存在する場合のみ)
        info.photoId?.let {
            conditionRepository.deleteConditionPhotoById(it, info.personId ?: "", featureName, "deletePhoto")
        }

        // 2. 物理ファイルを削除（写真本体およびサムネイル）
        ImageUtils.deleteImageFiles(context, info.photoFileName, info.thumbnailFileName)

        // 3. 最新の状態を再読み込み
        loadOrphanedPhotos()
    }
}
