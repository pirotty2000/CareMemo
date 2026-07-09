@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class PersonRepositoryTest {

    private val personDao = mockk<PersonDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: PersonRepository

    @Before
    fun setup() {
        repository = PersonRepository(personDao, auditLogRepository)
    }

    @Test
    fun `insertPersonを実行したとき、DAOのinsertとログ出力が行われること`() = runTest {
        val person = Person(id = 0, lastName = "山田", firstName = "太郎", lastNameFurigana = "やまだ", firstNameFurigana = "たろう", birthday = Instant.now())
        coEvery { personDao.insert(person) } returns 1L

        repository.insertPerson(person, "画面", "登録")

        coVerify { personDao.insert(person) }
        coVerify {
            auditLogRepository.log(
                screenName = "画面",
                operation = "登録",
                tableName = "person_db",
                actionType = "INSERT",
                affectedId = "1",
                details = match { it.contains("山田 太郎") }
            )
        }
    }

    @Test
    fun `updatePersonを実行したとき、DAOのupdateとログ出力が行われること`() = runTest {
        val person = Person(id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "やまだ", firstNameFurigana = "たろう", birthday = Instant.now())

        repository.updatePerson(person, "画面", "更新")

        coVerify { personDao.update(person) }
        coVerify {
            auditLogRepository.log(
                screenName = "画面",
                operation = "更新",
                tableName = "person_db",
                actionType = "UPDATE",
                affectedId = "1",
                details = match { it.contains("山田 太郎") }
            )
        }
    }
}
