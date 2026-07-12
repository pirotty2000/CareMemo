@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.AuditLogDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AuditLogRepositoryTest {

    private val auditLogDao = mockk<AuditLogDao>(relaxed = true)
    private lateinit var repository: AuditLogRepository

    @Before
    fun setup() {
        repository = AuditLogRepository(auditLogDao)
    }

    @Test
    fun `deleteOldLogsを実行したとき、指定日数以前のログ削除がDAOに依頼されること`() = runTest {
        // 30日分保持の設定
        repository.deleteOldLogs(30)

        // Instant.now() を内部で使っているので、おおよその時間で検証
        coVerify { auditLogDao.deleteOldLogs(any()) }
    }

    @Test
    fun `deleteAllLogsを実行したとき、DAOのdeleteAllが呼ばれること`() = runTest {
        repository.deleteAllLogs()
        coVerify { auditLogDao.deleteAll() }
    }

    @Test
    fun `log実行時に例外が発生しても、呼び出し元に例外が伝播しないこと`() = runTest {
        // DAOが例外を投げるように設定
        coEvery { auditLogDao.insert(any()) } throws RuntimeException("DB Error")

        // 例外が発生しても、ここで例外がスローされないことを確認
        repository.log("Screen", "Op", "Table", "Type", "1")

        coVerify { auditLogDao.insert(any()) }
    }
}
