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
    fun getPersonById(id: String): Flow<Person?>

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
    suspend fun logicalDelete(id: String, timestamp: Long)

    @Query("UPDATE person_db SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM person_db WHERE id = :id")
    suspend fun deletePersonPhysically(id: String)

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
    fun getByPersonId(personId: String): Flow<List<HeightAndWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HeightAndWeight): Long

    @Query("UPDATE height_and_weight_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE height_and_weight_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: HeightAndWeight)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM height_and_weight_db")
    suspend fun getAllRaw(): List<HeightAndWeight>

    @Query("DELETE FROM height_and_weight_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeightAndWeight>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT h.* FROM height_and_weight_db h
        LEFT JOIN person_db p ON h.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<HeightAndWeight>

    @Query("SELECT EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM height_and_weight_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): HeightAndWeight?
}

@Dao
interface BpAndPulseDao {
    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<BpAndPulse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BpAndPulse): Long

    @Query("UPDATE bp_and_pulse_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE bp_and_pulse_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: BpAndPulse)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM bp_and_pulse_db")
    suspend fun getAllRaw(): List<BpAndPulse>

    @Query("DELETE FROM bp_and_pulse_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BpAndPulse>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT b.* FROM bp_and_pulse_db b
        LEFT JOIN person_db p ON b.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<BpAndPulse>

    @Query("SELECT EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM bp_and_pulse_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): BpAndPulse?
}

@Dao
interface GlucoseAndHbA1cDao {
    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<GlucoseAndHbA1c>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GlucoseAndHbA1c): Long

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: GlucoseAndHbA1c)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM glucose_and_hba1c_db")
    suspend fun getAllRaw(): List<GlucoseAndHbA1c>

    @Query("DELETE FROM glucose_and_hba1c_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GlucoseAndHbA1c>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT g.* FROM glucose_and_hba1c_db g
        LEFT JOIN person_db p ON g.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<GlucoseAndHbA1c>

    @Query("SELECT EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM glucose_and_hba1c_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): GlucoseAndHbA1c?
}

@Dao
interface ConditionAtVisitDao {
    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<ConditionAtVisit>>

    @Upsert
    suspend fun insert(item: ConditionAtVisit): Long

    @Query("UPDATE condition_at_visit_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE condition_at_visit_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

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
    fun getPersonIdsByConditionKeyword(query: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM condition_at_visit_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): ConditionAtVisit?

    @Query("SELECT id FROM condition_at_visit_db")
    suspend fun getAllIds(): List<String>
}

@Dao
interface ConditionPhotoDao {
    @Query("SELECT * FROM condition_photo_db WHERE condition_id = :conditionId AND deleted_at IS NULL ORDER BY captured_at ASC")
    fun getByConditionId(conditionId: String): Flow<List<ConditionPhoto>>

    @Upsert
    suspend fun insert(item: ConditionPhoto): Long

    @Query("UPDATE condition_photo_db SET condition_id = :newConditionId WHERE condition_id = '' AND person_id = :personId")
    suspend fun linkTemporaryPhotosToRecord(personId: String, newConditionId: String)

    @Query("DELETE FROM condition_photo_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId AND deleted_at IS NULL")
    fun getAllByPersonIdFlow(personId: String): Flow<List<ConditionPhoto>>

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId")
    suspend fun getAllByPersonId(personId: String): List<ConditionPhoto>

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_photo_db")
    suspend fun getAllRaw(): List<ConditionPhoto>

    @Query("DELETE FROM condition_photo_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<ConditionPhoto>)

    @Query("UPDATE condition_photo_db SET condition_id = :conditionId WHERE id = :photoId")
    suspend fun updateConditionId(photoId: String, conditionId: String)

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
    fun getByPersonId(personId: String): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date LIKE :month || '%' AND deleted_at IS NULL")
    fun getByMonth(personId: String, month: String): Flow<List<MedicationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MedicationRecord): Long

    @Query("UPDATE medication_record_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE medication_record_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

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
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM medication_record_db WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLog)

    @Query("SELECT * FROM audit_log_db ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Query("DELETE FROM audit_log_db WHERE timestamp < :threshold")
    suspend fun deleteOldLogs(threshold: java.time.Instant)

    @Query("DELETE FROM audit_log_db")
    suspend fun deleteAll()
}
