package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Unit Test: Async Lifecycle (BP-01)
 * 
 * Verifies asynchronous processing lifecycles, especially state clearing, 
 * cancellations, and race conditions during person switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonLifecycleTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val conditionRepository = mockk<ConditionRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { userSettingsRepository.defaultRecorderName } returns flowOf("")
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(jp.mydns.fujiwara.carememo.data.PersonCategorySummary())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun BP_01_01_loadPerson_clearsStateImmediately() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle())
        val person1 = Person(id = "1", lastName = "First", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now())
        
        every { personRepository.getPersonById("1") } returns flowOf(person1)
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.personId)

        // Start loading second person with delay
        every { personRepository.getPersonById("2") } returns flow {
            delay(1000.milliseconds)
            emit(Person(id = "2", lastName = "Second", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        
        viewModel.loadPerson("2")
        
        // State must be cleared IMMEDIATELY before async finishes
        assertNull(viewModel.uiState.value.personId)
        
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("2", viewModel.uiState.value.personId)
    }

    @Test
    fun BP_01_02_rapidSwitch_cancelsPreviousLoad() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle())

        every { personRepository.getPersonById("1") } returns flow {
            delay(2000.milliseconds)
            emit(Person(id = "1", lastName = "Slow", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        every { personRepository.getPersonById("2") } returns flow {
            delay(500.milliseconds)
            emit(Person(id = "2", lastName = "Fast", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }

        viewModel.loadPerson("1")
        advanceTimeBy(100.milliseconds)
        viewModel.loadPerson("2")

        testDispatcher.scheduler.advanceUntilIdle()

        // Final state should be person 2
        assertEquals("2", viewModel.uiState.value.personId)
        assertEquals("Fast", viewModel.uiState.value.person?.lastName)
    }

    @Test
    fun BP_01_02_switchPerson_resetsSubclassState() = runTest {
        val viewModel = PersonConditionViewModel(
            conditionRepository, 
            personRepository, 
            summaryRepository, 
            userSettingsRepository, 
            auditLogRepository,
            mockk<Context>(relaxed = true),
            mockk(relaxed = true)
        )
        
        every { personRepository.getPersonById("1") } returns flowOf(Person(id = "1", lastName = "A", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateSearchQuery("Query")
        assertEquals("Query", viewModel.uiState.value.searchQuery)

        // Switch to person 2
        every { personRepository.getPersonById("2") } returns flow {
            delay(1000.milliseconds)
            emit(Person(id = "2", lastName = "B", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now()))
        }
        viewModel.loadPerson("2")

        // Search query must be reset IMMEDIATELY
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun BP_01_03_sameId_avoidsReload() = runTest {
        val viewModel = PersonDetailUiStateViewModel(personRepository, summaryRepository, userSettingsRepository, auditLogRepository, SavedStateHandle())
        val person1 = Person(id = "1", lastName = "P1", firstName = "", lastNameFurigana = "", firstNameFurigana = "", birthday = Instant.now())
        
        every { personRepository.getPersonById("1") } returns flowOf(person1)
        
        viewModel.loadPerson("1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.personId)

        // Load same ID again
        viewModel.loadPerson("1")
        
        // Should NOT trigger reload (null-flash)
        assertEquals("1", viewModel.uiState.value.personId)
    }
}
