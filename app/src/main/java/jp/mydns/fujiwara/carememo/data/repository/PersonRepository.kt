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
 * 【設計指針：レイヤー責務】
 * 1. データアクセス専念：DB 操作に特化し、特定の業務判断（保存の可否など）は Logic レイヤーへ委譲します。
 * 2. 依存方向の遵守：下位レイヤーとして、上位の Logic や ViewModel に依存しない構成を維持します。
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

    /*
     * 利用者一覧画面におけるバッジ表示（入力済み確認）のデータ取得元を、
     * PersonRepository から専用の PersonSummaryRepository へ完全に移行したため、
     * 過去のコードとの互換性やリファクタリング時の参照用に保持。
    fun getAllPersonCategorySummaries(): Flow<List<PersonSummaryQueryResult>> = 
        personDao.getPersonCategorySummaries()
    */
}
