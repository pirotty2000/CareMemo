package jp.mydns.fujiwara.carememo.data

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Data：BackupDto
 *
 * 【役割】
 * バックアップ・復元処理において、外部（JSON形式のファイル）とやり取りするための専用データ転送オブジェクト (DTO) 群を提供します。
 *
 * 【導入の背景】
 * データベースの内部構造（Room Entity）は、アプリの機能追加やリファクタリング（例：IDのUUID化、カラム名の変更等）
 * に伴い頻繁に変化する可能性があります。Entity を直接バックアップに使用すると、古いバージョンのバックアップが
 * 新しいアプリで復元できなくなる恐れがあるため、不変の「交換形式」として本 DTO を定義しています。
 *
 * 【主な機能】
 * ・Kotlin Serialization による JSON へのシリアライズ/デシリアライズ。
 * ・Entity と DTO の相互マッピング機能の提供。
 * ・論理削除フラグ (deletedAt) や同期管理用フラグ (isSynced) への対応。
 *
 * 【設計指針】
 * 1. 外部形式としての互換性を最優先し、Entity の構造が変わっても DTO の構造は極力維持する。
 * 2. ID は一貫して String (UUID) 形式を採用し、将来の分散同期等に備える。
 * 3. Instant 型のシリアライズには、プロジェクト共通の [InstantSerializer] を使用する。
 */

/**
 * 利用者情報のバックアップ用 DTO
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

/**
 * 身長・体重記録のバックアップ用 DTO
 */
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

/**
 * 血圧・脈拍・バイタル記録のバックアップ用 DTO
 */
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

/**
 * 血糖値・HbA1c記録のバックアップ用 DTO
 */
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

/**
 * 所見メモ記録のバックアップ用 DTO
 */
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

/**
 * 所見写真メタデータのバックアップ用 DTO
 */
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

/**
 * 服薬記録のバックアップ用 DTO
 */
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

// ------------------------------------------------------------------------------------------------
// DTO と Entity の相互マッピング用拡張関数
// ------------------------------------------------------------------------------------------------

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
