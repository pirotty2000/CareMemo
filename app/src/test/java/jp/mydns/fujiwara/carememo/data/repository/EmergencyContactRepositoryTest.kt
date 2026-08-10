package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.*
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.EmergencyContactDao
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit Test: EmergencyContactRepository
 */
class EmergencyContactRepositoryTest {

    private val emergencyContactDao = mockk<EmergencyContactDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private lateinit var repository: EmergencyContactRepository

    @Before
    fun setup() {
        repository = EmergencyContactRepository(emergencyContactDao, auditLogRepository)
    }

    // region 2. 連絡先操作テスト (CRUD)

    @Test
    fun CUR_01_insertContact_newRecord() = runTest {
        val contact = createSampleContact("NEW")
        coEvery { emergencyContactDao.insert(any()) } returns 1L

        repository.insertContact(contact, "Feature", "Op")

        // UUID was generated internally
        coVerify { 
            emergencyContactDao.insert(match { 
                !IdLogic.isNew(it.id) && it.facilityName == "Test Clinic" 
            }) 
        }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "INSERT", any(), any(), "SUCCESS") 
        }
    }

    @Test
    fun CUR_02_insertContact_maintainId() = runTest {
        val contact = createSampleContact("persisted-id")
        repository.insertContact(contact, "Feature", "Op")

        coVerify { emergencyContactDao.insert(match { it.id == "persisted-id" }) }
    }

    @Test
    fun CUR_03_updateContact_logsCorrectly() = runTest {
        val contact = createSampleContact("id-1")
        repository.updateContact(contact, "Feature", "Op")

        coVerify { emergencyContactDao.update(any()) }
        coVerify { 
            auditLogRepository.log(
                featureName = "Feature",
                operation = "Op",
                tableName = "emergency_contact_db",
                actionType = "UPDATE",
                affectedId = "id-1",
                details = match { it.contains("Test Clinic") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun CUR_04_deleteContact() = runTest {
        val contact = createSampleContact("id-1")
        repository.deleteContact(contact, "Feature", "Op")

        coVerify { emergencyContactDao.delete(contact) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "DELETE", "id-1", any(), "SUCCESS") 
        }
    }

    // endregion

    // region 3. データ取得テスト (Query)

    @Test
    fun GET_01_getContactsByPersonId() = runTest {
        repository.getContactsByPersonId("u1")
        verify { emergencyContactDao.getByPersonId("u1") }
    }

    @Test
    fun GET_02_getContactById() = runTest {
        repository.getContactById("c1")
        coVerify { emergencyContactDao.getById("c1") }
    }

    // endregion

    private fun createSampleContact(id: String) = EmergencyContact(
        id = id,
        personId = "u1",
        contactType = "DOCTOR",
        facilityName = "Test Clinic",
        priority = 0,
        updatedAt = Instant.now()
    )
}
