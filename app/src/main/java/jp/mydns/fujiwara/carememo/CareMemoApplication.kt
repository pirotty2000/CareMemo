package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.AppDatabase
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository

class CareMemoApplication : Application() {
    // データベースのインスタンス取得
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    // 設定リポジトリのインスタンス取得
    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    // リポジトリのインスタンス取得
    val repository: CareMemoRepository by lazy { 
        CareMemoRepository(
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
}
