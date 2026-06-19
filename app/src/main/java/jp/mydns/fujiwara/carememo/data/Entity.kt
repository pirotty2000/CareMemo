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

/**
 * アプリ全体のバックアップデータを保持するクラス
 */
@Serializable
data class CareMemoBackup(
    val version: Int = 1,
    val persons: List<Person>,
    val heightAndWeights: List<HeightAndWeight>,
    val bpAndPulses: List<BpAndPulse>,
    val glucoseAndHbA1cs: List<GlucoseAndHbA1c>,
    val conditionAtVisits: List<ConditionAtVisit>
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
