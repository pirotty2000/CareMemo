package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.InconsistencyType

/**
 * Component：MaintenanceDisplayMapper
 *
 * 【役割】
 * データベース不整合の種類（InconsistencyType）を表示用のリソース ID にマッピングします。
 */
object MaintenanceDisplayMapper {
    /**
     * 不整合の種類に対応する説明文のリソース ID を取得します。
     */
    fun getDescriptionResId(type: InconsistencyType): Int {
        return when (type) {
            InconsistencyType.UNASSIGNED_HEIGHT_WEIGHT -> R.string.maintenance_err_unassigned_height_weight
            InconsistencyType.UNASSIGNED_VITAL -> R.string.maintenance_err_unassigned_vital
            InconsistencyType.UNASSIGNED_GLUCOSE -> R.string.maintenance_err_unassigned_glucose
            InconsistencyType.UNASSIGNED_CONDITION -> R.string.maintenance_err_unassigned_condition
            InconsistencyType.UNASSIGNED_MEDICATION -> R.string.maintenance_err_unassigned_medication
            InconsistencyType.UNASSIGNED_CONTACT -> R.string.maintenance_err_unassigned_contact
            InconsistencyType.UNASSIGNED_PHOTO -> R.string.maintenance_err_unassigned_photo
        }
    }
}
