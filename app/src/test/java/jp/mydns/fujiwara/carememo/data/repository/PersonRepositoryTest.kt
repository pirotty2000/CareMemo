package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit Test: PersonRepository
 */
class PersonRepositoryTest {

    private val personDao = mockk<PersonDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: PersonRepository

    @Before
    fun setup() {
        repository = PersonRepository(personDao, auditLogRepository)
    }

    // region 2. 利用者操作テスト (CRUD)

    @Test
    fun CUR_01_insertPerson_logsCorrectly() = runTest {
        val person = createSamplePerson("1")
        coEvery { personDao.insert(any()) } returns 1L

        repository.insertPerson(person, "Feature", "Op")

        coVerify { personDao.insert(match { it.id == "1" && it.lastName == "山田" }) }
        coVerify {
            auditLogRepository.log(
                featureName = "Feature",
                operation = "Op",
                tableName = "person_db",
                actionType = "INSERT",
                affectedId = "1",
                details = match { it.contains("山田 太郎") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun CUR_02_updatePerson_logsCorrectly() = runTest {
        val person = createSamplePerson("1")

        repository.updatePerson(person, "Feature", "Op")

        coVerify { personDao.update(match { it.id == "1" && it.lastName == "山田" }) }
        coVerify {
            auditLogRepository.log(
                featureName = "Feature",
                operation = "Op",
                tableName = "person_db",
                actionType = "UPDATE",
                affectedId = "1",
                details = match { it.contains("山田 太郎") },
                resultType = "SUCCESS"
            )
        }
    }

    // endregion

    // region 3. 重複確認ロジックテスト (Duplicate Check)

    @Test
    fun DUP_01_findExistingPerson_usesDateRange() = runTest {
        val birthday = Instant.parse("1950-01-01T12:00:00Z")
        val person = createSamplePerson("NEW").copy(birthday = birthday)
        
        repository.findExistingPerson(person)

        // Verify that start/end of the day is passed to DAO
        val zone = ZoneId.systemDefault()
        val expectedStart = LocalDate.of(1950, 1, 1).atStartOfDay(zone).toInstant()
        val expectedEnd = LocalDate.of(1950, 1, 1).plusDays(1).atStartOfDay(zone).toInstant()

        coVerify {
            personDao.findExistingPerson(
                lastName = "山田",
                firstName = "太郎",
                start = expectedStart,
                end = expectedEnd,
                note = "備考"
            )
        }
    }

    // endregion

    // region 4. データ取得テスト (Query)

    @Test
    fun GET_01_getAllPersons() = runTest {
        repository.getAllPersons()
        verify { personDao.getAllPersons() }
    }

    @Test
    fun GET_02_getPersonById() = runTest {
        repository.getPersonById("u1")
        verify { personDao.getPersonById("u1") }
    }

    // endregion

    private fun createSamplePerson(id: String) = Person(
        id = id,
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z"),
        note = "備考"
    )
}
