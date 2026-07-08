package jp.mydns.fujiwara.carememo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person_db WHERE deleted_at IS NULL ORDER BY last_name_furigana ASC, first_name_furigana ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM person_db WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getDeletedPersons(): Flow<List<Person>>

    @Query("SELECT * FROM person_db WHERE id = :id")
    fun getPersonById(id: Int): Flow<Person?>

    @Query("""
        SELECT * FROM person_db 
        WHERE last_name = :lastName 
        AND first_name = :firstName 
        AND birthday >= :start 
        AND birthday < :end
        AND COALESCE(note, '') = COALESCE(:note, '')
        LIMIT 1
    """)
    suspend fun findExistingPerson(
        lastName: String, 
        firstName: String, 
        start: java.time.Instant, 
        end: java.time.Instant, 
        note: String
    ): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE person_db SET deleted_at = :timestamp WHERE id = :id")
    suspend fun logicalDelete(id: Int, timestamp: Long)

    @Query("UPDATE person_db SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM person_db WHERE id = :id")
    suspend fun deletePersonPhysically(id: Int)

    @Query("DELETE FROM person_db WHERE deleted_at IS NOT NULL")
    suspend fun deleteEndedPersons()

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM person_db")
    suspend fun getAllRaw(): List<Person>

    @Query("DELETE FROM person_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<Person>)

    @Query("""
        SELECT 
            p.id,
            EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasHeightWeight,
            EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasBpAndPulse,
            EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasGlucoseAndHbA1c,
            EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasCondition,
            EXISTS(SELECT 1 FROM medication_record_db WHERE person_id = p.id AND deleted_at IS NULL AND status IN (1, 2)) AS hasMedication
        FROM person_db p
        WHERE p.deleted_at IS NULL
    """)
    fun getPersonCategorySummaries(): Flow<List<PersonSummaryQueryResult>>
}

@Dao
interface HeightAndWeightDao {
    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<HeightAndWeight>>

    @Upsert
    suspend fun insert(item: HeightAndWeight): Long

