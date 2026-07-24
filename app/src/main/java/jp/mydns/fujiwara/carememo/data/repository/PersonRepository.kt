package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * 利用者情報の管理を担当するリポジトリ
 */
class PersonRepository(
    private val personDao: PersonDao,
    private val auditLogRepository: AuditLogRepository? = null
) {
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getPersonById(id: String): Flow<Person?> = personDao.getPersonById(id)
    
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
     * バックアップデータ等に時刻情報が含まれている場合を考慮し、日付の範囲で検索します。
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
}
