@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class PersonMedicationViewModelTest {

    private val medicationRepository = mockk<MedicationRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)

    private lateinit var viewModel: PersonMedicationViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fixedInstant = Instant.parse("2023-10-27T10:00:00Z")

    private val testPerson = Person(
        id = 1,
        lastName = "服薬",
        firstName = "太郎",
        lastNameFurigana = "ふくやく",
        firstNameFurigana = "たろう",
        birthday = fixedInstant
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        
        viewModel = PersonMedicationViewModel(
            medicationRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nextMonthを実行したとき、月が進みisLoadingがtrueになること`() = runTest {
        val initialMonth = viewModel.selectedMonth.value
        viewModel.nextMonth()
        
        assertEquals(initialMonth.plusMonths(1), viewModel.selectedMonth.value)
        assertTrue(viewModel.isLoading.value)
    }

    @Test
    fun `recordsByDateは日付ごとにグルーピングされること`() = runTest {
        val date = "2023-10-27"
        val records = listOf(
            MedicationRecord(id = 1, personId = 1, dosageDate = date, timeSlot = 0, status = 1, recordTime = fixedInstant),
            MedicationRecord(id = 2, personId = 1, dosageDate = date, timeSlot = 1, status = 1, recordTime = fixedInstant)
        )
        every { medicationRepository.getMedicationRecordsByMonth(any(), any()) } returns flowOf(records)

        viewModel.loadPerson(1)

        viewModel.recordsByDate.test {
            val result = awaitItem()
            assertTrue(result.containsKey(date))
            assertEquals(2, result[date]?.size)
        }
    }

    @Test
    fun `syncMedicationDayで新規レコードがあるとき、insertが呼ばれること`() = runTest {
        val date = "2023-10-27"
        val newRecord = MedicationRecord(id = 0, personId = 1, dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)
        
        // スロット0に新規、他はnull
        val slotRecords = listOf(newRecord, null, null, null)
        
        viewModel.syncMedicationDay(date, slotRecords)
        
        coVerify { medicationRepository.insertMedicationRecord(newRecord, any(), any()) }
    }

    @Test
    fun `syncMedicationDayで既存レコードがスロットから消えたとき、deleteが呼ばれること`() = runTest {
        val date = "2023-10-27"
        val existingRecord = MedicationRecord(id = 1, personId = 1, dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)
        
        // 既存データをFlowで流しておく
        every { medicationRepository.getMedicationRecordsByMonth(any(), any()) } returns flowOf(listOf(existingRecord))
        
        viewModel.recordsByDate.test {
            viewModel.loadPerson(1)

            // 【重要】目的のデータが Map に反映されるまで待機する
            var found = false
            // 最大 10 回（またはタイムアウトまで）最新の放出をチェック
            for (i in 1..10) {
                val currentMap = awaitItem()
                if (currentMap.containsKey(date)) {
                    found = true
                    break
                }
            }
            assertTrue("テストデータが ViewModel に読み込まれていること", found)

            // スロット0がnull（削除指定）、他もnull
            val slotRecords = listOf(null, null, null, null)
            
            viewModel.syncMedicationDay(date, slotRecords)
            
            // 検証: インスタンス一致ではなく ID 一致で検証（より堅牢なテスト）
            coVerify(timeout = 2000) { 
                medicationRepository.deleteMedicationRecord(
                    match { it.id == existingRecord.id }, 
                    any(), 
                    any()
                ) 
            }
        }
    }
}
