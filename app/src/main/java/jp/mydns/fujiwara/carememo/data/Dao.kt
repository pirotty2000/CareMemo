package jp.mydns.fujiwara.carememo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data：Dao (Data Access Objects)
 *
 * 【役割】
 * CareMemo の SQLite データベース（Room）に対するすべての操作を定義します。
 * 各エンティティに対応する DAO を提供し、業務ロジックからのデータアクセスを抽象化します。
 *
 * 【主な機能】
 * ・標準的な CRUD（作成・取得・更新・削除）操作。
 * ・「削除フラグ（deleted_at）」に基づく論理削除および復元ロジック。
 * ・バックアップ・インポート用の一括処理機能。
 * ・複数テーブルにまたがるサマリー情報の集計クエリ。
 * ・データ整合性チェック（孤立レコードの検出）用の特殊クエリ。
 *
 * 【設計指針】
 * 1. データの消失を防ぐため、原則として「論理削除」を採用し、deleted_at カラムで管理する。
 * 2. 監視が必要なデータ（一覧画面等）には Flow を返し、リアクティブな UI 更新を可能にする。
 * 3. 複雑な結合や集計は SQLite のネイティブクエリ（@Query）を活用して効率化する。
 */

/**
 * 利用者の基本情報（Person）を管理する DAO
 */
@Dao
interface PersonDao {
    /** 有効な利用者（未削除）をふりがな順に取得 */
    @Query("SELECT * FROM person_db WHERE deleted_at IS NULL ORDER BY last_name_furigana ASC, first_name_furigana ASC")
    fun getAllPersons(): Flow<List<Person>>

