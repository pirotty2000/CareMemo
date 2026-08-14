package jp.mydns.fujiwara.carememo.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit Test: Backup/Restore Integrity (UT-03)
 * 
 * Ensures that data serialization to JSON maintains perfect precision (down to the millisecond)
 * and that complex data structures are restored exactly as they were.
 */
class CareMemoBackupTest {

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun UT_03_01_instantSerializer_precision() {
        val original = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val serialized = json.encodeToString(InstantSerializer, original)
        val deserialized = json.decodeFromString(InstantSerializer, serialized)
        
        assertEquals("Date-time should match exactly before and after serialization", original, deserialized)
        // Verify ISO-8601 format
        assertEquals("\"$original\"", serialized)
    }

    @Test
    fun UT_03_02_backupRestore_fullDataStructure() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        
        val backup = CareMemoBackup(
            version = 5,
            appVersionCode = 100,
            persons = listOf(
                PersonBackupDto(id = "1", lastName = "山田", firstName = "太郎", lastNameFurigana = "やまだ", firstNameFurigana = "たろう", birthday = now, note = "備考")
            ),
            heightAndWeights = listOf(
                HeightAndWeightBackupDto(id = "1", personId = "1", height = 170.5, weight = 60.0, recordTime = now)
            ),
            bpAndPulses = listOf(
                BpAndPulseBackupDto(id = "1", personId = "1", bpSystolic = 120, bpDiastolic = 80, recordTime = now)
            ),
            glucoseAndHbA1cs = emptyList(),
            conditionAtVisits = listOf(
                ConditionAtVisitBackupDto(id = "1", personId = "1", title = "タイトル", condition = "内容", author = "記録者", recordTime = now)
            ),
            medicationRecords = listOf(
                MedicationRecordBackupDto(id = "1", personId = "1", dosageDate = "2023-10-27", timeSlot = 0, status = 2, recordTime = now)
            )
        )

        // Serialize
        val jsonString = json.encodeToString(backup)
        
        // Deserialize
        val restored = json.decodeFromString<CareMemoBackup>(jsonString)

        // Verify full equality
        assertEquals(backup, restored)
    }
}
