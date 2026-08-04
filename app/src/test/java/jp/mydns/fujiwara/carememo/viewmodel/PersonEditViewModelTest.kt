@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * SCR-M-002 PersonEditViewModel のロジックテスト
 *
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-M-002_PersonEditScreen.md に準拠
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // ======================================================================================
    // 3. ロジック・安全性テスト (PersonEditViewModel)
    // ======================================================================================

    @Test
    fun lg01_loadFailure_safety() = runTest {
        val personId = "1"
        // 既存データのロード中に例外が発生した際
        coEvery { personRepository.getPersonById(personId) } returns flow { throw RuntimeException("Load Error") }

        val viewModel = PersonEditViewModel(
            SavedStateHandle(mapOf("personId" to personId)),
            personRepository,
            userSettingsRepository,
            auditLogRepository
        )
        advanceUntilIdle()

        // isLoading が false になり、監査ログに記録されること
        assertEquals("ローディングが終了していること", false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonEdit",
                operation = "loadPerson",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = personId,
                details = match { it.contains("Load Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg02_saveFailure_safety() = runTest {
        val viewModel = PersonEditViewModel(null, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        // 必須項目入力
        viewModel.updateLastName("山田")
        viewModel.updateFirstName("太郎")
        viewModel.updateLastNameFurigana("ヤマダ")
        viewModel.updateFirstNameFurigana("タロウ")
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")

        // 保存中に例外が発生した際
        coEvery { personRepository.findExistingPerson(any()) } returns null
        coEvery { personRepository.insertPerson(any(), any(), any()) } throws RuntimeException("Save Error")

        viewModel.save()
        advanceUntilIdle()

        // isLoading が false になり、監査ログに記録されること
        assertEquals("ローディングが終了していること", false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonEdit",
                operation = "save",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "",
                details = match { it.contains("Save Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg03_validationFailure_translation() = runTest {
        val viewModel = PersonEditViewModel(null, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        // 必須項目（苗字）を空にする。他の必須項目（ふりがな等）は埋める。
        viewModel.updateLastName("")
        viewModel.updateFirstName("太郎")
        viewModel.updateLastNameFurigana("ヤマダ")
        viewModel.updateFirstNameFurigana("タロウ")
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")

        viewModel.save()
        advanceUntilIdle()

        // バリデーションエラーが監査ログに VALIDATION_ERROR として記録されること
        coVerify {
            auditLogRepository.log(
                featureName = "PersonEdit",
                operation = "save",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "",
                details = match { it.contains("EMPTY_LAST_NAME") },
                resultType = "VALIDATION_ERROR"
            )
        }
    }

    @Test
    fun lg04_uiState_atomicity() = runTest {
        val viewModel = PersonEditViewModel(null, personRepository, userSettingsRepository, auditLogRepository)
        advanceUntilIdle()

        // 初期状態の確認
        assertEquals(false, viewModel.uiState.value.isChanged)
        assertEquals(false, viewModel.uiState.value.isValid)

        // 値を更新
        viewModel.updateLastName("山田")
        
        // lastName が更新されると同時に isChanged も更新されていること（原子性の確認）
        val state = viewModel.uiState.value
        assertEquals("山田", state.lastName)
        assertEquals(true, state.isChanged)
        
        // 全ての必須項目を入力
        viewModel.updateFirstName("太郎")
        viewModel.updateLastNameFurigana("ヤマダ")
        viewModel.updateFirstNameFurigana("タロウ")
        viewModel.updateYear("25")
        viewModel.updateMonth("1")
        viewModel.updateDay("1")

        // isValid が true になっていること
        assertEquals(true, viewModel.uiState.value.isValid)
    }
}
