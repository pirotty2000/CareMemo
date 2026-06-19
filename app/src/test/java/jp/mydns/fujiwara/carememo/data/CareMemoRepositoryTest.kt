package jp.mydns.fujiwara.carememo.data

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class CareMemoRepositoryTest {

    private val db = mockk<AppDatabase>(relaxed = true)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private val hwDao = mockk<HeightAndWeightDao>(relaxed = true)
    private val bpDao = mockk<BpAndPulseDao>(relaxed = true)
    private val glucoseDao = mockk<GlucoseAndHbA1cDao>(relaxed = true)
    private val conditionDao = mockk<ConditionAtVisitDao>(relaxed = true)

    private lateinit var repository: CareMemoRepository

    @Before
    fun setup() {
        repository = CareMemoRepository(
            db, personDao, hwDao, bpDao, glucoseDao, conditionDao
        )
    }

    @Test
    fun `logicalDeletePersonを実行したとき、全DAOの論理削除メソッドが呼ばれること`() = runTest {
        val personId = 100
        
        repository.logicalDeletePerson(personId)

        // 全てのDAOで論理削除が呼ばれたか検証
        coVerify { personDao.logicalDelete(eq(personId), any()) }
        coVerify { hwDao.logicalDeleteByPersonId(eq(personId), any()) }
        coVerify { bpDao.logicalDeleteByPersonId(eq(personId), any()) }
        coVerify { glucoseDao.logicalDeleteByPersonId(eq(personId), any()) }
        coVerify { conditionDao.logicalDeleteByPersonId(eq(personId), any()) }
    }
}
