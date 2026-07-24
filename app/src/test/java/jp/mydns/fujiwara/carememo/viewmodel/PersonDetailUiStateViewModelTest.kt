@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailUiStateViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private lateinit var viewModel: PersonDetailUiStateViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPerson = Person(
        id = 1,
        lastName = "詳細",
        firstName = "太郎",
        lastNameFurigana = "しょうさい",
        firstNameFurigana = "たろう",
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())

        viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `loadPersonを実行したとき、指定したIDの利用者が取得できること`() = runTest(testDispatcher) {
        viewModel.loadPerson(1)

        viewModel.uiState.test {
            assertEquals(testPerson, awaitItem().person)
        }
    }

    @Test
    fun `setCategoryを実行したとき、currentCategoryが更新されること`() = runTest(testDispatcher) {
        viewModel.setCategory(Category.BP_AND_PULSE)
        
        assertEquals(Category.BP_AND_PULSE, viewModel.uiState.value.currentCategory)
    }

    @Test
    fun `loadPersonで例外が発生したとき、isLoadingがfalseになりエラーログが記録されること`() = runTest(testDispatcher) {
        every { personRepository.getPersonById(any()) } returns flow {
            throw RuntimeException("Load error")
        }

        viewModel.loadPerson(1)

        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonDetail",
                operation = "loadPerson",
                tableName = "person_db",
                actionType = "ERROR",
                affectedId = "1",
                details = match { it?.contains("Load error") == true },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
