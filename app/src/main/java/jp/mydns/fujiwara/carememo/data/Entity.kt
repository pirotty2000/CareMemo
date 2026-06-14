package jp.mydns.fujiwara.carememo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "person_db")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "furigana") val furigana: String?,
    @ColumnInfo(name = "birthday") val birthday: Instant // java.time.Instant を使用
)

@Entity(tableName = "height_and_weight_db")
data class HeightAndWeight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "height") val height: Double?, // nullable (測定なしの場合)
    @ColumnInfo(name = "weight") val weight: Double,  // non-nullable (必須と仮定、または nullable に変更検討)
    @ColumnInfo(name = "record_time") val recordTime: Instant
)

@Entity(tableName = "bp_and_pulse_db")
data class BpAndPulse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "bp_systolic") val bpSystolic: Int?,
    @ColumnInfo(name = "bp_diastolic") val bpDiastolic: Int?,
    @ColumnInfo(name = "pulse") val pulse: Int?,
    @ColumnInfo(name = "record_time") val recordTime: Instant
)

@Entity(tableName = "glucose_and_hba1c_db")
data class GlucoseAndHbA1c(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "glucose") val glucose: Int?,
    @ColumnInfo(name = "hba1c") val hba1c: Double?,
    @ColumnInfo(name = "record_time") val recordTime: Instant
)

@Entity(tableName = "condition_at_visit_db")
data class ConditionAtVisit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "condition") val condition: String?,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "record_time") val recordTime: Instant
)
