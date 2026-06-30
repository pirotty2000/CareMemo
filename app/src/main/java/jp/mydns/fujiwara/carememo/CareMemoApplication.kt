package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*

class CareMemoApplication : Application() {
    // データベースのインスタンス取得
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    // 設定リポジトリのインスタンス取得
    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    // (共通) 利用者情報リポジトリ
    val personRepository: PersonRepository by lazy {
        PersonRepository(
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
