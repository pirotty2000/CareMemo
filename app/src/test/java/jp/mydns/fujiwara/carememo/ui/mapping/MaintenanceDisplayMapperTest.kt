package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.InconsistencyType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit Test: MaintenanceDisplayMapper
 */
class MaintenanceDisplayMapperTest {

    @Test
    fun getDescriptionResId_returnsCorrectResId() {
        assertEquals(R.string.maintenance_err_unassigned_height_weight, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_HEIGHT_WEIGHT))
        assertEquals(R.string.maintenance_err_unassigned_vital, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_VITAL))
        assertEquals(R.string.maintenance_err_unassigned_glucose, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_GLUCOSE))
        assertEquals(R.string.maintenance_err_unassigned_condition, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_CONDITION))
        assertEquals(R.string.maintenance_err_unassigned_medication, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_MEDICATION))
        assertEquals(R.string.maintenance_err_unassigned_contact, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_CONTACT))
        assertEquals(R.string.maintenance_err_unassigned_photo, MaintenanceDisplayMapper.getDescriptionResId(InconsistencyType.UNASSIGNED_PHOTO))
    }
}
