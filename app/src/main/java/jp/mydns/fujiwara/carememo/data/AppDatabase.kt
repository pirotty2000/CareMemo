package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Person::class,
        HeightAndWeight::class,
        BpAndPulse::class,
        GlucoseAndHbA1c::class,
        ConditionAtVisit::class,
        ConditionPhoto::class,
        MedicationRecord::class
    ],
    version = 8,
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

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                // SQLCipherのロード
                System.loadLibrary("sqlcipher")

                val dbName = "care_memo_database"
                val dbFile = context.getDatabasePath(dbName)

                val keyManager = DatabaseKeyManager(context)
                val passphrase = keyManager.getOrCreatePassphrase()
                val factory = SupportOpenHelperFactory(passphrase)

                // 平文DBから暗号化DBへの切り替え時のクラッシュ対策
                // 既存のファイルが平文の場合、SQLCipherで開くと SQLiteException (code 26) が発生する。
                // 今回は「一旦空になってもよい」という方針のため、開けない場合は削除する。
                if (dbFile.exists()) {
                    try {
                        // 読み取り専用で開いてみて、パスワードが通るかチェック
                        // SQLCipher 4.x ではパスワードに ByteArray を受け取る。
                        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            passphrase,
                            null,
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        )
                        db.close()
                    } catch (e: Exception) {
                        // パスワードが一致しない、または平文DBの場合はここに来る
                        // ファイルを削除して、Roomに再作成させる
                        context.deleteDatabase(dbName)
                    }
                }

                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    dbName
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
