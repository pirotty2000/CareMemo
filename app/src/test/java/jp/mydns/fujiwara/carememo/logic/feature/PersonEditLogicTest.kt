package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PersonEditLogicTest {

    private val sampleInitialPerson = Person(
        id = 10,
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        note = "メモ"
    )

    private val sampleValidState = PersonEditUiState(
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        note = "メモ",
        era = BirthEra.SHOWA,
        year = "25",
        month = "1",
        day = "1"
    )

    // --- 変更検知 (isChanged) ---

    @Test
    fun ch01_new_empty_no_change() {
        assertFalse(PersonEditLogic.isChanged(PersonEditUiState(), null))
    }

    @Test
    fun ch02_new_with_input_has_change() {
        assertTrue(PersonEditLogic.isChanged(PersonEditUiState(lastName = "佐藤"), null))
    }

    @Test
    fun ch03_existing_same_no_change() {
        assertFalse(PersonEditLogic.isChanged(sampleValidState, sampleInitialPerson))
    }

    @Test
    fun ch04_existing_lastName_changed_has_change() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(lastName = "田中"), sampleInitialPerson))
    }

    @Test
    fun ch05_existing_era_changed_has_change() {
        // 西暦1950年は昭和25年なので、平成に変えれば変更あり
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(era = BirthEra.HEISEI), sampleInitialPerson))
    }

    @Test
    fun ch06_existing_note_changed_has_change() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(note = "新しいメモ"), sampleInitialPerson))
    }

    // --- バリデーション (validate / isValid) ---

    @Test
    fun vl01_all_valid_success() {
        assertEquals(PersonEditValidationResult.SUCCESS, PersonEditLogic.validate(sampleValidState))
        assertTrue(PersonEditLogic.isValid(sampleValidState))
    }

    @Test
    fun vl02_empty_lastName_invalid() {
        assertEquals(PersonEditValidationResult.EMPTY_LAST_NAME, PersonEditLogic.validate(sampleValidState.copy(lastName = "")))
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(lastName = "")))
    }

    @Test
    fun vl03_empty_firstName_invalid() {
        assertEquals(PersonEditValidationResult.EMPTY_FIRST_NAME, PersonEditLogic.validate(sampleValidState.copy(firstName = "")))
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(firstName = "")))
    }

    @Test
    fun vl04_empty_year_invalid() {
        assertEquals(PersonEditValidationResult.INVALID_BIRTHDAY, PersonEditLogic.validate(sampleValidState.copy(year = "")))
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(year = "")))
    }

    @Test
    fun vl05_invalid_date_invalid() {
        assertEquals(PersonEditValidationResult.INVALID_BIRTHDAY, PersonEditLogic.validate(sampleValidState.copy(month = "2", day = "30")))
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(month = "2", day = "30")))
    }

    // --- Entity 生成 (createPerson) ---

    @Test
    fun cp01_create_new_entity() {
        val entity = PersonEditLogic.createPerson(sampleValidState, null)
        assertEquals(0, entity.id)
        assertEquals("山田", entity.lastName)
        assertEquals(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), entity.birthday)
    }

    @Test
    fun cp02_update_existing_entity() {
        val state = sampleValidState.copy(lastName = " 田中 ") // スペースあり
        val entity = PersonEditLogic.createPerson(state, sampleInitialPerson)
        assertEquals(10, entity.id)
        assertEquals("田中", entity.lastName) // trim されていること
    }

    @Test(expected = IllegalArgumentException::class)
    fun cp03_invalid_date_throws_exception() {
        val state = sampleValidState.copy(year = "99", era = BirthEra.SHOWA) // 昭和99年は存在しない
        PersonEditLogic.createPerson(state, null)
    }
}
