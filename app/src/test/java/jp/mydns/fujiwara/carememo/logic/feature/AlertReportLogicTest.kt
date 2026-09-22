package jp.mydns.fujiwara.carememo.logic.feature

import io.mockk.coEvery
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.HealthRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Test：AlertReportLogicTest
 */
class AlertReportLogicTest {

    private val personRepository = mockk<PersonRepository>()
    private val healthRepository = mockk<HealthRepository>()
    private val logic = AlertReportLogic(personRepository, healthRepository)

    @Test
    fun SCAN_01_scanAlerts_extractsAbnormalVitals() = runBlocking {
        // --- Mock Setup ---
        val personId = "user1"
        val person = Person(
            id = personId, 
            lastName = "山田", 
            firstName = "太郎", 
            lastNameFurigana = "ヤマダ", 
            firstNameFurigana = "タロウ", 
            birthday = Instant.now()
        )
        coEvery { personRepository.getAllPersons() } returns flowOf(listOf(person))

        // 異常な血圧 (150/95) -> ALERT
        val now = Instant.now()
        val bp = BpAndPulse(
            personId = personId,
            bpSystolic = 150,
            bpDiastolic = 95,
            recordTime = now
        )
        coEvery { healthRepository.getBpAndPulseByPersonId(personId) } returns flowOf(listOf(bp))
        coEvery { healthRepository.getHeightAndWeightByPersonId(personId) } returns flowOf(emptyList())
        coEvery { healthRepository.getGlucoseAndHbA1cByPersonId(personId) } returns flowOf(emptyList())

        // --- Execute ---
        val alerts = logic.scanAlerts(sinceDays = 30, isNameMaskingEnabled = false)

        // --- Verify ---
        // 高血圧(上)と高血圧(下)の2つが抽出される（evaluateVitalItems の仕様）
        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.any { it.itemName == "バイタル" && it.alertLevel == HealthAlertLevel.ALERT })
        assertEquals("山田　太郎", alerts[0].personName)
    }

    @Test
    fun SCAN_02_scanAlerts_extractsWeightLossAlert() = runBlocking {
        // --- Mock Setup ---
        val personId = "user1"
        val person = Person(
            id = personId, 
            lastName = "山田", 
            firstName = "太郎", 
            lastNameFurigana = "ヤマダ", 
            firstNameFurigana = "タロウ", 
            birthday = Instant.now()
        )
        coEvery { personRepository.getAllPersons() } returns flowOf(listOf(person))

        val now = Instant.now()
        val prev = now.minus(1, ChronoUnit.DAYS)
        
        // 60.0kg -> 56.5kg (3.5kg減少) -> ALERT
        val hw1 = HeightAndWeight(id = "new", personId = personId, height = null, weight = 56.5, recordTime = now)
        val hw2 = HeightAndWeight(id = "old", personId = personId, height = null, weight = 60.0, recordTime = prev)
        
        coEvery { healthRepository.getHeightAndWeightByPersonId(personId) } returns flowOf(listOf(hw1, hw2))
        coEvery { healthRepository.getBpAndPulseByPersonId(personId) } returns flowOf(emptyList())
        coEvery { healthRepository.getGlucoseAndHbA1cByPersonId(personId) } returns flowOf(emptyList())

        // --- Execute ---
        val alerts = logic.scanAlerts(sinceDays = 30, isNameMaskingEnabled = false)

        // --- Verify ---
        val weightAlert = alerts.find { it.messageResId == R.string.alert_report_weight_loss }
        assertTrue("体重減少アラートが抽出されること", weightAlert != null)
        assertEquals(HealthAlertLevel.ALERT, weightAlert?.alertLevel)
        assertEquals("56.5 kg", weightAlert?.valueText)
    }
}
