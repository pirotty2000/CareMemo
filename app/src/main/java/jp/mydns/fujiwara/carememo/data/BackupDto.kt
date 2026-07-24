package jp.mydns.fujiwara.carememo.data

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * バックアップ・復元専用のデータ転送オブジェクト (DTO)
 * 将来的な Entity の構造変更（UUID化など）からバックアップ形式を保護するために導入。
 * バージョン 5 以降、ID は String (UUID) 形式となる。
 * 更新日時 (updatedAt) および同期フラグ (isSynced) に対応。
 */

@Serializable
data class PersonBackupDto(
    val id: String,
    val lastName: String,
    val firstName: String,
    val lastNameFurigana: String,
    val firstNameFurigana: String,
    @Serializable(with = InstantSerializer::class)
    val birthday: Instant,
    val note: String = "",
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class HeightAndWeightBackupDto(
    val id: String,
    val personId: String,
    val height: Double?,
    val weight: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val recordTime: Instant,
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class BpAndPulseBackupDto(
    val id: String,
    val personId: String,
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val sat: Int? = null,
    val pulse: Int? = null,
    val bodyTemperature: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val recordTime: Instant,
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class GlucoseAndHbA1cBackupDto(
    val id: String,
    val personId: String,
    val glucose: Int? = null,
    val hba1c: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val recordTime: Instant,
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class ConditionAtVisitBackupDto(
    val id: String,
    val personId: String,
    val title: String?,
    val condition: String?,
    val author: String,
    @Serializable(with = InstantSerializer::class)
    val recordTime: Instant,
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class ConditionPhotoBackupDto(
    val id: String,
    val conditionId: String,
    val personId: String,
    val photoFileName: String,
    val thumbnailFileName: String,
    @Serializable(with = InstantSerializer::class)
    val capturedAt: Instant,
    val caption: String = "",
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

@Serializable
data class MedicationRecordBackupDto(
    val id: String,
    val personId: String,
    val dosageDate: String,
    val timeSlot: Int,
    val status: Int,
    @Serializable(with = InstantSerializer::class)
    val recordTime: Instant,
    val deletedAt: Long? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val isSynced: Boolean = false
)

/**
 * DTO と Entity の相互マッピング用拡張関数
 */

fun Person.toBackupDto() = PersonBackupDto(
    id, lastName, firstName, lastNameFurigana, firstNameFurigana, birthday, note, deletedAt, updatedAt, isSynced
)

fun PersonBackupDto.toEntity() = Person(
    id, lastName, firstName, lastNameFurigana, firstNameFurigana, birthday, note, deletedAt, updatedAt, isSynced
)

fun HeightAndWeight.toBackupDto() = HeightAndWeightBackupDto(
    id, personId, height, weight, recordTime, deletedAt, updatedAt, isSynced
)

fun HeightAndWeightBackupDto.toEntity() = HeightAndWeight(
    id, personId, height, weight, recordTime, deletedAt, updatedAt, isSynced
)

fun BpAndPulse.toBackupDto() = BpAndPulseBackupDto(
    id, personId, bpSystolic, bpDiastolic, sat, pulse, bodyTemperature, recordTime, deletedAt, updatedAt, isSynced
)

fun BpAndPulseBackupDto.toEntity() = BpAndPulse(
    id, personId, bpSystolic, bpDiastolic, sat, pulse, bodyTemperature, recordTime, deletedAt, updatedAt, isSynced
)

fun GlucoseAndHbA1c.toBackupDto() = GlucoseAndHbA1cBackupDto(
    id, personId, glucose, hba1c, recordTime, deletedAt, updatedAt, isSynced
)

fun GlucoseAndHbA1cBackupDto.toEntity() = GlucoseAndHbA1c(
    id, personId, glucose, hba1c, recordTime, deletedAt, updatedAt, isSynced
)

fun ConditionAtVisit.toBackupDto() = ConditionAtVisitBackupDto(
    id, personId, title, condition, author, recordTime, deletedAt, updatedAt, isSynced
)

fun ConditionAtVisitBackupDto.toEntity() = ConditionAtVisit(
    id, personId, title, condition, author, recordTime, deletedAt, updatedAt, isSynced
)

fun ConditionPhoto.toBackupDto() = ConditionPhotoBackupDto(
    id, conditionId, personId, photoFileName, thumbnailFileName, capturedAt, caption, deletedAt, updatedAt, isSynced
)

fun ConditionPhotoBackupDto.toEntity() = ConditionPhoto(
    id, conditionId, personId, photoFileName, thumbnailFileName, capturedAt, caption, deletedAt, updatedAt, isSynced
)

fun MedicationRecord.toBackupDto() = MedicationRecordBackupDto(
    id, personId, dosageDate, timeSlot, status, recordTime, deletedAt, updatedAt, isSynced
)

fun MedicationRecordBackupDto.toEntity() = MedicationRecord(
    id, personId, dosageDate, timeSlot, status, recordTime, deletedAt, updatedAt, isSynced
)
