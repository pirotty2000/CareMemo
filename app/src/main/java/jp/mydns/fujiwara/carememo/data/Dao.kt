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

    @Insert
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE person_db SET deleted_at = :timestamp WHERE id = :id")
    suspend fun logicalDelete(id: Int, timestamp: Long)

    @Query("UPDATE person_db SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM person_db WHERE deleted_at IS NOT NULL")
    suspend fun deleteEndedPersons()

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM person_db")
    suspend fun getAllRaw(): List<Person>

    @Query("DELETE FROM person_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Person>)
}

@Dao
interface HeightAndWeightDao {
    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<HeightAndWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HeightAndWeight)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeightAndWeight>)

    @Query("SELECT DISTINCT person_id FROM height_and_weight_db WHERE (height IS NOT NULL OR weight IS NOT NULL) AND deleted_at IS NULL")
    fun getPersonIdsWithHeightWeight(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>
}

@Dao
interface BpAndPulseDao {
    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<BpAndPulse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BpAndPulse)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BpAndPulse>)

    @Query("SELECT DISTINCT person_id FROM bp_and_pulse_db WHERE (bp_systolic IS NOT NULL OR bp_diastolic IS NOT NULL OR pulse IS NOT NULL OR body_temperature IS NOT NULL) AND deleted_at IS NULL")
    fun getPersonIdsWithVital(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>
}

@Dao
interface GlucoseAndHbA1cDao {
    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GlucoseAndHbA1c)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GlucoseAndHbA1c>)

    @Query("SELECT DISTINCT person_id FROM glucose_and_hba1c_db WHERE (glucose IS NOT NULL OR hba1c IS NOT NULL) AND deleted_at IS NULL")
    fun getPersonIdsWithGlucose(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>
}

@Dao
interface ConditionAtVisitDao {
    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<ConditionAtVisit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ConditionAtVisit)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ConditionAtVisit>)

    @Query("SELECT DISTINCT person_id FROM condition_at_visit_db WHERE deleted_at IS NULL")
    fun getPersonIdsWithCondition(): Flow<List<Int>>

    @Query("""
        SELECT DISTINCT person_id FROM condition_at_visit_db 
        WHERE deleted_at IS NULL 
        AND (title LIKE '%' || :query || '%' OR condition LIKE '%' || :query || '%')
    """)
    fun getPersonIdsByConditionKeyword(query: String): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>
}

@Dao
interface ConditionPhotoDao {
    @Query("SELECT * FROM condition_photo_db WHERE condition_id = :conditionId AND deleted_at IS NULL ORDER BY captured_at ASC")
    fun getByConditionId(conditionId: Int): Flow<List<ConditionPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ConditionPhoto)

    @Update
    suspend fun update(item: ConditionPhoto)

    @Delete
    suspend fun delete(item: ConditionPhoto)

    @Query("DELETE FROM condition_photo_db WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE id = :id")
    suspend fun logicalDelete(id: Int, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE condition_id = :conditionId")
    suspend fun logicalDeleteByConditionId(conditionId: Int, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: Int, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: Int)

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId")
    suspend fun getAllByPersonId(personId: Int): List<ConditionPhoto>

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_photo_db")
    suspend fun getAllRaw(): List<ConditionPhoto>

    @Query("DELETE FROM condition_photo_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ConditionPhoto>)
}

@Dao
interface MedicationRecordDao {
    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date = :dosageDate AND deleted_at IS NULL")
    fun getByDate(personId: Int, dosageDate: String): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date LIKE :month || '%' AND deleted_at IS NULL")
    fun getByMonth(personId: Int, month: String): Flow<List<MedicationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MedicationRecord)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MedicationRecord>)

    @Query("SELECT EXISTS(SELECT 1 FROM medication_record_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: Int): Flow<Boolean>

    @Query("SELECT DISTINCT person_id FROM medication_record_db WHERE deleted_at IS NULL")
    fun getPersonIdsWithMedication(): Flow<List<Int>>
}
