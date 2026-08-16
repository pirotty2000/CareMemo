package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.Person
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit Test: PersonLogic
 */
class PersonLogicTest {

    @Test
    fun CLN_01_cleansePersonData_normalizesBirthday() {
        val birthday = Instant.parse("1950-01-01T15:30:00Z") // UTC 15:30
        val person = createSamplePerson(id = "p1", birthday = birthday)
        
        val result = PersonLogic.cleansePersonData(listOf(person)) { "suffix" }
        
        val normalized = result[0].birthday.atZone(ZoneOffset.UTC)
        assertEquals(1950, normalized.year)
        assertEquals(1, normalized.monthValue)
        assertEquals(1, normalized.dayOfMonth)
        assertEquals(0, normalized.hour)
        assertEquals(0, normalized.minute)
        assertEquals(0, normalized.second)
    }

    @Test
    fun CLN_02_cleansePersonData_addsSuffixOnDuplicate() {
        val birthday = Instant.parse("1950-01-01T00:00:00Z")
        // Same Name, Same Birthday, Same Note
        val person1 = createSamplePerson(id = "p1", lastName = "山田", firstName = "太郎", birthday = birthday, note = "メモ")
        val person2 = createSamplePerson(id = "p2", lastName = "山田", firstName = "太郎", birthday = birthday, note = "メモ")
        
        val result = PersonLogic.cleansePersonData(listOf(person1, person2)) { id -> " [ID:$id]" }
        
        assertEquals(2, result.size)
        assertEquals("メモ", result[0].note)
        assertTrue(result[1].note.startsWith("メモ [ID:"))
    }

    @Test
    fun CLN_03_cleansePersonData_handlesLongNote() {
        val birthday = Instant.parse("1950-01-01T00:00:00Z")
        val longNote = "a".repeat(250)
        val person1 = createSamplePerson(id = "p1", lastName = "A", firstName = "B", birthday = birthday, note = longNote)
        val person2 = createSamplePerson(id = "p2", lastName = "A", firstName = "B", birthday = birthday, note = longNote)
        
        val suffix = " [SUFFIX]" // 9 chars
        val result = PersonLogic.cleansePersonData(listOf(person1, person2)) { suffix }
        
        assertEquals(2, result.size)
        assertTrue("Note should be 255 chars max", result[1].note.length <= 255)
        assertTrue("Note should end with suffix", result[1].note.endsWith(suffix))
    }

    @Test
    fun CLN_04_cleansePersonData_keepsNoteWhenNoDuplicate() {
        // Different names
        val person1 = createSamplePerson(id = "p1", lastName = "山田", note = "メモ1")
        val person2 = createSamplePerson(id = "p2", lastName = "田中", note = "メモ1")
        
        val result = PersonLogic.cleansePersonData(listOf(person1, person2)) { "suffix" }
        
        assertEquals("メモ1", result[0].note)
        assertEquals("メモ1", result[1].note)
    }

    @Test
    fun CLN_05_cleansePersonData_addsSuffixToEmptyNote() {
        val birthday = Instant.parse("1950-01-01T00:00:00Z")
        // Same person info, empty notes
        val person1 = createSamplePerson(id = "p1", birthday = birthday, note = "")
        val person2 = createSamplePerson(id = "p2", birthday = birthday, note = "")
        
        val result = PersonLogic.cleansePersonData(listOf(person1, person2)) { id -> "[ID:$id]" }
        
        assertEquals("", result[0].note)
        assertTrue("Empty note should receive suffix", result[1].note.startsWith("[ID:"))
    }

    private fun createSamplePerson(
        id: String,
        lastName: String = "姓",
        firstName: String = "名",
        birthday: Instant = Instant.now(),
        note: String = ""
    ) = Person(
        id = id,
        lastName = lastName,
        firstName = firstName,
        lastNameFurigana = "せい",
        firstNameFurigana = "めい",
        birthday = birthday,
        note = note
    )
}
