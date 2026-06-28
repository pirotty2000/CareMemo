package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**********************************************************************
 * <care_memo_database>
 **********************************************************************
 *
 * [person_db(pk:id)] 利用者の基本情報(氏名、生年月日)を格納
 *   ├ [height_and_weight_db(pk:id, fk:person_id)] 利用者の「身長・体重」情報を格納
 *   ├ [bp_and_pulse_db(pk:id, fk:person_id)] 利用者の「バイタル」情報を格納
 *   ├ [glucose_and_hba1c_db(pk:id, fk:person_id)] 利用者の「血糖値・HbA1c」を格納
 *   ├ [condition_at_visit_db(pk:id, fk:person_id)] 利用者の「所見メモ」を格納
 *   ├   └ [condition_photo_db(pk:id, fk:condition_id)] 所見メモに添付した写真のファイル名を格納
 *   └ [medication_record_db(pk:id, fk:person_id)] 利用者の服薬状況を格納
 *
 **********************************************************************/

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/**
 * すべての履歴データの基底インターフェース
 */
interface HistoryRecord {
    val id: Int
    val personId: Int
    val recordTime: Instant
}

@Serializable
@Entity(
    tableName = "person_db",
    indices = [Index(value = ["last_name", "first_name", "birthday", "note"], unique = true)]
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name_furigana") val lastNameFurigana: String,
    @ColumnInfo(name = "first_name_furigana") val firstNameFurigana: String,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "birthday") val birthday: Instant,
    @ColumnInfo(name = "note") val note: String = "", // 同姓同名識別用メモ
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null // ヌルなら有効、値があれば削除日時（論理削除用）
) {
    /**
     * 伏せ字を適用した氏名を返す
     */
    fun getMaskedName(isEnabled: Boolean): String {
        return if (isEnabled) {
            "${lastName.mask()}\u3000${firstName.mask()}"
        } else {
            "${lastName}\u3000${firstName}"
        }
    }

    /**
     * 伏せ字を適用したふりがなを返す
     */
    fun getMaskedFurigana(isEnabled: Boolean): String {
        return if (isEnabled) {
            "${lastNameFurigana.mask()}\u3000${firstNameFurigana.mask()}"
        } else {
            "${lastNameFurigana}\u3000${firstNameFurigana}"
        }
    }
}

/**
 * 文字列に伏せ字ルールを適用する拡張関数
 * 2文字以上の場合、偶数番目の文字を「○」で置き換える
 */
fun String.mask(): String {
    if (this.length < 2) return this
    return this.mapIndexed { index, char ->
        if ((index + 1) % 2 == 0) '○' else char
    }.joinToString("")
}

@Serializable
@Entity(
    tableName = "height_and_weight_db",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["person_id", "record_time"], unique = true) // 分単位の一意制約（保存時に丸め込み前提）
    ]
)
data class HeightAndWeight(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    @ColumnInfo(name = "person_id") override val personId: Int,
    @ColumnInfo(name = "height") val height: Double?,
    @ColumnInfo(name = "weight") val weight: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) : HistoryRecord

@Serializable
@Entity(
    tableName = "bp_and_pulse_db",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["person_id", "record_time"], unique = true)
    ]
)
data class BpAndPulse(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    @ColumnInfo(name = "person_id") override val personId: Int,
    @ColumnInfo(name = "bp_systolic") val bpSystolic: Int? = null,
    @ColumnInfo(name = "bp_diastolic") val bpDiastolic: Int? = null,
    @ColumnInfo(name = "pulse") val pulse: Int? = null,
    @ColumnInfo(name = "body_temperature") val bodyTemperature: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) : HistoryRecord

@Serializable
@Entity(
    tableName = "glucose_and_hba1c_db",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["person_id", "record_time"], unique = true)
    ]
)
data class GlucoseAndHbA1c(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    @ColumnInfo(name = "person_id") override val personId: Int,
    @ColumnInfo(name = "glucose") val glucose: Int? = null,
    @ColumnInfo(name = "hba1c") val hba1c: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) : HistoryRecord

@Serializable
@Entity(
    tableName = "condition_at_visit_db",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["person_id", "record_time"], unique = true)
    ]
)
data class ConditionAtVisit(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    @ColumnInfo(name = "person_id") override val personId: Int,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "condition") val condition: String?,
    @ColumnInfo(name = "author") val author: String,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) : HistoryRecord

@Serializable
@Entity(
    tableName = "condition_photo_db",
    foreignKeys = [
        ForeignKey(
            entity = ConditionAtVisit::class,
            parentColumns = ["id"],
            childColumns = ["condition_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["condition_id"]),
        Index(value = ["person_id"])
    ]
)
data class ConditionPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "condition_id") val conditionId: Int,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "photo_file_name") val photoFileName: String,      // リサイズ済み画像
    @ColumnInfo(name = "thumbnail_file_name") val thumbnailFileName: String, // サムネイル画像
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "captured_at") val capturedAt: Instant,           // 撮影日時
    @ColumnInfo(name = "caption") val caption: String = "",              // キャプション
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

