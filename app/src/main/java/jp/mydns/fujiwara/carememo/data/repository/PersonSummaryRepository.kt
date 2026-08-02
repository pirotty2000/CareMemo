package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Repository：PersonSummaryRepository
 *
 * 【役割】
 * 利用者の各記録カテゴリ（健康記録、所見メモ、服薬管理）にデータが存在するかどうかの「集計情報（サマリー）」を管理します。
 * 複数の DAO を横断して、特定の利用者または全利用者の記録有無をリアルタイムで把握するためのインターフェースを提供します。
 *
 * 【主な機能】
 * ・特定利用者の最新サマリー取得（複数テーブルの combine 監視）。
 * ・全利用者のサマリー一覧取得（利用者一覧画面のバッジ表示用）。
 *
 * 【設計指針】
 * 1. リアルタイム性：各カテゴリの DAO が提供する `hasDataForPerson` (Flow) を統合することで、
 *    いずれかのカテゴリに追記・削除が行われた際、UI側のサマリー表示も即座に自動更新されることを保証する。
 * 2. 効率性：全利用者の一覧集計では、個別の Flow 監視ではなく、専用の集計クエリ（PersonDao 由来）を活用してパフォーマンスを最適化する。
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
     * 特定の利用者の各カテゴリー記録の有無サマリーを Flow で取得します。
     * 内部で 5 つのデータ系統を監視し、いずれかに変更があれば新しいサマリーを発行します。
     *
     * @param personId 対象の利用者ID
     * @return カテゴリごとの有無を保持する PersonCategorySummary を通知する Flow
     */
    fun getPersonCategorySummaryById(personId: String): Flow<PersonCategorySummary> {
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
     * 有効な全利用者のサマリー情報をマップ形式で取得します。
     * 利用者一覧画面において、各リストアイテムのバッジ状態を一括で決定するために使用します。
     *
     * @return 利用者IDをキー、サマリーを値とするマップを通知する Flow
     */
    fun getPersonCategorySummaries(): Flow<Map<String, PersonCategorySummary>> {
        return personDao.getPersonCategorySummaries().map { list ->
            // クエリ結果（PersonSummaryQueryResult）をドメインモデル（PersonCategorySummary）のマップに変換
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
