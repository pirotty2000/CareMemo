package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 各カテゴリの記録有無サマリーの集計を担当するリポジトリ
 */
class PersonSummaryRepository(
    private val personDao: PersonDao,
    private val heightAndWeightDao: HeightAndWeightDao,
    private val bpAndPulseDao: BpAndPulseDao,
    private val glucoseAndHbA1cDao: GlucoseAndHbA1cDao,
    private val conditionAtVisitDao: ConditionAtVisitDao,
    private val medicationRecordDao: MedicationRecordDao
) {
    /**
     * 特定の利用者の各カテゴリー記録の有無サマリーを返します。
     */
    fun getPersonCategorySummaryById(personId: Int): Flow<PersonCategorySummary> {
        return combine(
            heightAndWeightDao.hasDataForPerson(personId),
            bpAndPulseDao.hasDataForPerson(personId),
            glucoseAndHbA1cDao.hasDataForPerson(personId),
            conditionAtVisitDao.hasDataForPerson(personId),
            medicationRecordDao.hasDataForPerson(personId)
        ) { hw, bp, glucose, condition, medication ->
            PersonCategorySummary(
                hasHeightWeight = hw,
                hasBpAndPulse = bp,
                hasGlucoseAndHbA1c = glucose,
                hasCondition = condition,
                hasMedication = medication
            )
        }
    }

    /**
     * 全利用者のサマリー情報を取得します。
     */
    fun getPersonCategorySummaries(): Flow<Map<Int, PersonCategorySummary>> {
        return personDao.getPersonCategorySummaries().map { list ->
            list.associate { result ->
                result.id to PersonCategorySummary(
                    hasHeightWeight = result.hasHeightWeight,
                    hasBpAndPulse = result.hasBpAndPulse,
                    hasGlucoseAndHbA1c = result.hasGlucoseAndHbA1c,
                    hasCondition = result.hasCondition,
                    hasMedication = result.hasMedication
                )
            }
        }
    }
}
