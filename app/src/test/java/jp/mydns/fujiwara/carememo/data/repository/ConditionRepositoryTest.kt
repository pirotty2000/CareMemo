package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionAtVisitDao
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.ConditionPhotoDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit Test: ConditionRepository
 */
class ConditionRepositoryTest {

    private val conditionAtVisitDao = mockk<ConditionAtVisitDao>(relaxed = true)
    private val conditionPhotoDao = mockk<ConditionPhotoDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: ConditionRepository

    @Before
    fun setup() {
        repository = ConditionRepository(conditionAtVisitDao, conditionPhotoDao, auditLogRepository)
    }

    // region 2. 所見メモ操作テスト (ConditionAtVisit)

    @Test
    fun MEM_01_insertConditionAtVisit_new() = runTest {
        val record = createSampleRecord("100")
        coEvery { conditionAtVisitDao.insert(any()) } returns 1L

        repository.insertConditionAtVisit(record, "Feature", "Op", isUpdate = false)

        coVerify { conditionAtVisitDao.insert(match { it.id == "100" }) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "INSERT", "100", any(), "SUCCESS") 
        }
    }

    @Test
    fun MEM_02_insertConditionAtVisit_update() = runTest {
        val record = createSampleRecord("100")
        repository.insertConditionAtVisit(record, "Feature", "Op", isUpdate = true)

        coVerify { 
            auditLogRepository.log(any(), any(), any(), "UPDATE", "100", any(), "SUCCESS") 
        }
    }

    @Test
    fun MEM_03_deleteConditionAtVisit() = runTest {
        val record = createSampleRecord("100")
        repository.deleteConditionAtVisit(record, "F", "O")

        coVerify { conditionAtVisitDao.delete(any()) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "DELETE", "100", any(), "SUCCESS") 
        }
    }

    // endregion

    // region 3. 写真メタデータ操作テスト (ConditionPhoto)

    @Test
    fun PHT_01_insertConditionPhoto() = runTest {
        val photo = createSamplePhoto("p1", "c1")
        repository.insertConditionPhoto(photo, "F", "O")

        coVerify { conditionPhotoDao.insert(any()) }
        coVerify { auditLogRepository.log(any(), any(), "condition_photo_db", any(), "p1", any(), "SUCCESS") }
    }

    @Test
    fun PHT_02_linkTemporaryPhotosToRecord() = runTest {
        repository.linkTemporaryPhotosToRecord("u1", "c1", "F", "O")

        coVerify { conditionPhotoDao.linkTemporaryPhotosToRecord("u1", "c1") }
        coVerify { auditLogRepository.log(any(), any(), any(), "UPDATE", "person:u1", any(), "SUCCESS") }
    }

    @Test
    fun PHT_04_adoptFileAsPhoto() = runTest {
        val capturedAt = Instant.now()
        repository.adoptFileAsPhoto("u1", "c1", "img.jpg", "thumb.jpg", capturedAt, "F", "O")

        coVerify { 
            conditionPhotoDao.insert(match { 
                it.personId == "u1" && it.photoFileName == "img.jpg" && it.capturedAt == capturedAt 
            }) 
        }
    }

    // endregion

    private fun createSampleRecord(id: String) = ConditionAtVisit(
        id = id,
        personId = "u1",
        title = "Title",
        condition = "Content",
        author = "Author",
        recordTime = Instant.now()
    )

    private fun createSamplePhoto(id: String, conditionId: String) = ConditionPhoto(
        id = id,
        conditionId = conditionId,
        personId = "u1",
        photoFileName = "img.jpg",
        thumbnailFileName = "thumb.jpg",
        capturedAt = Instant.now()
    )
}
