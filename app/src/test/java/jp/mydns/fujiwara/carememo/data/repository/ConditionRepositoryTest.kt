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

    private val context = mockk<android.content.Context>(relaxed = true)
    private val conditionAtVisitDao = mockk<ConditionAtVisitDao>(relaxed = true)
    private val conditionPhotoDao = mockk<ConditionPhotoDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: ConditionRepository

    @Before
    fun setup() {
        repository = ConditionRepository(context, conditionAtVisitDao, conditionPhotoDao, auditLogRepository)
    }

    // region 2. 所見メモ操作テスト (ConditionAtVisit)

    @Test
    fun MEM_01_saveConditionAtVisit_insert() = runTest {
        val record = createSampleRecord("100")
        coEvery { conditionAtVisitDao.insert(any()) } returns 1L

        repository.saveConditionAtVisit(record, isUpdate = false, "Feature", "Op")

        coVerify { conditionAtVisitDao.insert(match { it.id == "100" }) }
        coVerify {
            auditLogRepository.log(any(), any(), any(), "INSERT", "100", any(), "SUCCESS")
        }
    }

    @Test
    fun MEM_02_saveConditionAtVisit_update() = runTest {
        val record = createSampleRecord("200")
        repository.saveConditionAtVisit(record, isUpdate = true, "Feature", "Op")

        coVerify {
            auditLogRepository.log(any(), any(), any(), "UPDATE", "200", any(), "SUCCESS")
        }
    }

    @Test
    fun MEM_03_deleteConditionAtVisit() = runTest {
        val record = createSampleRecord("300")
        repository.deleteConditionAtVisit(record, "F", "O")

        coVerify { conditionAtVisitDao.delete(any()) }
        coVerify { 
            auditLogRepository.log(any(), any(), any(), "DELETE", "300", any(), "SUCCESS") 
        }
    }

    // endregion

    // region 3. 写真メタデータ操作テスト (ConditionPhoto)

    @Test
    fun PHT_01_saveConditionPhoto() = runTest {
        val photo = createSamplePhoto()
        repository.saveConditionPhoto(photo, isUpdate = false, "F", "O")

        coVerify { conditionPhotoDao.insert(any()) }
        coVerify { auditLogRepository.log(any(), any(), "condition_photo_db", "INSERT", "p1", any(), "SUCCESS") }
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
        repository.adoptFileAsPhoto("u1", "c1", "img.jpg", "thumb.jpg", capturedAt, "generated-p1", "F", "O")

        coVerify {
            conditionPhotoDao.insert(match {
                it.id == "generated-p1" && it.personId == "u1" && it.photoFileName == "img.jpg" && it.capturedAt == capturedAt
            })
        }
    }

    // endregion

    private fun createSampleRecord(id: String = "100") = ConditionAtVisit(
        id = id,
        personId = "u1",
        title = "Title",
        condition = "Content",
        author = "Author",
        recordTime = Instant.now()
    )

    private fun createSamplePhoto() = ConditionPhoto(
        id = "p1",
        conditionId = "c1",
        personId = "u1",
        photoFileName = "img.jpg",
        thumbnailFileName = "thumb.jpg",
        capturedAt = Instant.now()
    )
}
