package jp.mydns.fujiwara.carememo.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import jp.mydns.fujiwara.carememo.logic.common.HealthLogic
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.UUID

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
 *   ├ [medication_record_db(pk:id, fk:person_id)] 利用者の服薬状況を格納
 *   └ [emergency_contact_db(pk:id, fk:person_id)] 利用者の緊急連絡先を格納
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
@Stable
interface HistoryRecord {
    val id: String
    val personId: String
    val recordTime: Instant
}

@Serializable
@Entity(
    tableName = "person_db",
    indices = [Index(value = ["last_name", "first_name", "birthday", "note"], unique = true)],
)
@Immutable
data class Person(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name_furigana") val lastNameFurigana: String,
    @ColumnInfo(name = "first_name_furigana") val firstNameFurigana: String,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "birthday") val birthday: Instant,
    @ColumnInfo(name = "note") val note: String = "", // 同姓同名識別用メモ
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null, // ヌルなら有効、値があれば削除日時（論理削除用）
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
) {
    /**
     * 伏せ字を適用した氏名を返す（漢字氏名用：交互にマスク）
     */
    fun getMaskedName(isEnabled: Boolean): String {
        return if (isEnabled) {
            "${lastName.maskAlternate()}\u3000${firstName.maskAlternate()}"
        } else {
            "$lastName\u3000$firstName"
        }
    }

    /**
     * 伏せ字を適用したふりがなを返す（カナ氏名用：2文字目以降すべてマスク）
     */
    fun getMaskedFurigana(isEnabled: Boolean): String {
        return if (isEnabled) {
            "${lastNameFurigana.maskStartOnly()}\u3000${firstNameFurigana.maskStartOnly()}"
        } else {
            "${lastNameFurigana}\u3000${firstNameFurigana}"
        }
    }
}

/**
 * 文字列に伏せ字ルールを適用する（漢字用）
 * 2文字以上の場合、偶数番目の文字を「○」で置き換える
 */
fun String.maskAlternate(): String {
    if (this.length < 2) return this
    return this.mapIndexed { index, char ->
        if (((index + 1) % 2) == 0) '○' else char
    }.joinToString("")
}

/**
 * 文字列に伏せ字ルールを適用する（カナ用）
 * 2文字目以降をすべて「○」で置き換える
 */
fun String.maskStartOnly(): String {
    if (this.isEmpty()) return this
    return this.take(1) + "○".repeat(this.length - 1)
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
@Immutable
data class HeightAndWeight(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") override val personId: String,
    @ColumnInfo(name = "height") val height: Double?,
    @ColumnInfo(name = "weight") val weight: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
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
@Immutable
data class BpAndPulse(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") override val personId: String,
    @ColumnInfo(name = "bp_systolic") val bpSystolic: Int? = null,
    @ColumnInfo(name = "bp_diastolic") val bpDiastolic: Int? = null,
    @ColumnInfo(name = "sat") val sat: Int? = null,
    @ColumnInfo(name = "pulse") val pulse: Int? = null,
    @ColumnInfo(name = "body_temperature") val bodyTemperature: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
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
@Immutable
data class GlucoseAndHbA1c(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") override val personId: String,
    @ColumnInfo(name = "glucose") val glucose: Int? = null,
    @ColumnInfo(name = "hba1c") val hba1c: Double? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
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
@Immutable
data class ConditionAtVisit(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") override val personId: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "condition") val condition: String?,
    @ColumnInfo(name = "author") val author: String,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "record_time") override val recordTime: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
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
@Immutable
data class ConditionPhoto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "condition_id") val conditionId: String,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "photo_file_name") val photoFileName: String,      // リサイズ済み画像
    @ColumnInfo(name = "thumbnail_file_name") val thumbnailFileName: String, // サムネイル画像
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "captured_at") val capturedAt: Instant,           // 撮影日時
    @ColumnInfo(name = "caption") val caption: String = "",              // キャプション
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
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
@Immutable
data class MedicationRecord(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") override val personId: String,

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

    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
) : HistoryRecord

@Serializable
@Entity(
    tableName = "emergency_contact_db",
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
        Index(value = ["person_id", "priority", "facility_name"])
    ]
)
@Immutable
data class EmergencyContact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "person_id") val personId: String,
    /** 種別 (DOCTOR, NURSING_STATION, FAMILY 等) */
    @ColumnInfo(name = "contact_type") val contactType: String,
    /** 病院名・事業所名・続柄 */
    @ColumnInfo(name = "facility_name") val facilityName: String,
    /** 担当者名・個人名 (任意) */
    @ColumnInfo(name = "person_name") val personName: String? = null,
    /** 電話番号 (任意) */
    @ColumnInfo(name = "phone_number") val phoneNumber: String? = null,
    /** 表示順序 (デフォルト 99) */
    @ColumnInfo(name = "priority") val priority: Int = 99,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)

/**
 * アプリ全体のバックアップデータを保持するクラス
 */
