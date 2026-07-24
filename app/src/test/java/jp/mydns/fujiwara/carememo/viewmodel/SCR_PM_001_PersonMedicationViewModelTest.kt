@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.viewmodel

import android.util.Log
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.MedicationRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * PersonMedicationViewModel (服薬管理) のユニットテスト
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SCR_PM_001_PersonMedicationViewModelTest {

    private val medicationRepository = mockk<MedicationRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val summaryRepository = mockk<PersonSummaryRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    private lateinit var viewModel: PersonMedicationViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fixedInstant = Instant.parse("2023-10-27T10:00:00Z")

    private val testPerson = Person(
        id = "1",
        lastName = "服薬",
        firstName = "太郎",
        lastNameFurigana = "ふくやく",
        firstNameFurigana = "たろう",
        birthday = fixedInstant,
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
        every { userSettingsRepository.isNameMaskingEnabled } returns flowOf(value = false)
        every { personRepository.getPersonById(any()) } returns flowOf(testPerson)
        every { summaryRepository.getPersonCategorySummaryById(any()) } returns flowOf(PersonCategorySummary())
        
        viewModel = PersonMedicationViewModel(
            medicationRepository,
            personRepository,
            summaryRepository,
            userSettingsRepository,
            auditLogRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun nextMonth_updatesMonth() = runTest {
        viewModel.loadPerson("1")
        val initialMonth = viewModel.uiState.value.selectedMonth
        viewModel.nextMonth()
        
        assertEquals(initialMonth.plusMonths(1), viewModel.uiState.value.selectedMonth)
    }

    @Test
    fun recordsByDate_groupsByDate() = runTest {
        val date = "2023-10-27"
        val records = listOf(
            MedicationRecord(id = "1", personId = "1", dosageDate = date, timeSlot = 0, status = 1, recordTime = fixedInstant),
            MedicationRecord(id = "2", personId = "1", dosageDate = date, timeSlot = 1, status = 1, recordTime = fixedInstant)
        )
        // データを準備してからロード
        every { medicationRepository.getMedicationRecordsByMonth("1", any()) } returns flowOf(records)

        viewModel.uiState.test {
            viewModel.loadPerson("1")
            
            var found = false
            // awaitItem() を繰り返して、期待するデータが含まれる状態を待つ
            for (i in 1..10) {
                val state = awaitItem()
                if (state.recordsByDate.containsKey(date)) {
                    found = true
                    assertEquals(2, state.recordsByDate[date]?.size)
                    break
                }
            }
            assertTrue("日付ごとのレコードが生成されていること", found)
        }
    }

    @Test
    fun syncMedicationDay_insertsNewRecord() = runTest {
        viewModel.loadPerson("1")
        val date = "2023-10-27"
        val newRecord = MedicationRecord(id = "", personId = "1", dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)
        val slotRecords = listOf(newRecord, null, null, null)
        
        viewModel.syncMedicationDay(date, slotRecords)
        
        coVerify { medicationRepository.insertMedicationRecord(match { it.personId == "1" && it.dosageDate == date && it.status == 2 }, any(), any(), any()) }
    }

    @Test
    fun syncMedicationDay_deletesRemovedRecord() = runTest {
        val date = "2023-10-27"
        val existingRecord = MedicationRecord(id = "1", personId = "1", dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)
        
        every { medicationRepository.getMedicationRecordsByMonth("1", any()) } returns flowOf(listOf(existingRecord))
        
        viewModel.uiState.test {
            viewModel.loadPerson("1")

            var found = false
            for (i in 1..10) {
                val currentMap = awaitItem().recordsByDate
                if (currentMap.containsKey(date)) {
                    found = true
                    break
                }
            }
            assertTrue("テストデータが反映されていること", found)

            viewModel.syncMedicationDay(date, listOf(null, null, null, null))
            
            coVerify(timeout = 2000) { 
                medicationRepository.deleteMedicationRecord(match { it.id == "1" }, any(), any()) 
            }
        }
    }

    @Test
    fun lg01_loadRecords_failure_handlesException() = runTest {
        every { medicationRepository.getMedicationRecordsByMonth("1", any()) } returns flow {
            throw RuntimeException("Flow Error")
        }
        
        viewModel.loadPerson("1")

        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonMedication",
                operation = "monthlyRecordsFlow",
                tableName = "medication_db",
                actionType = "ERROR",
                affectedId = any(),
                details = match { it.contains("Flow Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }

    @Test
    fun lg02_syncMedicationDay_failure_handlesException() = runTest {
        viewModel.loadPerson("1")
        coEvery { medicationRepository.insertMedicationRecord(any(), any(), any(), any()) } throws RuntimeException("Sync Error")
        
        val date = "2023-10-27"
        val newRecord = MedicationRecord(id = "", personId = "1", dosageDate = date, timeSlot = 0, status = 2, recordTime = fixedInstant)
        
        viewModel.syncMedicationDay(date, listOf(newRecord, null, null, null))

        assertEquals(false, viewModel.uiState.value.isLoading)
        coVerify {
            auditLogRepository.log(
                featureName = "PersonMedication",
                operation = "syncMedicationDay",
                tableName = "medication_db",
                actionType = "ERROR",
                affectedId = "1",
                details = match { it.contains("Sync Error") },
                resultType = "OTHER_ERROR"
            )
        }
    }
}
