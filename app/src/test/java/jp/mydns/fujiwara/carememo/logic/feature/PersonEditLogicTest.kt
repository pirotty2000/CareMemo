package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Logic層テスト：PersonEditLogic
 */
class PersonEditLogicTest {

    private val sampleInitialPerson = Person(
        id = "persisted-uuid-10",
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

    // region 2. 変更検知テスト (isChanged)

    @Test
    fun CHG_01_isChanged_initialNew() {
        assertFalse(PersonEditLogic.isChanged(PersonEditUiState(), null))
    }

    @Test
    fun CHG_02_isChanged_withInputNew() {
        assertTrue(PersonEditLogic.isChanged(PersonEditUiState(lastName = "佐藤"), null))
        assertTrue(PersonEditLogic.isChanged(PersonEditUiState(year = "1"), null))
    }

    @Test
    fun CHG_03_isChanged_noChangeEdit() {
        assertFalse(PersonEditLogic.isChanged(sampleValidState, sampleInitialPerson))
    }

    @Test
    fun CHG_04_isChanged_nameChangedEdit() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(lastName = "田中"), sampleInitialPerson))
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(firstName = "花子"), sampleInitialPerson))
    }

    @Test
    fun CHG_05_isChanged_birthdayChangedEdit() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(era = BirthEra.HEISEI), sampleInitialPerson))
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(year = "26"), sampleInitialPerson))
    }

    @Test
    fun CHG_06_isChanged_noteChangedEdit() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(note = "新しいメモ"), sampleInitialPerson))
    }

    // endregion

    // region 3. バリデーションテスト (validate / isValid)

    @Test
    fun VAL_01_validate_success() {
        assertEquals(PersonEditValidationResult.SUCCESS, PersonEditLogic.validate(sampleValidState))
        assertTrue(PersonEditLogic.isValid(sampleValidState))
    }

    @Test
    fun VAL_02_validate_emptyLastName() {
        val state = sampleValidState.copy(lastName = " ")
        assertEquals(PersonEditValidationResult.EMPTY_LAST_NAME, PersonEditLogic.validate(state))
        assertFalse(PersonEditLogic.isValid(state))
    }

    @Test
    fun VAL_03_validate_emptyFirstName() {
        val state = sampleValidState.copy(firstName = "")
        assertEquals(PersonEditValidationResult.EMPTY_FIRST_NAME, PersonEditLogic.validate(state))
    }

    @Test
    fun VAL_04_validate_emptyLastNameFurigana() {
        val state = sampleValidState.copy(lastNameFurigana = "")
        assertEquals(PersonEditValidationResult.EMPTY_LAST_FURIGANA, PersonEditLogic.validate(state))
    }

    @Test
    fun VAL_05_validate_emptyFirstNameFurigana() {
        val state = sampleValidState.copy(firstNameFurigana = " ")
        assertEquals(PersonEditValidationResult.EMPTY_FIRST_FURIGANA, PersonEditLogic.validate(state))
    }

    @Test
    fun VAL_06_validate_invalidBirthday() {
        val state = sampleValidState.copy(month = "2", day = "30")
        assertEquals(PersonEditValidationResult.INVALID_BIRTHDAY, PersonEditLogic.validate(state))
    }

    @Test
    fun VAL_07_validate_nameTooLong() {
        val longName = "a".repeat(AppSpecifications.Constraints.Person.Validation.MAX_LENGTH_LAST_NAME + 1)
        val state = sampleValidState.copy(lastName = longName)
        assertEquals(PersonEditValidationResult.NAME_TOO_LONG, PersonEditLogic.validate(state))
    }

    @Test
    fun VAL_08_validate_noteTooLong() {
        val longNote = "n".repeat(AppSpecifications.Constraints.Person.Validation.MAX_LENGTH_NOTE + 1)
        val state = sampleValidState.copy(note = longNote)
        assertEquals(PersonEditValidationResult.NOTE_TOO_LONG, PersonEditLogic.validate(state))
    }

    // endregion

    // region 4. Entity 生成テスト (createPerson)

    @Test
    fun CRT_01_createPerson_mapping() {
        val entity = PersonEditLogic.createPerson(sampleValidState, null)
        assertEquals("山田", entity.lastName)
        assertEquals("太郎", entity.firstName)
        assertEquals("ヤマダ", entity.lastNameFurigana)
        assertEquals("タロウ", entity.firstNameFurigana)
        assertEquals("メモ", entity.note)
        assertEquals(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), entity.birthday)
    }

    @Test
    fun CRT_02_createPerson_trimming() {
        val state = sampleValidState.copy(
            lastName = " 田中 ",
            firstName = " 健二 ",
            note = " 備考 "
        )
        val entity = PersonEditLogic.createPerson(state, null)
        assertEquals("田中", entity.lastName)
        assertEquals("健二", entity.firstName)
        assertEquals("備考", entity.note)
    }

    @Test
    fun CRT_03_createPerson_maintainId() {
        val entity = PersonEditLogic.createPerson(sampleValidState, sampleInitialPerson)
        assertEquals(sampleInitialPerson.id, entity.id)
    }

    @Test
    fun CRT_04_createPerson_newId() {
        val entity = PersonEditLogic.createPerson(sampleValidState, null)
        assertFalse(IdLogic.isNew(entity.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun CRT_05_createPerson_invalidData() {
        val state = sampleValidState.copy(year = "abc")
        PersonEditLogic.createPerson(state, null)
    }

    // endregion
}