@Serializable
@Entity(
    tableName = "medication_record_db",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        // 「ある利用者の、ある服用対象日の、ある時間枠」は1つだけに制限
        Index(value = ["person_id", "dosage_date", "time_slot"], unique = true)
    ]
)
data class MedicationRecord(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    @ColumnInfo(name = "person_id") override val personId: Int,

    /**
     * 服用対象日 (例: "2023-10-27")
     * カレンダーの「どのマス」に表示するかを決定する論理的な日付
     */
    @ColumnInfo(name = "dosage_date") val dosageDate: String,

    /**
     * 時間枠 (0:朝, 1:昼, 2:夕, 3:寝る前)
     */
    @ColumnInfo(name = "time_slot") val timeSlot: Int,

    /**
     * 服用ステータス
     * 0: 未服用 (グレー)
     * 1: 服薬介助 (薄い色)
     * 2: 服用 (濃い色)
     * ※ レコードが存在しない場合は「未確認 (白)」として扱う
     */
    @ColumnInfo(name = "status") val status: Int,

    /**
     * 実際に確認・記録した日時
     * 「朝の分を、結局何時に確認したのか」という事実情報
     */
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,

    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) : HistoryRecord

/**
 * アプリ全体のバックアップデータを保持するクラス
 */
@Serializable
data class CareMemoBackup(
    val version: Int = 3,
    val persons: List<Person>,
    val heightAndWeights: List<HeightAndWeight>,
    val bpAndPulses: List<BpAndPulse>,
    val glucoseAndHbA1cs: List<GlucoseAndHbA1c>,
    val conditionAtVisits: List<ConditionAtVisit>,
    val conditionPhotos: List<ConditionPhoto> = emptyList(),
    val medicationRecords: List<MedicationRecord> = emptyList()
)

/**
 * 利用者ごとの記録有無サマリー
 * メイン画面のインジケーター（バッジ）点灯判定に使用
 */
data class PersonCategorySummary(
    val hasHeightWeight: Boolean = false,
    val hasBpAndPulse: Boolean = false,
    val hasGlucoseAndHbA1c: Boolean = false,
    val hasCondition: Boolean = false,
    val hasMedication: Boolean = false
)

// --- 計算・判定用拡張関数（基軸となる HealthThresholds を使用） ---

fun HeightAndWeight.calculateBMI(): Double {
    val h = height ?: 0.0
    val w = weight ?: 0.0
    val heightM = h / 100.0
    if (heightM <= 0.0) return 0.0
    return w / (heightM * heightM)
}

fun HeightAndWeight.getBmiResult(context: Context): Pair<String, HealthThresholds.AlertLevel> {
    val (resId, alert) = HealthThresholds.evaluateBMI(calculateBMI())
    return (resId?.let { context.getString(it) } ?: "---") to alert
}

fun BpAndPulse.getVitalResults(context: Context): List<Pair<String, HealthThresholds.AlertLevel>> =
    HealthThresholds.evaluateVital(bpSystolic, bpDiastolic, pulse, bodyTemperature).map {
        context.getString(it.first) to it.second
    }

fun BpAndPulse.getWorstAlertLevel(): HealthThresholds.AlertLevel =
    HealthThresholds.evaluateVital(bpSystolic, bpDiastolic, pulse, bodyTemperature)
        .maxByOrNull { it.second.severity }?.second ?: HealthThresholds.AlertLevel.NORMAL

fun GlucoseAndHbA1c.getGlucoseResult(context: Context): Pair<String, HealthThresholds.AlertLevel> {
    val (resId, alert) = HealthThresholds.evaluateGlucose(glucose)
    return (resId?.let { context.getString(it) } ?: "---") to alert
}

fun GlucoseAndHbA1c.getHbA1cResult(context: Context): Pair<String, HealthThresholds.AlertLevel> {
    val (resId, alert) = HealthThresholds.evaluateHbA1c(hba1c)
    return (resId?.let { context.getString(it) } ?: "---") to alert
}

fun GlucoseAndHbA1c.getWorstAlertLevel(): HealthThresholds.AlertLevel =
    maxOfBySeverity(
        HealthThresholds.evaluateGlucose(glucose).second,
        HealthThresholds.evaluateHbA1c(hba1c).second
    )

fun GlucoseAndHbA1c.getCombinedResultText(context: Context): String {
    val g = getGlucoseResult(context).first
    val h = getHbA1cResult(context).first
    return if (g != "---" && h != "---") "$g・$h" else if (g != "---") g else h
}

private fun maxOfBySeverity(a: HealthThresholds.AlertLevel, b: HealthThresholds.AlertLevel): HealthThresholds.AlertLevel =
    if (a.severity >= b.severity) a else b
