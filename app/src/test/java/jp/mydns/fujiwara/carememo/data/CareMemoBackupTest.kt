@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * バックアップデータのJSONシリアライズ・デシリアライズを検証するテスト。
 * データの「命綱」が壊れていないことを保証する。
 */
class CareMemoBackupTest {

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun InstantSerializer_日時の文字列化と復元が1ミリ秒の狂いもなく行われること() {
        val original = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val serialized = json.encodeToString(InstantSerializer, original)
        val deserialized = json.decodeFromString(InstantSerializer, serialized)
        
        assertEquals("シリアライズ前後で日時が一致すること", original, deserialized)
        // JSON形式がISO-8601であることを確認
        assertEquals("\"$original\"", serialized)
    }

    @Test
    fun CareMemoBackup_複雑なデータ構造が完全に復元されること() {
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

        // シリアライズ
        val jsonString = json.encodeToString(backup)
        
        // デシリアライズ
        val restored = json.decodeFromString<CareMemoBackup>(jsonString)

        // 検証
        assertEquals(backup.version, restored.version)
        assertEquals(backup.persons[0].lastName, restored.persons[0].lastName)
        assertEquals(backup.heightAndWeights[0].height, restored.heightAndWeights[0].height)
        assertEquals(backup.conditionAtVisits[0].author, restored.conditionAtVisits[0].author)
        assertEquals(backup.medicationRecords[0].dosageDate, restored.medicationRecords[0].dosageDate)
        
        // 全体一致の確認 (Data Class の equals)
        assertEquals(backup, restored)
    }
}
