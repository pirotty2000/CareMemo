package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * バックアップ・リストア、およびデータの整合性スキャンのテスト。
 * データの「引っ越し」が安全に行われることを保証する。
 */
@RunWith(AndroidJUnit4::class)
class AppMaintenanceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var maintenanceRepository: AppMaintenanceRepository
    private lateinit var personDao: PersonDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        personDao = db.personDao()
        
        maintenanceRepository = AppMaintenanceRepository(
            db,
            personDao,
            db.heightAndWeightDao(),
            db.bpAndPulseDao(),
            db.glucoseAndHbA1cDao(),
            db.conditionAtVisitDao(),
            db.conditionPhotoDao(),
            db.medicationRecordDao(),
            db.auditLogDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun バックアップと復元_全てのデータとリレーションが完全に維持されること() = runBlocking {
        // 1. テストデータの作成
        val person = Person(id = "1", lastName = "山田", firstName = "太郎", lastNameFurigana = "やまだ", firstNameFurigana = "たろう", birthday = Instant.now())
        personDao.insert(person)
        
        val hw = HeightAndWeight(personId = "1", height = 170.0, recordTime = Instant.now())
        db.heightAndWeightDao().insert(hw)

        // 2. バックアップ取得
        val backup = maintenanceRepository.getBackupData()
        assertEquals(1, backup.persons.size)
        assertEquals(1, backup.heightAndWeights.size)

        // 3. データを全消去
        maintenanceRepository.clearAllData()
        assertTrue("全消去後はデータが空であること", personDao.getAllPersons().first().isEmpty())

        // 4. リストア実行
        maintenanceRepository.replaceAllData(backup)

        // 5. データが元通りであることを確認
        val restoredPersons = personDao.getAllPersons().first()
        assertEquals(1, restoredPersons.size)
        assertEquals("山田", restoredPersons[0].lastName)
        
        // 注意: replaceAllData は内部で ID を振り直すため、ID "1" での取得はできない可能性がある。
        // ここでは復元された利用者の新しい ID を取得して検索する。
        val restoredPersonId = restoredPersons[0].id
        val restoredHw = db.heightAndWeightDao().getByPersonId(restoredPersonId).first()
        assertEquals(1, restoredHw.size)
        assertEquals(170.0, restoredHw[0].height!!, 0.0)
    }

    @Test
    fun 不整合スキャン_親のいないレコードを正しく検出して削除できること() = runBlocking {
        // 1. あえて不整合レコードを挿入
        maintenanceRepository.insertTestInconsistency()
        
        // 2. スキャン実行
        val inconsistencies = maintenanceRepository.scanInconsistencies()
        assertEquals("不整合が1件検出されること", 1, inconsistencies.size)
        assertEquals("bp_and_pulse_db", inconsistencies[0].tableName)

        // 3. クリーンアップ実行
        maintenanceRepository.cleanInconsistencies(inconsistencies)
        
        // 4. 再スキャンで0件になること
        val resultsAfter = maintenanceRepository.scanInconsistencies()
        assertTrue("クリーンアップ後は不整合が0件であること", resultsAfter.isEmpty())
    }
}
