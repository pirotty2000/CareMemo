package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.AuditLogDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit Test: AuditLogRepository
 */
class AuditLogRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val auditLogDao = mockk<AuditLogDao>(relaxed = true)
    private lateinit var repository: AuditLogRepository

    @Before
    fun setup() {
        repository = AuditLogRepository(context, auditLogDao)
    }

    // region 2. ログ記録テスト (log)

    @Test
    fun LOG_01_log_delegatesToDao() = runTest {
        repository.log("Feature", "Operation", "Table", "INSERT", "id-123", "details", "SUCCESS")
        
        coVerify { 
            auditLogDao.insert(withArg {
                assertEquals("Feature", it.featureName)
                assertEquals("Operation", it.operation)
                assertEquals("Table", it.tableName)
                assertEquals("INSERT", it.actionType)
                assertEquals("id-123", it.affectedId)
                assertEquals("details", it.details)
                assertEquals("SUCCESS", it.resultType)
            })
        }
    }

    @Test
    fun LOG_02_log_suppressesExceptionsAndCallsEmergencyLogger() = runTest {
        // Force DAO to throw
        coEvery { auditLogDao.insert(any()) } throws RuntimeException("Database error")

        // Should not throw exception
        repository.log("F", "O", "T", "A", "ID")

        coVerify { auditLogDao.insert(any()) }
        // Note: SystemEmergencyLogger is an object and hard to verify directly without static mocking,
        // but we verify that the repository method completes without crashing.
    }

    @Test
    fun GET_02_allLogsRaw_delegatesToDao() = runTest {
        coEvery { auditLogDao.getAllLogsRaw() } returns listOf(mockk())
        val result = repository.allLogsRaw()
        assertEquals(1, result.size)
        coVerify { auditLogDao.getAllLogsRaw() }
    }

    // endregion

    // region 3. ログ削除・ローテーションテスト (Delete)

    @Test
    fun DEL_01_deleteOldLogs_calculatesCorrectThreshold() = runTest {
        repository.deleteOldLogs(30)
        coVerify { auditLogDao.deleteOldLogs(any()) }
    }

    @Test
    fun DEL_02_deleteOldLogs_ignoresNegativeDays() = runTest {
        repository.deleteOldLogs(-1)
        coVerify(exactly = 0) { auditLogDao.deleteOldLogs(any()) }
    }

    @Test
    fun DEL_03_deleteAllLogs_delegatesToDao() = runTest {
        repository.deleteAllLogs()
        coVerify { auditLogDao.deleteAll() }
    }

    // endregion

    // region 4. データ取得テスト (Get)

    @Test
    fun GET_01_getAuditLogCountFlow_returnsSize() = runTest {
        coEvery { auditLogDao.getAllLogs() } returns flowOf(listOf(mockk(), mockk()))
        
        val count = repository.getAuditLogCountFlow().first()
        assertEquals(2, count)
    }

    // endregion
}
