package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.mydns.fujiwara.carememo.utils.SystemEmergencyLogger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Data：AppDatabase
 *
 * 【役割】
 * CareMemo アプリのメインとなる Room データベース基盤です。
 * 全ての業務エンティティの管理、DAO の提供、および SQLCipher によるフルディスク暗号化を司ります。
 *
 * 【構成エンティティ】
 * ・利用者基本情報 (Person) / 緊急連絡先 (EmergencyContact)
 * ・健康記録 (HeightAndWeight, BpAndPulse, GlucoseAndHbA1c)
 * ・所見管理 (ConditionAtVisit, ConditionPhoto)
 * ・服薬管理 (MedicationRecord)
 * ・システムログ (AuditLog)
 *
 * 【設計指針】
 * 1. セキュリティ：SQLCipher を使用し、データベースファイルをバイナリレベルで暗号化する。
 * 2. データの健全性：[Converters] を用いて、Instant 等の非プリミティブ型を SQLite 互換形式に変換する。
 * 3. 保守性：古い開発版からの移行コードは廃止し、スキーマ不整合時は Destructive Migration により常に最新状態を維持する。
 */
@Database(
    entities = [
        Person::class,
        HeightAndWeight::class,
        BpAndPulse::class,
        GlucoseAndHbA1c::class,
        ConditionAtVisit::class,
        ConditionPhoto::class,
        MedicationRecord::class,
        AuditLog::class,
        EmergencyContact::class,
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun heightAndWeightDao(): HeightAndWeightDao
    abstract fun bpAndPulseDao(): BpAndPulseDao
    abstract fun glucoseAndHbA1cDao(): GlucoseAndHbA1cDao
    abstract fun conditionAtVisitDao(): ConditionAtVisitDao
    abstract fun conditionPhotoDao(): ConditionPhotoDao
    abstract fun medicationRecordDao(): MedicationRecordDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        /** スレッドセーフなシングルトンインスタンス */
        @Volatile
        private var Instance: AppDatabase? = null

        /** DBが再作成されたことを示すフラグ（起動セッション内のみ有効） */
        var wasRecreated: Boolean = false
            private set

        /**
         * データベースインスタンスを取得します。
         * 初回呼び出し時には SQLCipher のロードと暗号化キーの準備を行います。
         */
        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                // SQLCipher ネイティブライブラリのロード
                System.loadLibrary("sqlcipher")

                val dbName = "care_memo_database"
                val dbFile = context.getDatabasePath(dbName)

                // Android Keystore から暗号化キーを取得（なければ生成）
                val keyManager = DatabaseKeyManager(context)
                val passphrase = keyManager.getOrCreatePassphrase()

                val factory = SupportOpenHelperFactory(passphrase)

                // 【重要】平文DBから暗号化DBへの切り替え時のクラッシュ対策
                // 暗号化されていない既存DBファイルを SQLCipher で開こうとすると SQLiteException が発生する。
                // データの機密性を優先し、開けない（＝暗号化されていない、またはキー不一致）場合はファイルを削除して初期化する。
                if (dbFile.exists()) {
                    try {
                        // 読み取り専用でテストオープンしてパスワードの妥当性をチェック
                        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            passphrase,
                            null,
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        )
                        db.close()
                    } catch (e: Exception) {
                        // 1. 緊急ログへの記録（重大事象：パスワード不一致または平文DBからの移行失敗）
                        SystemEmergencyLogger.log(
                            context = context,
                            tag = "AppDatabase",
                            message = "CRITICAL: Database password mismatch or corrupted. Recreating database...",
                            throwable = e
                        )
                        // 2. パスワード不一致または平文DBの場合は、一度削除して Room に再作成させる
                        context.deleteDatabase(dbName)
                        wasRecreated = true
                    }
                }

                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    dbName
                )
                    .openHelperFactory(factory)
                    // スキーマの不整合（古い開発版等）が発生した場合は、テーブルを再作成する
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
