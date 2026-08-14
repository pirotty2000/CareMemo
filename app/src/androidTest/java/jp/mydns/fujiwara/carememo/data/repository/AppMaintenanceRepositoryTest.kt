package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented Test: AppMaintenanceRepository
 */
@RunWith(AndroidJUnit4::class)
class AppMaintenanceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var maintenanceRepository: AppMaintenanceRepository
    private lateinit var personDao: PersonDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        personDao = db.personDao()
        
        maintenanceRepository = AppMaintenanceRepository(
            context,
            db,
            personDao,
            db.heightAndWeightDao(),
            db.bpAndPulseDao(),
            db.glucoseAndHbA1cDao(),
            db.conditionAtVisitDao(),
            db.conditionPhotoDao(),
            db.medicationRecordDao(),
            db.emergencyContactDao(),
            db.auditLogDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region 2. バックアップ・復元テスト (Backup & Restore)

    @Test
    fun BKR_01_replaceAllData_maintainsClinicalRelations() = runBlocking {
        // 1. Setup test data
        val personId = "u1"
        val person = Person(id = personId, lastName = "山田", firstName = "太郎", lastNameFurigana = "やまだ", firstNameFurigana = "たろう", birthday = Instant.now())
        personDao.insert(person)
        
        val hw = HeightAndWeight(personId = personId, height = 170.0, recordTime = Instant.now())
        db.heightAndWeightDao().insert(hw)

        // 2. Capture backup
        val backup = maintenanceRepository.getBackupData()
        assertEquals(1, backup.persons.size)
        assertEquals(1, backup.heightAndWeights.size)

        // 3. Clear data
        maintenanceRepository.clearAllData()
        assertTrue(personDao.getAllPersons().first().isEmpty())

        // 4. Restore
        maintenanceRepository.replaceAllData(backup)

        // 5. Verify
        val restoredPersons = personDao.getAllPersons().first()
        assertEquals(1, restoredPersons.size)
        assertEquals("山田", restoredPersons[0].lastName)
        
        // Relationship check (personId might be same in in-memory test, but we use the new ID anyway)
        val restoredPersonId = restoredPersons[0].id
        val restoredHw = db.heightAndWeightDao().getByPersonId(restoredPersonId).first()
        assertEquals(1, restoredHw.size)
        assertEquals(170.0, restoredHw[0].height!!, 0.0)
    }

    // endregion

    // region 3. データ消去テスト (Clear Data)

    @Test
    fun CLR_02_clearAllData_emptiesAllTables() = runBlocking {
        // 1. Setup data in multiple tables
        val person = Person(id = "p1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now())
        personDao.insert(person)
        
        val auditLog = AuditLog(timestamp = Instant.now(), featureName = "Test", operation = "op", tableName = "none", actionType = "INSERT", affectedId = "p1", resultType = "SUCCESS")
        db.auditLogDao().insert(auditLog)

        // 2. Clear all
        maintenanceRepository.clearAllData()

        // 3. Verify empty
        assertTrue(personDao.getAllPersons().first().isEmpty())
        assertTrue(db.auditLogDao().getAllLogs().first().isEmpty())
    }

    // endregion

    // region 4. 整合性修復テスト (Inconsistency)

    @Test
    fun INC_01_02_scanAndCleanInconsistencies() = runBlocking {
        // 1. Insert test inconsistency (unassigned record)
        maintenanceRepository.insertTestInconsistency()
        
        // 2. Scan
        var inconsistencies = maintenanceRepository.scanInconsistencies()
        assertEquals(1, inconsistencies.size)
        assertEquals("bp_and_pulse_db", inconsistencies[0].tableName)

        // 3. Clean
        maintenanceRepository.cleanInconsistencies(inconsistencies)
        
        // 4. Verify re-scan is empty
        inconsistencies = maintenanceRepository.scanInconsistencies()
        assertTrue(inconsistencies.isEmpty())
    }

    // endregion
}
