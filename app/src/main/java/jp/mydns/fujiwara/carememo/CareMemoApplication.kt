package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.AppDatabase
import jp.mydns.fujiwara.carememo.data.CareMemoRepository

class CareMemoApplication : Application() {
    // データベースのインスタンス取得
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    // リポジトリのインスタンス取得
    val repository: CareMemoRepository by lazy { 
        CareMemoRepository(
            database.personDao(),
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            database.conditionAtVisitDao()
        ) 
    }
}
