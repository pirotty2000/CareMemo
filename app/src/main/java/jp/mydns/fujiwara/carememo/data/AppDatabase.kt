package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Person::class,
        HeightAndWeight::class,
        BpAndPulse::class,
        GlucoseAndHbA1c::class,
        ConditionAtVisit::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun heightAndWeightDao(): HeightAndWeightDao
    abstract fun bpAndPulseDao(): BpAndPulseDao
    abstract fun glucoseAndHbA1cDao(): GlucoseAndHbA1cDao
    abstract fun conditionAtVisitDao(): ConditionAtVisitDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "care_memo_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { Instance = it }
            }
        }
    }
}
