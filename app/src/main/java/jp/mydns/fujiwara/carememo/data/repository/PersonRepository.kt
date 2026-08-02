package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * Repository：PersonRepository
 *
 * 【役割】
 * 利用者の基本情報（姓名、ふりがな、生年月日、備考）の管理を担当します。
 * データの永続化に加え、重複登録の防止や記録サマリーの集計機能を提供します。
 *
 * 【主な機能】
 * ・アクティブな全利用者の取得（ふりがな順）。
 * ・利用者の新規登録、更新、論理削除、復元操作。
 * ・同姓同名、同生年月日の存在確認（バックアップ復元時の重複防止）。
 * ・各利用者の全カテゴリ記録有無サマリー（バッジ用）の一括集計。
 * ・データ操作に応じた監査ログの記録。
 *
 * 【設計指針】
 * 1. 同一性の確保：重複チェックでは、時刻情報の微細な差を許容するために日付範囲での検索を行う。
 * 2. 透明性：利用者の氏名変更や削除といった重要操作は、監査ログに影響を受けた人物名を明記する。
 * 3. 同期対応：保存時には `updatedAt` の自動更新と `isSynced = false` の設定を行い、外部同期に備える。
 */
class PersonRepository(
    private val personDao: PersonDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    /**
     * 有効な全利用者のリストを Flow で取得します。
     * 内部で「ふりがな（姓 -> 名）」の昇順でソートされます。
     *
     * @return 利用者リストを通知する Flow
     */
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    /**
     * IDを指定して特定の利用者情報を Flow で取得します。
     *
     * @param id 利用者ID
     * @return 該当する利用者情報を通知する Flow
     */
    fun getPersonById(id: String): Flow<Person?> = personDao.getPersonById(id)
    
    /**
     * 新しい利用者を登録します。
     *
     * @param person 保存対象の Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun insertPerson(person: Person, featureName: String = "", operation: String = "") {
        val itemToSave = person.copy(updatedAt = java.time.Instant.now(), isSynced = false)
        personDao.insert(itemToSave)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "INSERT",
            affectedId = itemToSave.id,
            details = "Name: ${itemToSave.lastName} ${itemToSave.firstName}",
            resultType = "SUCCESS"
        )
    }
    
    /**
     * 既存の利用者情報を更新します。
     *
     * @param person 更新対象の Entity
     * @param featureName ログ出力用の機能名
     * @param operation ログ出力用の操作名
     */
    suspend fun updatePerson(person: Person, featureName: String = "", operation: String = "") {
        val itemToUpdate = person.copy(updatedAt = java.time.Instant.now(), isSynced = false)
        personDao.update(itemToUpdate)
        auditLogRepository?.log(
            featureName = featureName,
            operation = operation,
            tableName = "person_db",
            actionType = "UPDATE",
            affectedId = itemToUpdate.id,
            details = "Name: ${itemToUpdate.lastName} ${itemToUpdate.firstName}",
            resultType = "SUCCESS"
        )
    }

    /**
     * 同姓同名・同生年月日・同備考の利用者が既に存在するか（論理削除済みを含む）を確認します。
     * バックアップデータ等に不完全な時刻情報が含まれている場合を考慮し、指定日の 00:00 〜 23:59 の範囲で検索します。
     *
     * @param person 検索の基準となる情報
     * @return 該当する既存の Person、存在しなければ null
     */
    suspend fun findExistingPerson(person: Person): Person? {
        val zone = ZoneId.systemDefault()
        val localDate = person.birthday.atZone(zone).toLocalDate()
        val startOfDay = localDate.atStartOfDay(zone).toInstant()
        val endOfDay = localDate.plusDays(1).atStartOfDay(zone).toInstant()

        return personDao.findExistingPerson(
            lastName = person.lastName,
            firstName = person.firstName,
            start = startOfDay,
            end = endOfDay,
            note = person.note
        )
    }

    /**
     * 全利用者の各カテゴリ記録状況サマリーを取得します。
     * 一覧画面でのバッジ表示（入力済み確認）に使用します。
     *
     * @return サマリー結果のリストを通知する Flow
     */
    fun getAllPersonCategorySummaries(): Flow<List<PersonSummaryQueryResult>> = 
        personDao.getPersonCategorySummaries()
}