@Serializable
data class CareMemoBackup(
    val version: Int = 5,
    val appVersionCode: Int = 0, // エクスポート時のアプリバージョンコード
    val persons: List<PersonBackupDto>,
    val heightAndWeights: List<HeightAndWeightBackupDto>,
    val bpAndPulses: List<BpAndPulseBackupDto>,
    val glucoseAndHbA1cs: List<GlucoseAndHbA1cBackupDto>,
    val conditionAtVisits: List<ConditionAtVisitBackupDto>,
    val conditionPhotos: List<ConditionPhotoBackupDto> = emptyList(),
    val medicationRecords: List<MedicationRecordBackupDto> = emptyList(),
    val emergencyContacts: List<EmergencyContactBackupDto> = emptyList()
)

/**
 * 監査ログ (操作履歴)
 */
@Serializable
@Entity(tableName = "audit_log_db")
@Immutable
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * 操作日時
     */
    @Serializable(with = InstantSerializer::class)
    @ColumnInfo(name = "timestamp")
    val timestamp: Instant = Instant.now(),

    /**
     * 実行された機能・コンテキスト名 (例: "PersonCondition", "Detail/Health")
     */
    @ColumnInfo(name = "feature_name")
    val featureName: String,

    /**
     * 実行された操作・トリガー (例: "onSaveButtonClick", "swipeToDelete")
     */
    @ColumnInfo(name = "operation")
    val operation: String,

    /**
     * 操作対象のテーブル名 (例: "person_db")
     */
    @ColumnInfo(name = "table_name")
    val tableName: String,

    /**
     * 操作種別 ("INSERT", "UPDATE", "DELETE")
     */
    @ColumnInfo(name = "action_type")
    val actionType: String,

    /**
     * 操作されたレコードの主キー (追跡用)
     */
    @ColumnInfo(name = "affected_id")
    val affectedId: String,

    /**
     * 操作結果 ("SUCCESS", "DB_ERROR", "OTHER_ERROR", "UNKNOWN")
     */
    @ColumnInfo(name = "result_type", defaultValue = "UNKNOWN")
    val resultType: String = "UNKNOWN",

    /**
     * 補足情報 (エラーメッセージや非個人情報のメタデータ)
     */
    @ColumnInfo(name = "details")
    val details: String? = null
)

/**
 * 利用者ごとの記録有無サマリー
 * メイン画面のインジケーター（バッジ）点灯判定に使用
 */
@Immutable
data class PersonCategorySummary(
    val hasHeightWeight: Boolean = false,
    val hasBpAndPulse: Boolean = false,
    val hasGlucoseAndHbA1c: Boolean = false,
    val hasCondition: Boolean = false,
    val hasMedication: Boolean = false
)

/**
 * データベースクエリから直接サマリーを取得するための射影クラス
 */
data class PersonSummaryQueryResult(
    val id: String,
    val hasHeightWeight: Boolean,
    val hasBpAndPulse: Boolean,
    val hasGlucoseAndHbA1c: Boolean,
    val hasCondition: Boolean,
    val hasMedication: Boolean
)

/**
 * データベースの不整合（孤立したレコード）を表現するクラス
 */
data class DatabaseInconsistency(
    val tableName: String,
    val recordId: String,
    val personId: String?,
    val recordTime: Instant?,
    val description: String
)

// --- 計算・判定用拡張関数（HealthLogic を使用） ---

fun HeightAndWeight.calculateBMI(): Double {
    return HealthLogic.calculateBMI(height, weight)
}

fun HeightAndWeight.getBmiResult(context: Context): Pair<String, HealthAlertLevel> {
    val bmi = calculateBMI()
    val (status, alert) = HealthLogic.evaluateBMI(bmi)
    val label = status?.let { context.getString(HealthDisplayMapper.getBmiLabel(it)!!) } ?: "---"
    return label to alert
}

fun BpAndPulse.getVitalResults(context: Context): List<Pair<String, HealthAlertLevel>> =
    HealthLogic.evaluateVitalItems(bpSystolic, bpDiastolic, sat, pulse, bodyTemperature).map {
        context.getString(HealthDisplayMapper.getVitalLabel(it.first)) to it.second
    }

fun BpAndPulse.getWorstAlertLevel(): HealthAlertLevel =
    HealthAlertLevel.worst(
        HealthLogic.evaluateVitalItems(bpSystolic, bpDiastolic, sat, pulse, bodyTemperature).map { it.second }
    )

fun GlucoseAndHbA1c.getGlucoseResult(context: Context): Pair<String, HealthAlertLevel> {
    val (status, alert) = HealthLogic.evaluateGlucose(glucose)
    val label = status?.let { context.getString(HealthDisplayMapper.getGlucoseLabel(it)!!) } ?: "---"
    return label to alert
}

fun GlucoseAndHbA1c.getHbA1cResult(context: Context): Pair<String, HealthAlertLevel> {
    val (status, alert) = HealthLogic.evaluateHbA1c(hba1c)
    val label = status?.let { context.getString(HealthDisplayMapper.getHbA1cLabel(it)!!) } ?: "---"
    return label to alert
}

fun GlucoseAndHbA1c.getWorstAlertLevel(): HealthAlertLevel =
    HealthAlertLevel.worst(
        listOf(
            HealthLogic.evaluateGlucose(glucose).second,
            HealthLogic.evaluateHbA1c(hba1c).second
        )
    )

fun GlucoseAndHbA1c.getCombinedResultText(context: Context): String {
    val g = getGlucoseResult(context).first
    val h = getHbA1cResult(context).first
    return if (g != "---" && h != "---") "$g・$h" else if (g != "---") g else if (h != "---") h else "---"
}
