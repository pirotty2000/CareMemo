package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

                // 操作ログの自動ローテーション
                val days = userSettingsRepository.auditLogRetentionDays.first()
                auditLogRepository.deleteOldLogs(days)
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
        PersonRepository(database.personDao(), auditLogRepository)
    }

    // (共通) 監査ログリポジトリ
    val auditLogRepository: AuditLogRepository by lazy {
        AuditLogRepository(database.auditLogDao())
    }

    // (管理) 利用者復帰・抹消管理リポジトリ
    val deleteOrRestorePersonRepository: DeleteOrRestorePersonRepository by lazy {
        DeleteOrRestorePersonRepository(
            database,
            database.personDao(),
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            database.conditionAtVisitDao(),
            database.conditionPhotoDao(),
            database.medicationRecordDao(),
            auditLogRepository
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
            database.medicationRecordDao(),
            database.auditLogDao()
        )
    }

    // (A系統) 健康記録リポジトリ
    val healthRepository: HealthRepository by lazy {
        HealthRepository(
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            auditLogRepository
        )
    }

    // (B系統) 所見メモ・写真リポジトリ
    val conditionRepository: ConditionRepository by lazy {
        ConditionRepository(
            database.conditionAtVisitDao(),
            database.conditionPhotoDao(),
            auditLogRepository
        )
    }

    // (C系統) 服薬管理リポジトリ
    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(
            database.medicationRecordDao(),
            auditLogRepository
        )
    }

    // (D系統) 緊急連絡先リポジトリ
    val emergencyContactRepository: EmergencyContactRepository by lazy {
        EmergencyContactRepository(
            database.emergencyContactDao(),
            auditLogRepository
        )
    }
}
