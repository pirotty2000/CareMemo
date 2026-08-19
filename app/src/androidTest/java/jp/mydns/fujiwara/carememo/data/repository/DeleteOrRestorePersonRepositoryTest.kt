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
import java.io.File
import java.time.Instant
import jp.mydns.fujiwara.carememo.utils.ImageUtils

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
            context,
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
        
        val deletedHwList = hwDao.getAllRaw().filter { it.personId == "u1" }
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
    fun PHY_01_permanentlyDeletePerson_removesRecordsAndFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val personId = "u1"
        val photoId = "photo1"
        val photoName = "test_photo.jpg"
        val thumbName = "test_thumb.jpg"
        
        // 1. Setup DB
        personDao.insert(Person(id = personId, lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()))
        db.conditionAtVisitDao().insert(ConditionAtVisit(id = "c1", personId = personId, title = "T", condition = "C", author = "A", recordTime = Instant.now()))
        db.conditionPhotoDao().insert(ConditionPhoto(id = photoId, conditionId = "c1", personId = personId, photoFileName = photoName, thumbnailFileName = thumbName, capturedAt = Instant.now()))

        // 2. Setup Physical Files
        val photoDir = ImageUtils.getPhotosDirPublic(context)
        val photoFile = File(photoDir, photoName).apply { createNewFile(); writeText("dummy") }
        val thumbFile = File(photoDir, thumbName).apply { createNewFile(); writeText("dummy") }
        assertTrue(photoFile.exists())
        assertTrue(thumbFile.exists())

        // 3. Execute
        repository.permanentlyDeletePerson(personId)

        // 4. Verify DB
        assertNull(personDao.getPersonById(personId).first())
        
        // 5. Verify Files
        assertFalse("Original photo should be deleted", photoFile.exists())
        assertFalse("Thumbnail photo should be deleted", thumbFile.exists())
    }

    @Test
    fun PHY_02_deleteAllEndedPersons_removesRecordsAndFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val p1 = "u1"
        val p2 = "u2"
        
        // 1. Setup DB (both archived)
        personDao.insert(Person(id = p1, lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now(), deletedAt = 1000L))
        personDao.insert(Person(id = p2, lastName = "C", firstName = "D", lastNameFurigana = "C", firstNameFurigana = "D", birthday = Instant.now(), deletedAt = 2000L))
        
        db.conditionAtVisitDao().insert(ConditionAtVisit(id = "c1", personId = p1, title = "T", condition = "C", author = "A", recordTime = Instant.now()))
        db.conditionPhotoDao().insert(ConditionPhoto(id = "ph1", conditionId = "c1", personId = p1, photoFileName = "p1.jpg", thumbnailFileName = "t1.jpg", capturedAt = Instant.now()))

        // 2. Setup Files
        val photoDir = ImageUtils.getPhotosDirPublic(context)
        val f1 = File(photoDir, "p1.jpg").apply { createNewFile() }
        val t1 = File(photoDir, "t1.jpg").apply { createNewFile() }

        // 3. Execute
        repository.deleteAllEndedPersons()

        // 4. Verify
        assertTrue(personDao.getDeletedPersonsRaw().isEmpty())
        assertFalse(f1.exists())
        assertFalse(t1.exists())
    }

    @Test
    fun PHY_02_deleteAllEndedPersons_removesArchivedOnly() = runBlocking {
        personDao.insert(Person(id = "active", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()))
        personDao.insert(Person(id = "archived", lastName = "X", firstName = "Y", lastNameFurigana = "X", firstNameFurigana = "Y", birthday = Instant.now(), deletedAt = 1000L))

        val archived = repository.getArchivedPersons().first()
        assertEquals(1, archived.size)
        assertEquals("archived", archived[0].id)
    }

    // endregion
}
