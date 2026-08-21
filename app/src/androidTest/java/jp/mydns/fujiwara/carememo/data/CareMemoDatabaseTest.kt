package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository

/**
 * Instrumented Test: Database Integrity (DT-01)
 * 
 * Verifies Room schema definitions (foreign keys, unique constraints, indexes) 
 * working as designed on a real device database.
 */
@RunWith(AndroidJUnit4::class)
class CareMemoDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var hwDao: HeightAndWeightDao
    private lateinit var conditionDao: ConditionAtVisitDao
    private lateinit var medicationDao: MedicationRecordDao

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        hwDao = db.heightAndWeightDao()
        conditionDao = db.conditionAtVisitDao()
        medicationDao = db.medicationRecordDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private fun createTestPerson() = Person(
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "やまだ",
        firstNameFurigana = "たろう",
        birthday = Instant.now().truncatedTo(ChronoUnit.DAYS)
    )

    @Test
    fun DT_01_01_foreignKey_deletePerson_cascades() = runBlocking {
        val person = createTestPerson()
        personDao.insert(person)
        val personId = person.id

        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        conditionDao.insert(ConditionAtVisit(personId = personId, title = "T", condition = "C", author = "A", recordTime = now))

        // Physical delete person
        personDao.deletePersonPhysically(personId)

        // Verify records are gone
        assertTrue(hwDao.getByPersonId(personId).first().isEmpty())
        assertTrue(conditionDao.getByPersonId(personId).first().isEmpty())
    }

    @Test
    fun DT_01_02_uniqueConstraint_upsert_logic() = runBlocking {
        val person = createTestPerson()
        personDao.insert(person)
        val personId = person.id
        val sameTime = Instant.now().truncatedTo(ChronoUnit.MINUTES)

        hwDao.insert(HeightAndWeight(personId = personId, height = 160.0, recordTime = sameTime))
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = sameTime))

        val records = hwDao.getByPersonId(personId).first()
        assertEquals(1, records.size)
        assertEquals(170.0, records[0].height!!, 0.0)
    }

    @Test
    fun DT_01_03_medication_uniqueConstraint_upsert() = runBlocking {
        val person = createTestPerson()
        personDao.insert(person)
        val personId = person.id
        val date = "2023-10-27"
        val slot = 0 

        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = date, timeSlot = slot, status = 0, recordTime = Instant.now()))
        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = date, timeSlot = slot, status = 2, recordTime = Instant.now()))

        val records = medicationDao.getByPersonId(personId).first()
        assertEquals(1, records.size)
        assertEquals(2, records[0].status)
    }

    @Test
    fun DT_01_04_cascadeLogicalDelete() = runBlocking {
        val person = createTestPerson()
        personDao.insert(person)
        val personId = person.id
        
        val now = Instant.now()
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = "2023-10-27", timeSlot = 0, status = 2, recordTime = now))

        val repo = DeleteOrRestorePersonRepository(
            ApplicationProvider.getApplicationContext(),
            db, personDao, hwDao, db.bpAndPulseDao(), db.glucoseAndHbA1cDao(),
            db.conditionAtVisitDao(), db.conditionPhotoDao(), medicationDao,
            db.emergencyContactDao()
        )

        repo.logicalDeletePerson(personId, "Test", "Delete")

        val personResult = personDao.getDeletedPersons().first().find { it.id == personId }
        assertNotNull(personResult?.deletedAt)

        val hw = hwDao.getAllRaw().find { it.personId == personId }
        assertNotNull(hw?.deletedAt)

        val med = medicationDao.getAllRaw().find { it.personId == personId }
        assertNotNull(med?.deletedAt)
    }

    @Test
    fun DT_01_05_summary_excludesLogicalDeleted() = runBlocking {
        val person = createTestPerson()
        personDao.insert(person)
        val personId = person.id
        val now = Instant.now()

        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        
        var summary = personDao.getPersonCategorySummaries().first().find { it.id == personId }
        assertTrue(summary?.hasHeightWeight ?: false)

        hwDao.logicalDeleteByPersonId(personId, System.currentTimeMillis())

        summary = personDao.getPersonCategorySummaries().first().find { it.id == personId }
        assertFalse(summary?.hasHeightWeight ?: true)
    }

    @Test
    fun DT_01_06_auditLog_orderDescending() = runBlocking {
        val auditDao = db.auditLogDao()
        val now = Instant.now()
        
        auditDao.insert(AuditLog(featureName = "S1", operation = "O1", tableName = "T1", actionType = "INSERT", affectedId = "1", timestamp = now.minusSeconds(10)))
        auditDao.insert(AuditLog(featureName = "S2", operation = "O2", tableName = "T2", actionType = "UPDATE", affectedId = "2", timestamp = now))

        val logs = auditDao.getAllLogs().first()
        assertEquals(2, logs.size)
        assertEquals("S2", logs[0].featureName)
        assertEquals("S1", logs[1].featureName)
    }

    @Test
    fun DT_01_07_findPerson_ignoresTimeComponent() = runBlocking {
        val baseDate = Instant.parse("1950-01-01T00:00:00Z")
        val personWithTime = createTestPerson().copy(
            lastName = "山田",
            firstName = "太郎",
            birthday = baseDate.plus(12, ChronoUnit.HOURS)
        )
        personDao.insert(personWithTime)

        val endOfDay = baseDate.plus(1, ChronoUnit.DAYS)

        val found = personDao.findExistingPerson(
            lastName = "山田",
            firstName = "太郎",
            start = baseDate,
            end = endOfDay,
            note = ""
        )

        assertNotNull(found)
        assertEquals("山田", found?.lastName)
    }
}
