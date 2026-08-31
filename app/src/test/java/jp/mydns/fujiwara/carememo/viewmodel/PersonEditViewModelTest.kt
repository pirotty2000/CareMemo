package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonEditViewEvent
import jp.mydns.fujiwara.carememo.ui.navigation.EditResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

/**
 * Logic Test: PersonEditViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest {

    private val repository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val securitySession = SecuritySession()
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()
    
    private val testPerson = Person(
        id = "u1",
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        note = "備考"
    )
    
    private val isNameMaskingEnabledFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { userSettingsRepository.isNameMaskingEnabled } returns isNameMaskingEnabledFlow
        coEvery { repository.getPersonById("u1") } returns flowOf(testPerson)
        coEvery { repository.findExistingPerson(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel(personId: String? = AppSpecifications.Id.NEW_RECORD_ID): PersonEditViewModel {
        return PersonEditViewModel(
            SavedStateHandle(if (!IdLogic.isNew(personId)) mapOf("personId" to personId!!) else emptyMap()),
            repository,
            userSettingsRepository,
            securitySession,
            auditLogRepository
        )
    }

    // region 2. 初期化・データロードテスト (Initialization)

    @Test
    fun INI_01_initialLoad_newMode() = runTest {
        val viewModel = createViewModel(AppSpecifications.Id.NEW_RECORD_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertEquals("", state.lastName)
        assertFalse(state.isChanged)
    }

    @Test
    fun INI_02_initialLoad_editMode() = runTest {
        val viewModel = createViewModel("u1")
        
        viewModel.uiState.test {
            // Skip intermediate state transitions during loading
            advanceUntilIdle()
            
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isNew)
            assertEquals("山田", loaded.lastName)
            assertEquals("25", loaded.year) // 1950 is Showa 25
            assertEquals(BirthEra.SHOWA, loaded.era)
            assertFalse(loaded.isChanged)
        }
    }

    @Test
    fun INI_03_maskingSettingReflection() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()
        
        isNameMaskingEnabledFlow.value = true
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isNameMaskingEnabled)
    }

    // endregion

    // region 3. 入力・状態管理テスト (Input & State)

    @Test
    fun INP_01_INP_03_inputUpdate_reactivity() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        viewModel.updateLastName("佐藤")
        
        val state = viewModel.uiState.value
        assertEquals("佐藤", state.lastName)
        assertTrue(state.isChanged)
        assertFalse(state.isValid) // Missing other required fields
    }

    @Test
    fun INP_02_fullInput_validationSuccess() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        viewModel.updateLastName("佐藤")
        viewModel.updateFirstName("花子")
        viewModel.updateLastNameFurigana("さとう")
        viewModel.updateFirstNameFurigana("はなこ")
        // Use a valid Reiwa date (Reiwa 1 starts from May 1st)
        viewModel.updateYear("1")
        viewModel.updateMonth("5")
        viewModel.updateDay("1")
        viewModel.updateEra(BirthEra.REIWA)
        
        assertTrue("Input should be valid with full correct data", viewModel.uiState.value.isValid)
    }

    // endregion

    // region 4. 保存・処理実行テスト (Saving)

    @Test
    fun SAV_01_save_newSuccess() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        // Setup valid state
        viewModel.updateLastName("A")
        viewModel.updateFirstName("B")
        viewModel.updateLastNameFurigana("あ")
        viewModel.updateFirstNameFurigana("い")
        viewModel.updateEra(BirthEra.REIWA)
        viewModel.updateYear("1")
        viewModel.updateMonth("5")
        viewModel.updateDay("1")

        viewModel.viewEvent.test {
            viewModel.save()
            advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is PersonEditViewEvent.NavigateBack)
            val navBack = event as PersonEditViewEvent.NavigateBack
            assertEquals(EditResult.ADDED, navBack.result)
            assertEquals("A　B", navBack.personName)
        }

        coVerify { repository.insertPerson(any(), any(), any()) }
    }

    @Test
    fun SAV_02_save_updateSuccess() = runTest {
        val viewModel = createViewModel("u1")
        advanceUntilIdle()

        viewModel.updateLastName("佐藤")
        viewModel.updateFirstName("花子")

        viewModel.viewEvent.test {
            viewModel.save()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PersonEditViewEvent.NavigateBack)
            val navBack = event as PersonEditViewEvent.NavigateBack
            assertEquals(EditResult.UPDATED, navBack.result)
            assertEquals("佐藤　花子", navBack.personName)
        }

        coVerify { repository.updatePerson(any(), any(), any()) }
    }

    @Test
    fun SAV_02_save_updateSuccess_masked() = runTest {
        isNameMaskingEnabledFlow.value = true
        val viewModel = createViewModel("u1")
        advanceUntilIdle()

        viewModel.updateLastName("佐藤")
        viewModel.updateFirstName("花子")

        viewModel.viewEvent.test {
            viewModel.save()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PersonEditViewEvent.NavigateBack)
            val navBack = event as PersonEditViewEvent.NavigateBack
            assertEquals(EditResult.UPDATED, navBack.result)
            assertEquals("佐○　花○", navBack.personName)
        }
    }

    @Test
    fun SAV_03_save_duplicateActiveBlocked() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        // Setup valid state (Reiwa 1/5/1 is valid)
        viewModel.updateLastName("Duplicate")
        viewModel.updateFirstName("User")
        viewModel.updateLastNameFurigana("あ")
        viewModel.updateFirstNameFurigana("い")
        viewModel.updateEra(BirthEra.REIWA)
        viewModel.updateYear("1")
        viewModel.updateMonth("5")
        viewModel.updateDay("1")

        // Mock active duplicate
        coEvery { repository.findExistingPerson(any()) } returns testPerson.copy(deletedAt = null)

        viewModel.uiEventFlow.test {
            viewModel.save()
            advanceUntilIdle()
            
            val event = awaitItem()
            assertTrue(event is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes)
            assertEquals(R.string.main_err_duplicate_active, (event as BaseUiStateViewModel.UiEvent.ShowErrorDialogRes).messageResId)
        }
        
        coVerify(exactly = 0) { repository.insertPerson(any(), any(), any()) }
    }

    @Test
    fun SAV_04_save_doubleClickPrevention() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        // Setup valid state
        viewModel.updateLastName("A")
        viewModel.updateFirstName("B")
        viewModel.updateLastNameFurigana("あ")
        viewModel.updateFirstNameFurigana("い")
        viewModel.updateEra(BirthEra.REIWA)
        viewModel.updateYear("1")
        viewModel.updateMonth("5")
        viewModel.updateDay("1")

        // Mock repository with delay
        coEvery { repository.insertPerson(any(), any(), any()) } coAnswers {
            delay(1000.milliseconds)
        }

        // Call save twice
        viewModel.save()
        viewModel.save()

        advanceUntilIdle()

        // Verify repository was called ONLY once
        coVerify(exactly = 1) { repository.insertPerson(any(), any(), any()) }
    }

    // endregion

    // region 5. 安全性・例外テスト (Safety)

    @Test
    fun ERR_01_loadFailure_safety() = runTest {
        coEvery { repository.getPersonById("u1") } returns flow { throw RuntimeException("Load Error") }

        val viewModel = createViewModel("u1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", "u1", match { it.contains("Load Error") }, any()) }
    }

    @Test
    fun ERR_02_save_validationFailureLogs() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        viewModel.updateLastName("") // Empty required
        viewModel.save()
        advanceUntilIdle()

        coVerify { auditLogRepository.log(any(), any(), any(), "ERROR", any(), match { it.contains("EMPTY_LAST_NAME") }, "VALIDATION_ERROR") }
    }

    // endregion

    // region 6. 状態復元テスト (State Restoration)

    @Test
    fun RST_01_restore_input() = runTest {
        // SavedStateHandle に入力値をセット
        val handle = SavedStateHandle(mapOf(
            "restoration_version" to 1,
            "input_last_name" to "佐藤",
            "input_first_name" to "花子",
            "input_era" to BirthEra.REIWA.name,
            "input_year" to "1",
            "input_month" to "5",
            "input_day" to "1",
            "input_is_new" to true
        ))
        
        val viewModel = PersonEditViewModel(handle, repository, userSettingsRepository, securitySession, auditLogRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("佐藤", state.lastName)
        assertEquals("花子", state.firstName)
        assertEquals(BirthEra.REIWA, state.era)
    }

    @Test
    fun RST_02_RST_03_restore_baseline_and_isChanged() = runTest {
        // 既存編集モードでの復元
        val handle = SavedStateHandle(mapOf(
            "personId" to "u1",
            "restoration_version" to 1,
            // Baseline: 山田太郎 (1950/1/1)
            "baseline_last_name" to "山田",
            "baseline_first_name" to "太郎",
            "baseline_last_name_furigana" to "ヤマダ",
            "baseline_first_name_furigana" to "タロウ",
            "baseline_birthday_epoch" to testPerson.birthday.toEpochMilli(),
            "baseline_note" to "備考",
            // Current Input: 佐藤次郎 (変更あり)
            "input_last_name" to "佐藤",
            "input_first_name" to "次郎",
            "input_last_name_furigana" to "サトウ",
            "input_first_name_furigana" to "ジロウ",
            "input_note" to "備考変更",
            "input_era" to BirthEra.SHOWA.name,
            "input_year" to "25",
            "input_month" to "1",
            "input_day" to "1",
            "input_is_new" to false
        ))

        val viewModel = PersonEditViewModel(handle, repository, userSettingsRepository, securitySession, auditLogRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("佐藤", state.lastName)
        // baseline が正しく復帰し、現在の入力と比較されて isChanged == true になること
        assertTrue("isChanged should be true when input differs from baseline", state.isChanged)
        
        // 元に戻すと false になること (baseline の全フィールドを一致させる)
        viewModel.updateLastName("山田")
        viewModel.updateFirstName("太郎")
        viewModel.updateLastNameFurigana("ヤマダ")
        viewModel.updateFirstNameFurigana("タロウ")
        viewModel.updateNote("備考")
        assertFalse("isChanged should be false when input matches baseline", viewModel.uiState.value.isChanged)
    }

    @Test
    fun RST_04_cleanup_on_success() = runTest {
        // バリデーションを通る完全な入力を提供
        val handle = SavedStateHandle(mapOf(
            "personId" to "u1",
            "restoration_version" to 1,
            "input_last_name" to "佐藤",
            "input_first_name" to "花子",
            "input_last_name_furigana" to "サトウ",
            "input_first_name_furigana" to "ハナコ",
            "input_era" to BirthEra.REIWA.name,
            "input_year" to "1",
            "input_month" to "5",
            "input_day" to "1",
            "input_is_new" to false
        ))

        val viewModel = PersonEditViewModel(handle, repository, userSettingsRepository, securitySession, auditLogRepository)
        advanceUntilIdle()

        // 保存実行 (coEvery での成功を前提)
        viewModel.save()
        advanceUntilIdle()

        // 保存成功後に SavedStateHandle から復元用データが削除されていること
        assertFalse("Restoration version should be removed", handle.contains("restoration_version"))
        assertFalse("Input data should be removed", handle.contains("input_last_name"))
    }

    // endregion

    // region 7. バリデーション・フィードバックテスト (Validation Feedback)

    @Test
    fun FBK_01_emptyField_feedback_afterTouch() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        // 初期状態ではエラーはないはず（touched ではないため）
        assertNull(viewModel.uiState.value.fieldErrors["lastName"])

        // 姓を空のまま touched にする
        viewModel.markFieldAsTouched("lastName")
        advanceUntilIdle()

        assertEquals(R.string.main_err_edit_empty_last_name, viewModel.uiState.value.fieldErrors["lastName"])
    }

    @Test
    fun FBK_02_tooLongInput_feedback() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        // 51文字入力（上限50）
        viewModel.updateLastName("A".repeat(51))
        advanceUntilIdle()

        assertEquals(R.string.main_err_name_too_long, viewModel.uiState.value.fieldErrors["lastName"])
    }

    @Test
    fun FBK_03_errorClears_onCorrectInput() = runTest {
        val viewModel = createViewModel("_new")
        advanceUntilIdle()

        viewModel.markFieldAsTouched("lastName")
        assertEquals(R.string.main_err_edit_empty_last_name, viewModel.uiState.value.fieldErrors["lastName"])

        viewModel.updateLastName("山田")
        advanceUntilIdle()

        assertNull("Error should be cleared on valid input", viewModel.uiState.value.fieldErrors["lastName"])
    }

    // endregion
}
