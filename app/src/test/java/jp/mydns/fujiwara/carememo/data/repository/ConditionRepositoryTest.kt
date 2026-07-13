@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionAtVisitDao
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.ConditionPhotoDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ConditionRepositoryTest {

    private val conditionAtVisitDao = mockk<ConditionAtVisitDao>(relaxed = true)
    private val conditionPhotoDao = mockk<ConditionPhotoDao>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var repository: ConditionRepository

    @Before
    fun setup() {
        repository = ConditionRepository(conditionAtVisitDao, conditionPhotoDao, auditLogRepository)
    }

    @Test
    fun `insertConditionAtVisitを実行したとき、DAOのinsertが呼ばれ、かつ操作ログが記録されること`() = runTest {
        val record = ConditionAtVisit(
            id = 0,
            personId = 1,
            title = "テスト",
            condition = "内容",
            author = "記録者",
            recordTime = Instant.now()
        )
        val screen = "テスト画面"
        val op = "保存ボタン押下"
        
        coEvery { conditionAtVisitDao.insert(record) } returns 100L

        val resultId = repository.insertConditionAtVisit(record, screen, op)

        assertEquals(100L, resultId)
        coVerify { conditionAtVisitDao.insert(record) }
        coVerify {
            auditLogRepository.log(
                featureName = screen,
                operation = op,
                tableName = "condition_at_visit_db",
                actionType = "INSERT",
                affectedId = "100",
                details = match { it.contains("PersonId: 1") && it.contains("Title: テスト") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun `deleteConditionAtVisitを実行したとき、DAOのdeleteが呼ばれ、かつ操作ログが記録されること`() = runTest {
        val record = ConditionAtVisit(
            id = 10,
            personId = 1,
            title = "テスト",
            condition = "内容",
            author = "記録者",
            recordTime = Instant.now()
        )
        
        repository.deleteConditionAtVisit(record, "画面", "削除")

        coVerify { conditionAtVisitDao.delete(record) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "削除",
                tableName = "condition_at_visit_db",
                actionType = "DELETE",
                affectedId = "10",
                details = match { it.contains("PersonId: 1") },
                resultType = "SUCCESS"
            )
        }
    }

    @Test
    fun `insertConditionPhotoを実行したとき、DAOのinsertが呼ばれ、かつ操作ログが記録されること`() = runTest {
        val photo = ConditionPhoto(
            id = 0,
            conditionId = 5,
            personId = 1,
            photoFileName = "p.jpg",
            thumbnailFileName = "t.jpg",
            capturedAt = Instant.now()
        )

        coEvery { conditionPhotoDao.insert(photo) } returns 200L

        repository.insertConditionPhoto(photo, "画面", "写真保存")

        coVerify { conditionPhotoDao.insert(photo) }
        coVerify {
            auditLogRepository.log(
                featureName = "画面",
                operation = "写真保存",
                tableName = "condition_photo_db",
                actionType = "INSERT",
                affectedId = "200",
                details = match { it.contains("PersonId: 1") && it.contains("ConditionId: 5") },
                resultType = "SUCCESS"
            )
        }
    }
}
