package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CareMemoApplication : Application() {
    // データベースのインスタンス取得
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        // データベースの早期初期化（バックグラウンドで接続を確立しておく）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // queryを発行することで物理的にDBファイルを開き、SQLCipherの復号化処理を走らせる
                database.openHelper.writableDatabase
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 設定リポジトリのインスタンス取得
    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    // (共通) 利用者情報リポジトリ
    val personRepository: PersonRepository by lazy {
        PersonRepository(database.personDao())
    }

    // (管理) 利用終了者管理リポジトリ
    val archivedPersonRepository: ArchivedPersonRepository by lazy {
        ArchivedPersonRepository(
            database,
            database.personDao(),
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            database.conditionAtVisitDao(),
            database.conditionPhotoDao(),
            database.medicationRecordDao()
        )
    }

    // (集計) 記録有無サマリーリポジトリ
    val personSummaryRepository: PersonSummaryRepository by lazy {
        PersonSummaryRepository(
            database.personDao(),
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            database.conditionAtVisitDao(),
            database.medicationRecordDao()
        )
    }

    // (保守) システムメンテナンスリポジトリ
    val appMaintenanceRepository: AppMaintenanceRepository by lazy {
        AppMaintenanceRepository(
            database,
            database.personDao(),
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            database.conditionAtVisitDao(),
            database.conditionPhotoDao(),
            database.medicationRecordDao()
        )
    }

    // (A系統) 健康記録リポジトリ
    val healthRepository: HealthRepository by lazy {
        HealthRepository(
            database,
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao()
        )
    }

    // (B系統) 所見メモ・写真リポジトリ
    val conditionRepository: ConditionRepository by lazy {
        ConditionRepository(
            database.conditionAtVisitDao(),
            database.conditionPhotoDao()
        )
    }

    // (C系統) 服薬管理リポジトリ
    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(
            database.medicationRecordDao()
        )
    }
}