    @Query("UPDATE height_and_weight_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE height_and_weight_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Delete
    suspend fun delete(item: HeightAndWeight)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM height_and_weight_db")
    suspend fun getAllRaw(): List<HeightAndWeight>

    @Query("DELETE FROM height_and_weight_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<HeightAndWeight>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT h.* FROM height_and_weight_db h
        LEFT JOIN person_db p ON h.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<HeightAndWeight>

    @Query("SELECT EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("DELETE FROM height_and_weight_db WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface BpAndPulseDao {
    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<BpAndPulse>>

    @Upsert
    suspend fun insert(item: BpAndPulse): Long

    @Query("UPDATE bp_and_pulse_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE bp_and_pulse_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Delete
    suspend fun delete(item: BpAndPulse)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM bp_and_pulse_db")
    suspend fun getAllRaw(): List<BpAndPulse>

    @Query("DELETE FROM bp_and_pulse_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<BpAndPulse>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT b.* FROM bp_and_pulse_db b
        LEFT JOIN person_db p ON b.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<BpAndPulse>

    @Query("SELECT EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("DELETE FROM bp_and_pulse_db WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface GlucoseAndHbA1cDao {
    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>>

    @Upsert
    suspend fun insert(item: GlucoseAndHbA1c): Long

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Delete
    suspend fun delete(item: GlucoseAndHbA1c)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM glucose_and_hba1c_db")
    suspend fun getAllRaw(): List<GlucoseAndHbA1c>

    @Query("DELETE FROM glucose_and_hba1c_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<GlucoseAndHbA1c>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT g.* FROM glucose_and_hba1c_db g
        LEFT JOIN person_db p ON g.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<GlucoseAndHbA1c>

    @Query("SELECT EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("DELETE FROM glucose_and_hba1c_db WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface ConditionAtVisitDao {
    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<ConditionAtVisit>>

    @Upsert
    suspend fun insert(item: ConditionAtVisit): Long

    @Query("UPDATE condition_at_visit_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE condition_at_visit_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Delete
    suspend fun delete(item: ConditionAtVisit)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_at_visit_db")
    suspend fun getAllRaw(): List<ConditionAtVisit>

    @Query("DELETE FROM condition_at_visit_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<ConditionAtVisit>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT c.* FROM condition_at_visit_db c
        LEFT JOIN person_db p ON c.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<ConditionAtVisit>

    @Query("""
        SELECT DISTINCT person_id FROM condition_at_visit_db 
        WHERE deleted_at IS NULL 
        AND (title LIKE '%' || :query || '%' OR condition LIKE '%' || :query || '%')
    """)
    fun getPersonIdsByConditionKeyword(query: String): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("DELETE FROM condition_at_visit_db WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface ConditionPhotoDao {
    @Query("SELECT * FROM condition_photo_db WHERE condition_id = :conditionId AND deleted_at IS NULL ORDER BY captured_at ASC")
    fun getByConditionId(conditionId: Int): Flow<List<ConditionPhoto>>

    @Upsert
    suspend fun insert(item: ConditionPhoto): Long

    @Query("UPDATE condition_photo_db SET condition_id = :newConditionId WHERE condition_id = 0 AND person_id = :personId")
    suspend fun linkTemporaryPhotosToRecord(personId: Int, newConditionId: Int)

    @Query("DELETE FROM condition_photo_db WHERE id = :id")
    suspend fun deleteById(id: Int)

//    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE id = :id")
//    suspend fun logicalDelete(id: Int, timestamp: Long)

//    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE condition_id = :conditionId")
//    suspend fun logicalDeleteByConditionId(conditionId: Int, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId AND deleted_at IS NULL")
    fun getAllByPersonIdFlow(personId: Int): Flow<List<ConditionPhoto>>

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId")
    suspend fun getAllByPersonId(personId: Int): List<ConditionPhoto>

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_photo_db")
    suspend fun getAllRaw(): List<ConditionPhoto>

    @Query("DELETE FROM condition_photo_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<ConditionPhoto>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT cp.* FROM condition_photo_db cp
        LEFT JOIN condition_at_visit_db c ON cp.condition_id = c.id
        WHERE c.id IS NULL
    """)
    suspend fun getOrphanedPhotos(): List<ConditionPhoto>
}

@Dao
interface MedicationRecordDao {
    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND deleted_at IS NULL")
    fun getByPersonId(personId: Int): Flow<List<MedicationRecord>>

//    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date = :dosageDate AND deleted_at IS NULL")
//    fun getByDate(personId: Int, dosageDate: String): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date LIKE :month || '%' AND deleted_at IS NULL")
    fun getByMonth(personId: Int, month: String): Flow<List<MedicationRecord>>

    @Upsert
    suspend fun insert(item: MedicationRecord): Long

    @Query("UPDATE medication_record_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE medication_record_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Delete
    suspend fun delete(item: MedicationRecord)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM medication_record_db")
    suspend fun getAllRaw(): List<MedicationRecord>

    @Query("DELETE FROM medication_record_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<MedicationRecord>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT m.* FROM medication_record_db m
        LEFT JOIN person_db p ON m.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<MedicationRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM medication_record_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("DELETE FROM medication_record_db WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLog)

    @Query("SELECT * FROM audit_log_db ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    /**
     * 指定された日時より古いログを削除する（ローテーション用）
     * @param threshold 削除のしきい値となる Instant
     */
    @Query("DELETE FROM audit_log_db WHERE timestamp < :threshold")
    suspend fun deleteOldLogs(threshold: java.time.Instant)

    /**
     * ログの総件数を取得（管理画面での統計用）
     */
    @Query("SELECT COUNT(*) FROM audit_log_db")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM audit_log_db")
    suspend fun deleteAll()
}