    /** 利用終了した利用者（論理削除済み）を削除日時の新しい順に取得 */
    @Query("SELECT * FROM person_db WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getDeletedPersons(): Flow<List<Person>>

    /** IDを指定して利用者を取得 */
    @Query("SELECT * FROM person_db WHERE id = :id")
    fun getPersonById(id: String): Flow<Person?>

    /**
     * 重複登録防止用の検索。
     * 同姓同名、かつ同一生年月日、かつ同一備考（note）の人物を検索します。
     * note は NULL の可能性があるため COALESCE で空文字として比較します。
     */
    @Query("""
        SELECT * FROM person_db 
        WHERE last_name = :lastName 
        AND first_name = :firstName 
        AND birthday >= :start 
        AND birthday < :end
        AND COALESCE(note, '') = COALESCE(:note, '')
        LIMIT 1
    """)
    suspend fun findExistingPerson(
        lastName: String, 
        firstName: String, 
        start: java.time.Instant, 
        end: java.time.Instant, 
        note: String
    ): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    /** 指定した利用者を論理削除（削除時刻を記録） */
    @Query("UPDATE person_db SET deleted_at = :timestamp WHERE id = :id")
    suspend fun logicalDelete(id: String, timestamp: Long)

    /** 論理削除された利用者を一覧に復帰 */
    @Query("UPDATE person_db SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Delete
    suspend fun delete(person: Person)

    /** 指定した利用者を物理削除 */
    @Query("DELETE FROM person_db WHERE id = :id")
    suspend fun deletePersonPhysically(id: String)

    /** 全ての論理削除済み利用者を物理削除（アーカイブ抹消） */
    @Query("DELETE FROM person_db WHERE deleted_at IS NOT NULL")
    suspend fun deleteEndedPersons()

    // --- バックアップ・インポート用 ---
    
    @Query("SELECT * FROM person_db")
    suspend fun getAllRaw(): List<Person>

    @Query("DELETE FROM person_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<Person>)

    /**
     * 利用者一覧に表示する「記録状況バッジ」用の情報を一括取得します。
     * 各カテゴリに有効なデータが存在するかどうかを EXISTS 句で判定します。
     */
    @Query("""
        SELECT 
            p.id,
            EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasHeightWeight,
            EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasBpAndPulse,
            EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasGlucoseAndHbA1c,
            EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = p.id AND deleted_at IS NULL) AS hasCondition,
            EXISTS(SELECT 1 FROM medication_record_db WHERE person_id = p.id AND deleted_at IS NULL AND status IN (1, 2)) AS hasMedication
        FROM person_db p
        WHERE p.deleted_at IS NULL
    """)
    fun getPersonCategorySummaries(): Flow<List<PersonSummaryQueryResult>>
}

/**
 * 身長・体重記録を管理する DAO
 */
@Dao
interface HeightAndWeightDao {
    /** 特定の利用者の記録を日時の新しい順に取得 */
    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<HeightAndWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HeightAndWeight): Long

    @Query("UPDATE height_and_weight_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE height_and_weight_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: HeightAndWeight)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM height_and_weight_db")
    suspend fun getAllRaw(): List<HeightAndWeight>

    @Query("DELETE FROM height_and_weight_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeightAndWeight>)

    // --- 整合性チェック用 ---
    
    /** 親となる利用者が存在しない「孤立レコード」を取得 */
    @Query("""
        SELECT h.* FROM height_and_weight_db h
        LEFT JOIN person_db p ON h.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<HeightAndWeight>

    /** 特定の利用者に有効なデータが存在するか判定 */
    @Query("SELECT EXISTS(SELECT 1 FROM height_and_weight_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM height_and_weight_db WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 同一日時の既存データを検索（重複チェック用） */
    @Query("SELECT * FROM height_and_weight_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): HeightAndWeight?
}

/**
 * 血圧・脈拍等のバイタル記録を管理する DAO
 */
@Dao
interface BpAndPulseDao {
    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<BpAndPulse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BpAndPulse): Long

    @Query("UPDATE bp_and_pulse_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE bp_and_pulse_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: BpAndPulse)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM bp_and_pulse_db")
    suspend fun getAllRaw(): List<BpAndPulse>

    @Query("DELETE FROM bp_and_pulse_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BpAndPulse>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT b.* FROM bp_and_pulse_db b
        LEFT JOIN person_db p ON b.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<BpAndPulse>

    @Query("SELECT EXISTS(SELECT 1 FROM bp_and_pulse_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM bp_and_pulse_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM bp_and_pulse_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): BpAndPulse?
}

/**
 * 血糖値・HbA1c記録を管理する DAO
 */
@Dao
interface GlucoseAndHbA1cDao {
    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<GlucoseAndHbA1c>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GlucoseAndHbA1c): Long

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE glucose_and_hba1c_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: GlucoseAndHbA1c)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM glucose_and_hba1c_db")
    suspend fun getAllRaw(): List<GlucoseAndHbA1c>

    @Query("DELETE FROM glucose_and_hba1c_db")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GlucoseAndHbA1c>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT g.* FROM glucose_and_hba1c_db g
        LEFT JOIN person_db p ON g.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<GlucoseAndHbA1c>

    @Query("SELECT EXISTS(SELECT 1 FROM glucose_and_hba1c_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM glucose_and_hba1c_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM glucose_and_hba1c_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): GlucoseAndHbA1c?
}

/**
 * 所見メモ記録を管理する DAO
 */
@Dao
interface ConditionAtVisitDao {
    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL ORDER BY record_time DESC")
    fun getByPersonId(personId: String): Flow<List<ConditionAtVisit>>

    @Upsert
    suspend fun insert(item: ConditionAtVisit): Long

    @Query("UPDATE condition_at_visit_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE condition_at_visit_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: ConditionAtVisit)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_at_visit_db")
    suspend fun getAllRaw(): List<ConditionAtVisit>

    @Query("DELETE FROM condition_at_visit_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<ConditionAtVisit>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT c.* FROM condition_at_visit_db c
        LEFT JOIN person_db p ON c.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<ConditionAtVisit>

    /** タイトルまたは本文のキーワードによる利用者IDの検索（利用者一覧の絞り込みに使用） */
    @Query("""
        SELECT DISTINCT person_id FROM condition_at_visit_db 
        WHERE deleted_at IS NULL 
        AND (title LIKE '%' || :query || '%' OR condition LIKE '%' || :query || '%')
    """)
    fun getPersonIdsByConditionKeyword(query: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM condition_at_visit_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM condition_at_visit_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM condition_at_visit_db WHERE person_id = :personId AND record_time = :recordTime LIMIT 1")
    suspend fun findAtTime(personId: String, recordTime: java.time.Instant): ConditionAtVisit?

    @Query("SELECT id FROM condition_at_visit_db")
    suspend fun getAllIds(): List<String>
}

/**
 * 所見メモに紐付く写真メタデータを管理する DAO
 */
@Dao
interface ConditionPhotoDao {
    /** 特定の所見メモに紐付く写真を撮影順に取得 */
    @Query("SELECT * FROM condition_photo_db WHERE condition_id = :conditionId AND deleted_at IS NULL ORDER BY captured_at ASC")
    fun getByConditionId(conditionId: String): Flow<List<ConditionPhoto>>

    @Upsert
    suspend fun insert(item: ConditionPhoto): Long

    /** 一時保存中（親レコード確定前）の写真を正規の記録に紐付ける */
    @Query("UPDATE condition_photo_db SET condition_id = :newConditionId WHERE condition_id = '' AND person_id = :personId")
    suspend fun linkTemporaryPhotosToRecord(personId: String, newConditionId: String)

    @Query("DELETE FROM condition_photo_db WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE condition_photo_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE condition_photo_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId AND deleted_at IS NULL")
    fun getAllByPersonIdFlow(personId: String): Flow<List<ConditionPhoto>>

    @Query("SELECT * FROM condition_photo_db WHERE person_id = :personId")
    suspend fun getAllByPersonId(personId: String): List<ConditionPhoto>

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM condition_photo_db")
    suspend fun getAllRaw(): List<ConditionPhoto>

    @Query("DELETE FROM condition_photo_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<ConditionPhoto>)

    @Query("UPDATE condition_photo_db SET condition_id = :conditionId WHERE id = :photoId")
    suspend fun updateConditionId(photoId: String, conditionId: String)

    // --- 整合性チェック用 ---
    /** 紐付け先の所見メモが存在しない「孤立写真」を取得 */
    @Query("""
        SELECT cp.* FROM condition_photo_db cp
        LEFT JOIN condition_at_visit_db c ON cp.condition_id = c.id
        WHERE c.id IS NULL
    """)
    suspend fun getOrphanedPhotos(): List<ConditionPhoto>
}

/**
 * 服薬記録を管理する DAO
 */
@Dao
interface MedicationRecordDao {
    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND deleted_at IS NULL")
    fun getByPersonId(personId: String): Flow<List<MedicationRecord>>

    /** 月単位での服薬記録の取得（カレンダー表示用） */
    @Query("SELECT * FROM medication_record_db WHERE person_id = :personId AND dosage_date LIKE :month || '%' AND deleted_at IS NULL")
    fun getByMonth(personId: String, month: String): Flow<List<MedicationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MedicationRecord): Long

    @Query("UPDATE medication_record_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE medication_record_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: MedicationRecord)

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM medication_record_db")
    suspend fun getAllRaw(): List<MedicationRecord>

    @Query("DELETE FROM medication_record_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<MedicationRecord>)

    // --- 整合性チェック用 ---
    @Query("""
        SELECT m.* FROM medication_record_db m
        LEFT JOIN person_db p ON m.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<MedicationRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM medication_record_db WHERE person_id = :personId AND deleted_at IS NULL)")
    fun hasDataForPerson(personId: String): Flow<Boolean>

    @Query("DELETE FROM medication_record_db WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * 操作ログ（監査ログ）を管理する DAO
 */
@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLog)

    /** ログを日時の降順で全件取得 */
    @Query("SELECT * FROM audit_log_db ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    /** 指定日時より古いログを物理削除（ログの自動クリーンアップ用） */
    @Query("DELETE FROM audit_log_db WHERE timestamp < :threshold")
    suspend fun deleteOldLogs(threshold: java.time.Instant)

    @Query("DELETE FROM audit_log_db")
    suspend fun deleteAll()
}

/**
 * 緊急連絡先を管理する DAO
 */
@Dao
interface EmergencyContactDao {
    /**
     * 利用者に紐付く連絡先一覧を取得する。
     * ソート順: 種別優先度 (CASE文) ➔ 表示優先度 (priority) ➔ 施設名
     */
    @Query("""
        SELECT * FROM emergency_contact_db 
        WHERE person_id = :personId AND deleted_at IS NULL
        ORDER BY 
            CASE contact_type 
                WHEN 'DOCTOR' THEN 1 
                WHEN 'NURSING_STATION' THEN 2 
                WHEN 'SUPPORT_CENTER' THEN 3 
                WHEN 'CASE_WORKER' THEN 4 
                WHEN 'FAMILY' THEN 5 
                ELSE 6 
            END ASC, 
            priority ASC, 
            facility_name ASC
    """)
    fun getByPersonId(personId: String): Flow<List<EmergencyContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EmergencyContact): Long

    @Update
    suspend fun update(item: EmergencyContact)

    @Query("UPDATE emergency_contact_db SET deleted_at = :timestamp WHERE person_id = :personId")
    suspend fun logicalDeleteByPersonId(personId: String, timestamp: Long)

    @Query("UPDATE emergency_contact_db SET deleted_at = NULL WHERE person_id = :personId")
    suspend fun restoreByPersonId(personId: String)

    @Delete
    suspend fun delete(item: EmergencyContact)

    /** 連絡先の存在確認 */
    @Query("SELECT EXISTS(SELECT 1 FROM emergency_contact_db WHERE person_id = :personId)")
    suspend fun hasDataForPerson(personId: String): Boolean

    @Query("SELECT * FROM emergency_contact_db WHERE id = :id")
    suspend fun getById(id: String): EmergencyContact?

    // --- バックアップ・インポート用 ---
    @Query("SELECT * FROM emergency_contact_db")
    suspend fun getAllRaw(): List<EmergencyContact>

    @Query("DELETE FROM emergency_contact_db")
    suspend fun deleteAll()

    @Upsert
    suspend fun insertAll(items: List<EmergencyContact>)

    // --- 整合性チェック用 ---

    /** 親となる利用者が存在しない「孤立レコード」を取得 */
    @Query("""
        SELECT e.* FROM emergency_contact_db e
        LEFT JOIN person_db p ON e.person_id = p.id
        WHERE p.id IS NULL
    """)
    suspend fun getOrphanedRecords(): List<EmergencyContact>

    @Query("DELETE FROM emergency_contact_db WHERE id = :id")
    suspend fun deleteById(id: String)
}
