package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.*
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.EmergencyContactDao
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
    fun CUR_01_saveContact_insert() = runTest {
        val contact = createSampleContact("generated-uuid")
        coEvery { emergencyContactDao.insert(any()) } returns 1L

        repository.saveContact(contact, isUpdate = false, "Feature", "Op")

        coVerify {
            emergencyContactDao.insert(match {
                it.id == "generated-uuid" && it.facilityName == "Test Clinic"
            })
        }
        coVerify {
            auditLogRepository.log(any(), any(), any(), "INSERT", "generated-uuid", any(), "SUCCESS")
        }
    }

    @Test
    fun CUR_02_saveContact_update() = runTest {
        val contact = createSampleContact("id-1")
        repository.saveContact(contact, isUpdate = true, "Feature", "Op")

        coVerify { emergencyContactDao.update(match { it.id == "id-1" }) }
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
    @Suppress("UNUSED_EXPRESSION")
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
