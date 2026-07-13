@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PersonListLogicTest {

    @Test
    fun `PL_SC_01_あべはあ行`() {
        assertEquals("あ", PersonListLogic.getSection("あべ"))
    }

    @Test
    fun `PL_SC_02_濁音か行の判定`() {
        assertEquals("か", PersonListLogic.getSection("がもう"))
    }

    @Test
    fun `PL_SC_04_促音開始の判定`() {
        assertEquals("た", PersonListLogic.getSection("っだ"))
    }

    @Test
    fun `PL_FL_01_全件表示`() {
        val persons = listOf(
            createMockPerson(1, "あべ"),
            createMockPerson(2, "さとう")
        )
        val result = PersonListLogic.filterPersons(persons, "全", null)
        assertEquals(2, result.size)
    }

    @Test
    fun `PL_FL_02_さ行のみ抽出`() {
        val persons = listOf(
            createMockPerson(1, "あべ"),
            createMockPerson(2, "さとう")
        )
        val result = PersonListLogic.filterPersons(persons, "さ", null)
        assertEquals(1, result.size)
        assertEquals(2, result[0].id)
    }

    private fun createMockPerson(id: Int, lastNameFurigana: String): Person {
        return Person(
            id = id,
            lastName = "",
            firstName = "",
            lastNameFurigana = lastNameFurigana,
            firstNameFurigana = "",
            birthday = Instant.now()
        )
    }
}
