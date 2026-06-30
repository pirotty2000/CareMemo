package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PersonDetailViewModelTest {

    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private lateinit var viewModel: PersonDetailViewModel
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
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        
        viewModel = PersonDetailViewModel(personRepository, userSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPersonを実行したとき、指定したIDの利用者が取得できること`() = runTest {
        viewModel.loadPerson(1)

        viewModel.currentPerson.test {
            assertEquals(testPerson, awaitItem())
        }
    }

    @Test
    fun `setCategoryを実行したとき、currentCategoryが更新されること`() = runTest {
        viewModel.setCategory(Category.BP_AND_PULSE)
        
        assertEquals(Category.BP_AND_PULSE, viewModel.currentCategory.value)
    }
}
