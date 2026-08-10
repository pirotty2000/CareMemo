package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mapping層テスト：FeatureNameMapper
 */
class FeatureNameMapperTest {

    @Test
    fun MAP_01_personList_mapping() {
        assertEquals(R.string.audit_feature_person_list, "PersonList".toFeatureLabelRes)
    }

    @Test
    fun MAP_02_personEdit_mapping() {
        assertEquals(R.string.audit_feature_person_edit, "PersonEdit".toFeatureLabelRes)
    }

    @Test
    fun MAP_03_deleteOrRestore_mapping() {
        assertEquals(R.string.audit_feature_person_archive, "DeleteOrRestorePerson".toFeatureLabelRes)
    }

    @Test
    fun MAP_04_personBase_mapping() {
        assertEquals(R.string.audit_feature_person_base, "PersonBase".toFeatureLabelRes)
    }

    @Test
    fun MAP_05_personHealth_mapping() {
        assertEquals(R.string.audit_feature_health, "PersonHealth".toFeatureLabelRes)
    }

    @Test
    fun MAP_06_batchInput_mapping() {
        assertEquals(R.string.audit_feature_batch_input, "BatchInput".toFeatureLabelRes)
    }

    @Test
    fun MAP_07_detailHeightWeight_mapping() {
        assertEquals(R.string.audit_feature_detail_height_weight, "PersonDetail/HEIGHT_AND_WEIGHT".toFeatureLabelRes)
    }

    @Test
    fun MAP_08_detailVital_mapping() {
        assertEquals(R.string.audit_feature_detail_vital, "PersonDetail/BP_AND_PULSE".toFeatureLabelRes)
    }

    @Test
    fun MAP_09_detailGlucose_mapping() {
        assertEquals(R.string.audit_feature_detail_glucose, "PersonDetail/GLUCOSE_AND_HBA1C".toFeatureLabelRes)
    }

    @Test
    fun MAP_10_personCondition_mapping() {
        assertEquals(R.string.audit_feature_condition, "PersonCondition".toFeatureLabelRes)
    }

    @Test
    fun MAP_11_detailCondition_mapping() {
        assertEquals(R.string.audit_feature_detail_condition, "PersonDetail/CONDITION".toFeatureLabelRes)
    }

    @Test
    fun MAP_12_personMedication_mapping() {
        assertEquals(R.string.audit_feature_medication, "PersonMedication".toFeatureLabelRes)
    }

    @Test
    fun MAP_13_detailMedication_mapping() {
        assertEquals(R.string.audit_feature_detail_medication, "PersonDetail/MEDICATION".toFeatureLabelRes)
    }

    @Test
    fun MAP_14_settings_mapping() {
        assertEquals(R.string.audit_feature_settings, "Settings".toFeatureLabelRes)
    }

    @Test
    fun MAP_15_detailBase_mapping() {
        assertEquals(R.string.audit_feature_detail_base, "PersonDetail/Base".toFeatureLabelRes)
    }

    @Test
    fun MAP_16_unknown_mapping() {
        assertEquals(0, "UNKNOWN".toFeatureLabelRes)
    }
}
