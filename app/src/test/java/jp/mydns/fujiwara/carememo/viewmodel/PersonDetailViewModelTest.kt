package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
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

    private val repository = mockk<CareMemoRepository>(relaxed = true)
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
        every { repository.getPersonById(any()) } returns flowOf(testPerson)
        
        viewModel = PersonDetailViewModel(repository, userSettingsRepository)
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
    fun `loadRecordsを実行したとき、対応するカテゴリのデータ取得メソッドが呼ばれること`() = runTest {
        viewModel.loadRecords(1, Category.BP_AND_PULSE)

        // 検証: リポジトリの該当メソッドが呼ばれたか
        coVerify { repository.getBpAndPulseByPersonId(1) }
    }
}
