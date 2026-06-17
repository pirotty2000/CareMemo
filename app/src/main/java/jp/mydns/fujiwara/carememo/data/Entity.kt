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
    indices = [Index(value = ["name", "birthday", "note"], unique = true)]
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "furigana") val furigana: String?,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "birthday") val birthday: Instant,
    @ColumnInfo(name = "note") val note: String = "", // 同姓同名識別用メモ
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)

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
