package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * 利用者情報の管理を担当するリポジトリ
 */
class PersonRepository(
    private val personDao: PersonDao
) {
    fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
    
    fun getPersonById(id: Int): Flow<Person?> = personDao.getPersonById(id)
    
    suspend fun insertPerson(person: Person) = personDao.insert(person)
    
    suspend fun updatePerson(person: Person) = personDao.update(person)

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
