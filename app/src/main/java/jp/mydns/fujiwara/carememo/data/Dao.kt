package jp.mydns.fujiwara.carememo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person_db ORDER BY name ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM person_db WHERE id = :id")
    fun getPersonById(id: Int): Flow<Person?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)
}

@Dao
interface HeightAndWeightDao {
    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<HeightAndWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HeightAndWeight)

    @Update
    suspend fun update(item: HeightAndWeight)

    @Delete
    suspend fun delete(item: HeightAndWeight)
}

@Dao
interface BpAndPulseDao {
    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<BpAndPulse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BpAndPulse)

    @Update
    suspend fun update(item: BpAndPulse)

    @Delete
    suspend fun delete(item: BpAndPulse)
}

@Dao
interface GlucoseAndHbA1cDao {
    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<GlucoseAndHbA1c>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GlucoseAndHbA1c)

    @Update
    suspend fun update(item: GlucoseAndHbA1c)

    @Delete
    suspend fun delete(item: GlucoseAndHbA1c)
}

@Dao
interface ConditionAtVisitDao {
    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId ORDER BY record_time DESC")
    fun getByPersonId(personId: Int): Flow<List<ConditionAtVisit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ConditionAtVisit)

    @Update
    suspend fun update(item: ConditionAtVisit)

    @Delete
    suspend fun delete(item: ConditionAtVisit)
}
