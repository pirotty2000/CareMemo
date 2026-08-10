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
 * Instrumented Test: DeleteOrRestorePersonRepository
 */
@RunWith(AndroidJUnit4::class)
class DeleteOrRestorePersonRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DeleteOrRestorePersonRepository
    private lateinit var personDao: PersonDao
    private lateinit var hwDao: HeightAndWeightDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        personDao = db.personDao()
        hwDao = db.heightAndWeightDao()

        repository = DeleteOrRestorePersonRepository(
            db,
            personDao,
            hwDao,
            db.bpAndPulseDao(),
            db.glucoseAndHbA1cDao(),
            db.conditionAtVisitDao(),
            db.conditionPhotoDao(),
            db.medicationRecordDao(),
            db.emergencyContactDao(),
            null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region 2. カスケード論理削除・復帰テスト

    @Test
    fun CAS_01_logicalDeletePerson_updatesAllRelatedTables() = runBlocking {
        // 1. Setup
        val person = Person(id = "u1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now())
        personDao.insert(person)
        hwDao.insert(HeightAndWeight(personId = "u1", height = 170.0, recordTime = Instant.now()))

        // 2. Execute
        repository.logicalDeletePerson("u1")

        // 3. Verify
        val deletedPerson = personDao.getPersonById("u1").first()
        assertNotNull(deletedPerson?.deletedAt)
        
        val deletedHwList = hwDao.getByPersonId("u1").first()
        assertEquals(1, deletedHwList.size)
        assertNotNull(deletedHwList[0].deletedAt)
        assertEquals(deletedPerson?.deletedAt, deletedHwList[0].deletedAt)
    }

    @Test
    fun CAS_02_restorePerson_clearsDeletedAt() = runBlocking {
        // 1. Setup with deleted state
        val ts = System.currentTimeMillis()
        val person = Person(id = "u1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now(), deletedAt = ts)
        personDao.insert(person)
        hwDao.insert(HeightAndWeight(personId = "u1", height = 170.0, recordTime = Instant.now(), deletedAt = ts))

        // 2. Execute
        repository.restorePerson("u1")

        // 3. Verify
        val restoredPerson = personDao.getPersonById("u1").first()
        assertNull(restoredPerson?.deletedAt)
        
        val restoredHwList = hwDao.getByPersonId("u1").first()
        assertNull(restoredHwList[0].deletedAt)
    }

    // endregion

    // region 3. 物理削除（完全抹消）テスト

    @Test
    fun PHY_01_permanentlyDeletePerson_removesRecordsFromDb() = runBlocking {
        // 1. Setup
        personDao.insert(Person(id = "u1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()))
        hwDao.insert(HeightAndWeight(personId = "u1", height = 170.0, recordTime = Instant.now()))

        // 2. Execute
        repository.permanentlyDeletePerson("u1")

        // 3. Verify
        assertNull(personDao.getPersonById("u1").first())
        assertTrue(hwDao.getByPersonId("u1").first().isEmpty())
    }

    // endregion

    // region 4. データ取得テスト

    @Test
    fun GET_01_getArchivedPersons_returnsOnlyDeleted() = runBlocking {
        personDao.insert(Person(id = "active", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()))
        personDao.insert(Person(id = "archived", lastName = "X", firstName = "Y", lastNameFurigana = "X", firstNameFurigana = "Y", birthday = Instant.now(), deletedAt = 1000L))

        val archived = repository.getArchivedPersons().first()
        assertEquals(1, archived.size)
        assertEquals("archived", archived[0].id)
    }

    // endregion
}
