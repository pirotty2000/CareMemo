package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CareMemoDaoTest {
    private lateinit var personDao: PersonDao
    private lateinit var db: AppDatabase

    private val testPerson = Person(
        lastName = "テスト",
        firstName = "太郎",
        lastNameFurigana = "てすと",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        personDao = db.personDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetPerson() = runBlocking {
        personDao.insert(testPerson)
        val allPersons = personDao.getAllPersons().first()
        assertEquals(allPersons[0].lastName, testPerson.lastName)
    }

    @Test
    fun logicalDeleteAndGet() = runBlocking {
        personDao.insert(testPerson)
        val personsBefore = personDao.getAllPersons().first()
        val personId = personsBefore[0].id
        
        personDao.logicalDelete(personId, System.currentTimeMillis())
        
        val activePersons = personDao.getAllPersons().first()
        assertEquals(0, activePersons.size)
        
        val deletedPersons = personDao.getDeletedPersons().first()
        assertEquals(1, deletedPersons.size)
        assertEquals(personId, deletedPersons[0].id)
    }
}
