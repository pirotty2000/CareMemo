@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import jp.mydns.fujiwara.carememo.data.repository.DeleteOrRestorePersonRepository

/**
 * データベースの整合性、外部キー制約、一意制約など
 * 「目に見えないルール」を検証する統合テスト。
 */
@RunWith(AndroidJUnit4::class)
class CareMemoDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var hwDao: HeightAndWeightDao
    private lateinit var conditionDao: ConditionAtVisitDao
    private lateinit var medicationDao: MedicationRecordDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // インメモリDBを使用してテストごとにクリーンな状態にする
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        hwDao = db.heightAndWeightDao()
        conditionDao = db.conditionAtVisitDao()
        medicationDao = db.medicationRecordDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private fun createTestPerson() = Person(
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "やまだ",
        firstNameFurigana = "たろう",
        birthday = Instant.now().truncatedTo(ChronoUnit.DAYS)
    )

    @Test
    fun 外部キー制約_利用者を物理削除したら紐付く記録も自動で消えること() = runBlocking {
        // 1. 利用者作成
        val personId = personDao.insert(createTestPerson()).toInt()

        // 2. 紐付く記録を作成
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        conditionDao.insert(ConditionAtVisit(personId = personId, title = "テスト", condition = "内容", author = "記", recordTime = now))

        // 3. 物理削除
        personDao.deletePersonPhysically(personId)

        // 4. 記録が消えていることを確認
        assertTrue(hwDao.getByPersonId(personId).first().isEmpty())
        assertTrue(conditionDao.getByPersonId(personId).first().isEmpty())
    }

    @Test
    fun 一意制約_同じ利用者の同じ日時の記録は重複せず上書きされること() = runBlocking {
        val personId = personDao.insert(createTestPerson()).toInt()
        val sameTime = Instant.now().truncatedTo(ChronoUnit.MINUTES)

        // 1回目の挿入
        hwDao.insert(HeightAndWeight(personId = personId, height = 160.0, recordTime = sameTime))
        
        // 2回目の挿入（同じ日時で別の値）
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = sameTime))

        // 結果確認（1件のみ存在し、値が170.0であること）
        val records = hwDao.getByPersonId(personId).first()
        assertEquals(1, records.size)
        assertEquals(170.0, records[0].height!!, 0.0)
    }

    @Test
    fun 服薬記録の一意制約_同じ日同じ時間枠は重複せず上書きされること() = runBlocking {
        val personId = personDao.insert(createTestPerson()).toInt()
        val date = "2023-10-27"
        val slot = 0 // 朝

        // 1回目：未服用
        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = date, timeSlot = slot, status = 0, recordTime = Instant.now()))
        
        // 2回目：服用済みに更新
        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = date, timeSlot = slot, status = 2, recordTime = Instant.now()))

        val records = medicationDao.getByPersonId(personId).first()
        assertEquals(1, records.size)
        assertEquals(2, records[0].status)
    }

    @Test
    fun サマリー集計_論理削除された記録はカウントに含まれないこと() = runBlocking {
        val personId = personDao.insert(createTestPerson()).toInt()
        val now = Instant.now()

        // 1. 記録を挿入
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        
        // サマリー確認 (あり)
        var summary = personDao.getPersonCategorySummaries().first().find { it.id == personId }
        assertTrue(summary?.hasHeightWeight ?: false)

        // 2. 論理削除
        hwDao.logicalDeleteByPersonId(personId, System.currentTimeMillis())

        // サマリー確認 (なし)
        summary = personDao.getPersonCategorySummaries().first().find { it.id == personId }
        assertFalse(summary?.hasHeightWeight ?: true)
    }

    @Test
    fun 同一利用者判定_時刻が違っても同じ日付なら同一人物として検出できること() = runBlocking {
        // 1. あえて「12時34分」という時刻付きで利用者を登録 (過去の負の遺産をシミュレート)
        val baseDate = Instant.parse("1950-01-01T00:00:00Z")
        val personWithTime = createTestPerson().copy(
            lastName = "山田",
            firstName = "太郎",
            birthday = baseDate.plus(12, java.time.temporal.ChronoUnit.HOURS).plus(34, java.time.temporal.ChronoUnit.MINUTES)
        )
        personDao.insert(personWithTime)

        // 2. 検索時は「その日の開始時刻」から「翌日の開始時刻」までの範囲で検索する
        // これが PersonRepository.findExistingPerson で行われているロジック
        val startOfDay = baseDate
        val endOfDay = baseDate.plus(1, java.time.temporal.ChronoUnit.DAYS)

        val found = personDao.findExistingPerson(
            lastName = "山田",
            firstName = "太郎",
            start = startOfDay,
            end = endOfDay,
            note = ""
        )

        // 3. 時刻が違っていても、同じ日であれば見つけ出せること
        assertNotNull("時刻が異なっていても同一人物として検出されるべき", found)
        assertEquals("山田", found?.lastName)
    }

    @Test
    fun 写真の外部キー制約_所見メモを物理削除したら紐付く写真データも自動で消えること() = runBlocking {
        val personId = personDao.insert(createTestPerson()).toInt()
        val conditionId = conditionDao.insert(ConditionAtVisit(personId = personId, title = "T", condition = "C", author = "A", recordTime = Instant.now())).toInt()
        
        val photoDao = db.conditionPhotoDao()
        photoDao.insert(ConditionPhoto(conditionId = conditionId, personId = personId, photoFileName = "p.jpg", thumbnailFileName = "t.jpg", capturedAt = Instant.now()))

        // 所見メモを物理削除
        conditionDao.deleteById(conditionId)

        // 写真データが消えていることを確認
        assertTrue(photoDao.getByConditionId(conditionId).first().isEmpty())
    }

    @Test
    fun カスケード論理削除_利用者を論理削除したときに関連レコードも全て論理削除されること() = runBlocking {
        val personId = personDao.insert(createTestPerson()).toInt()
        val now = Instant.now()
        hwDao.insert(HeightAndWeight(personId = personId, height = 170.0, recordTime = now))
        medicationDao.insert(MedicationRecord(personId = personId, dosageDate = "2023-10-27", timeSlot = 0, status = 2, recordTime = now))

        val repo = DeleteOrRestorePersonRepository(
            db, personDao, hwDao, db.bpAndPulseDao(), db.glucoseAndHbA1cDao(),
            db.conditionAtVisitDao(), db.conditionPhotoDao(), medicationDao
        )

        // 論理削除実行
        repo.logicalDeletePerson(personId, "Test", "Delete")

        // 各テーブルで deleted_at がセットされていることを確認
        val person = personDao.getDeletedPersons().first().find { it.id == personId }
        assertNotNull("利用者が論理削除されていること", person?.deletedAt)

        val hw = hwDao.getAllRaw().find { it.personId == personId }
        assertNotNull("身長体重が論理削除されていること", hw?.deletedAt)

        val med = medicationDao.getAllRaw().find { it.personId == personId }
        assertNotNull("服薬記録が論理削除されていること", med?.deletedAt)
    }

    @Test
    fun 監査ログ_記録が正しく永続化され順序が新しい順であること() = runBlocking {
        val auditDao = db.auditLogDao()
        val now = Instant.now()
        
        auditDao.insert(AuditLog(screenName = "S1", operation = "O1", tableName = "T1", actionType = "INSERT", affectedId = "1", timestamp = now.minusSeconds(10)))
        auditDao.insert(AuditLog(screenName = "S2", operation = "O2", tableName = "T2", actionType = "UPDATE", affectedId = "2", timestamp = now))

        val logs = auditDao.getAllLogs().first()
        assertEquals(2, logs.size)
        // 新しい順（S2が先）であることを確認
        assertEquals("S2", logs[0].screenName)
        assertEquals("S1", logs[1].screenName)
    }
}
