@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * SCR-S-003 DeleteOrRestorePersonViewModel のロジック・安全性テスト
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-S-003_DeleteOrRestorePerson.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteOrRestorePersonViewModelTest {

    private val repository = mockk<DeleteOrRestorePersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private lateinit var viewModel: DeleteOrRestorePersonViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "削除済",
        firstName = "太郎",
        lastNameFurigana = "さくじょずみ",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
        
        every { repository.getArchivedPersons() } returns flow { emit(listOf(testPerson)) }

        viewModel = DeleteOrRestorePersonViewModel(
            repository,
            userSettingsRepository,
            auditLogRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // ======================================================================================
    // 3. ロジック・安全性テスト (DeleteOrRestorePersonViewModel)
    // ======================================================================================

    @Test
    fun lg01_operationFailure_safety() = runTest {
        // 復元操作中に例外が発生する状況をシミュレート
        coEvery { repository.restorePerson(any(), any(), any()) } throws RuntimeException("Restore Error")

        viewModel.toggleSelection(1)
        viewModel.restoreSelectedPersons(listOf(testPerson))
        advanceUntilIdle()

        // 検証: isLoading が false に戻ること
        assertEquals(false, viewModel.isLoading.value)
        
        // 検証: 監査ログにエラーが記録されること
        coVerify {
            auditLogRepository.log(
                featureName = "DeleteOrRestorePerson",
                operation = "restoreSelectedPersons",
                actionType = "ERROR",
                details = match { it.contains("Restore Error") },
                resultType = "OTHER_ERROR",
                tableName = any(),
                affectedId = any()
            )
        }
    }
}
