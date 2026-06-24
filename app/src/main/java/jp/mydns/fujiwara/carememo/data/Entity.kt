package jp.mydns.fujiwara.carememo.data

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

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "height") val height: Double?,
    @ColumnInfo(name = "weight") val weight: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "bp_systolic") val bpSystolic: Int? = null,
    @ColumnInfo(name = "bp_diastolic") val bpDiastolic: Int? = null,
    @ColumnInfo(name = "pulse") val pulse: Int? = null,
    @ColumnInfo(name = "body_temperature") val bodyTemperature: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "glucose") val glucose: Int? = null,
    @ColumnInfo(name = "hba1c") val hba1c: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "person_id") val personId: Int,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "condition") val condition: String?,
    @ColumnInfo(name = "author") val author: String,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

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

/**
 * アプリ全体のバックアップデータを保持するクラス
 */
@Serializable
data class CareMemoBackup(
    val version: Int = 2,
    val persons: List<Person>,
    val heightAndWeights: List<HeightAndWeight>,
    val bpAndPulses: List<BpAndPulse>,
    val glucoseAndHbA1cs: List<GlucoseAndHbA1c>,
    val conditionAtVisits: List<ConditionAtVisit>,
    val conditionPhotos: List<ConditionPhoto> = emptyList()
)

/**
 * 利用者ごとの記録有無サマリー (4つのカテゴリーに対応)
 * メイン画面のインジケーター（バッジ）点灯判定に使用
 */
data class PersonCategorySummary(
    val hasHeightWeight: Boolean = false,
    val hasBpAndPulse: Boolean = false,
    val hasGlucoseAndHbA1c: Boolean = false,
    val hasCondition: Boolean = false
)

// --- 計算・判定用拡張関数 ---

fun HeightAndWeight.calculateBMI(): Double {
    val h = height ?: 0.0
    val w = weight ?: 0.0
    val heightM = h / 100.0
    if (heightM <= 0.0) return 0.0
    return w / (heightM * heightM)
}

fun Double.evaluateBMI(): String {
    return when {
        this <= 0.0 -> "-"
        this < HealthThresholds.BMI_NORMAL_LOW -> HealthThresholds.BMI_LABEL_UNDERWEIGHT
        this < HealthThresholds.BMI_NORMAL_HIGH -> HealthThresholds.BMI_LABEL_NORMAL
        this < HealthThresholds.BMI_OBESITY_1 -> HealthThresholds.BMI_LABEL_OBESITY_1
        this < HealthThresholds.BMI_OBESITY_2 -> HealthThresholds.BMI_LABEL_OBESITY_2
        this < HealthThresholds.BMI_OBESITY_3 -> HealthThresholds.BMI_LABEL_OBESITY_3
        else -> HealthThresholds.BMI_LABEL_OBESITY_4
    }
}

fun BpAndPulse.checkStatus(): String {
    val status = getVitalStatus()
    val labels = mutableListOf<String>()
    
    if (status.isHighBp) labels.add(HealthThresholds.VITAL_LABEL_HIGH_BP)
    if (status.isLowBp) labels.add(HealthThresholds.VITAL_LABEL_LOW_BP)
    if (status.isTachycardia) labels.add(HealthThresholds.VITAL_LABEL_TACHYCARDIA)
    if (status.isBradycardia) labels.add(HealthThresholds.VITAL_LABEL_BRADYCARDIA)
    if (status.isFever) labels.add(HealthThresholds.VITAL_LABEL_FEVER)
    if (status.isHypothermia) labels.add(HealthThresholds.VITAL_LABEL_HYPOTHERMIA)
    
    return if (labels.isEmpty()) HealthThresholds.VITAL_LABEL_NORMAL else labels.joinToString("・")
}

/**
 * 各指標の判定結果を保持するデータクラス
 */
data class VitalStatus(
    val isHighBp: Boolean,
    val isLowBp: Boolean,
    val isTachycardia: Boolean,
    val isBradycardia: Boolean,
    val isFever: Boolean,
    val isHypothermia: Boolean
)

/**
 * 現在の値に基づいた判定フラグを返す
 */
fun BpAndPulse.getVitalStatus(): VitalStatus {
    val systolic = bpSystolic ?: 120
    val diastolic = bpDiastolic ?: 80
    val pulseVal = pulse ?: 70
    val tempVal = bodyTemperature ?: 36.5

    return VitalStatus(
        isHighBp = systolic >= HealthThresholds.BP_HIGH_SYSTOLIC || diastolic >= HealthThresholds.BP_HIGH_DIASTOLIC,
        isLowBp = systolic < HealthThresholds.BP_LOW_SYSTOLIC || diastolic < HealthThresholds.BP_LOW_DIASTOLIC,
        isTachycardia = pulseVal >= HealthThresholds.PULSE_HIGH,
        isBradycardia = pulseVal <= HealthThresholds.PULSE_LOW,
        isFever = tempVal >= HealthThresholds.TEMP_HIGH,
        isHypothermia = tempVal < HealthThresholds.TEMP_LOW
    )
}

fun GlucoseAndHbA1c.evaluateGlucose(): String? {
    val g = glucose ?: return null
    return when {
        g < HealthThresholds.GLUCOSE_NORMAL_LOW -> HealthThresholds.GLUCOSE_LABEL_LOW
        g <= HealthThresholds.GLUCOSE_NORMAL_HIGH -> HealthThresholds.GLUCOSE_LABEL_NORMAL
        else -> HealthThresholds.GLUCOSE_LABEL_HIGH
    }
}

fun GlucoseAndHbA1c.evaluateHbA1c(): String? {
    val h = hba1c ?: return null
    return when {
        h >= HealthThresholds.HBA1C_DIABETES -> HealthThresholds.HBA1C_LABEL_DIABETES
        h >= HealthThresholds.HBA1C_PREDIABETES -> HealthThresholds.HBA1C_LABEL_PREDIABETES
        h > HealthThresholds.HBA1C_GOOD -> HealthThresholds.HBA1C_LABEL_NORMAL_HIGH
        else -> HealthThresholds.HBA1C_LABEL_NORMAL
    }
}

fun GlucoseAndHbA1c.checkStatus(): String {
    val gStatus = evaluateGlucose()
    val hStatus = evaluateHbA1c()

    return when {
        gStatus != null && hStatus != null -> "$gStatus・$hStatus"
        gStatus != null -> gStatus
        hStatus != null -> hStatus
        else -> "---"
    }
}
