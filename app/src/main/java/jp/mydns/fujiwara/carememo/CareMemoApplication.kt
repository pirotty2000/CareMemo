package jp.mydns.fujiwara.carememo

import android.app.Application
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Application：CareMemoApplication
 *
 * 【役割】
 * CareMemo アプリ全体のライフサイクル管理および依存関係の集約を行う Application クラスです。
 * データベースの初期化、操作ログのローテーション、および各画面で使用するリポジトリのシングルトン管理を担当します。
 *
 * 【主要な機能】
 * ・暗号化データベース（SQLCipher）の早期初期化と接続の確立。
 * ・設定に基づいた操作ログ（監査ログ）の自動ローテーション実行。
 * ・アプリ内の全リポジトリインスタンスの保持と提供（サービスロケーター的な役割）。
 *
 * 【設計指針】
 * 1. パフォーマンス：起動時にバックグラウンドで DB 接続を試行し、初回のデータアクセス時の遅延を軽減する。
 * 2. 単純性：DI フレームワークを導入せず、Application インスタンスを介したプロパティ参照による依存性の解決を行う。
 * 3. 疎結合：各リポジトリの生成時に必要な DAO や他のリポジトリをここで一元的に注入し、各レイヤの関心を分離する。
 */
class CareMemoApplication : Application() {
    
    /** 
     * アプリケーション全体の非同期処理を管理するコルーチンスコープ。
     * SupervisorJob を使用し、個別の処理の失敗が他の処理やアプリ全体に影響しないように構成します。
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 
     * データベースインスタンスの取得。
     * lazy により、必要になったタイミングで SQLCipher のセットアップを含む初期化が行われます。
     */
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        // データベースの早期初期化およびバックグラウンドメンテナンスの実行
        // applicationScope を使用して、構造化された並行性を実現します。
        applicationScope.launch(Dispatchers.IO) {
            try {
                // 1. DB ファイルを開き、SQLCipher の復号化・接続処理を先行して走らせる
                database.openHelper.writableDatabase

                // 2. 操作ログの自動メンテナンス（1日1回に制限）
                val lastDate = userSettingsRepository.lastAuditLogRotationDate.first()
                val today = LocalDate.now().toString()
                
                if (lastDate != today) {
                    val days = userSettingsRepository.auditLogRetentionDays.first()
                    auditLogRepository.deleteOldLogs(days)
                    // 実行完了日を記録
                    userSettingsRepository.setLastAuditLogRotationDate(today)
                }
            } catch (e: Exception) {
                // 起動時の致命的なエラーはスタックトレースを出力
                e.printStackTrace()
            }
        }
    }

    // --- 各種リポジトリのインスタンス定義 (lazy) ---

    /** ユーザー設定（マスキング、ロック、テーマ等）のリポジトリ */
    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    /** 利用者基本情報の参照・更新リポジトリ */
    val personRepository: PersonRepository by lazy {
        PersonRepository(database.personDao(), auditLogRepository)
    }

    /** アプリ内操作ログ（監査ログ）の記録と取得リポジトリ */
    val auditLogRepository: AuditLogRepository by lazy {
        AuditLogRepository(database.auditLogDao())
    }

    /** 利用者のアーカイブ（論理削除）および復元、抹消を管理するリポジトリ */
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

    /** 利用者一覧画面などで使用する、各カテゴリの記録有無サマリー取得リポジトリ */
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

    /** システムメンテナンス（エクスポート、インポート、整合性チェック）用リポジトリ */
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

    /** (A系統) 健康記録（身長体重、バイタル、血糖値）の管理リポジトリ */
    val healthRepository: HealthRepository by lazy {
        HealthRepository(
            database,
            database.heightAndWeightDao(),
            database.bpAndPulseDao(),
            database.glucoseAndHbA1cDao(),
            auditLogRepository
        )
    }

    /** (B系統) 経過記録（所見メモ）および添付写真の管理リポジトリ */
    val conditionRepository: ConditionRepository by lazy {
        ConditionRepository(
            database.conditionAtVisitDao(),
            database.conditionPhotoDao(),
            auditLogRepository
        )
    }

    /** (C系統) 服薬状況の記録とカレンダー管理リポジトリ */
    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(
            database.medicationRecordDao(),
            auditLogRepository
        )
    }

    /** (D系統) 緊急連絡先情報の管理リポジトリ */
    val emergencyContactRepository: EmergencyContactRepository by lazy {
        EmergencyContactRepository(
            database.emergencyContactDao(),
            auditLogRepository
        )
    }
}
