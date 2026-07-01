package jp.mydns.fujiwara.carememo.data.repository

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.coroutines.flow.Flow

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
}
