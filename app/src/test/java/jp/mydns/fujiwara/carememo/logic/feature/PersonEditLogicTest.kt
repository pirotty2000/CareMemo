@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * LOG-M-002 PersonEditLogic のテスト
 * 
 * テスト仕様書: doc/test/logic/TEST_SPEC_LOG-M-002_PersonEditLogic.md に準拠
 */
class PersonEditLogicTest {

    private val sampleInitialPerson = Person(
        id = "10",
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

    // ======================================================================================
    // 1. 変更検知テスト (isChanged)
    // ======================================================================================

    @Test
    fun chg_01_変更なし_新規() {
        assertFalse(PersonEditLogic.isChanged(PersonEditUiState(), null))
    }

    @Test
    fun chg_02_入力あり_新規() {
        // いずれかのフィールドに入力があれば true
        assertTrue("姓", PersonEditLogic.isChanged(PersonEditUiState(lastName = "佐藤"), null))
        assertTrue("ふりがな", PersonEditLogic.isChanged(PersonEditUiState(lastNameFurigana = "サトウ"), null))
        assertTrue("メモ", PersonEditLogic.isChanged(PersonEditUiState(note = "あ"), null))
        assertTrue("年", PersonEditLogic.isChanged(PersonEditUiState(year = "1"), null))
    }

    @Test
    fun chg_03_変更なし_更新() {
        assertFalse(PersonEditLogic.isChanged(sampleValidState, sampleInitialPerson))
    }

    @Test
    fun chg_04_氏名の変更() {
        assertTrue("姓の変更", PersonEditLogic.isChanged(sampleValidState.copy(lastName = "田中"), sampleInitialPerson))
        assertTrue("名の変更", PersonEditLogic.isChanged(sampleValidState.copy(firstName = "花子"), sampleInitialPerson))
    }

    @Test
    fun chg_05_生年月日の変更() {
        assertTrue("元号の変更", PersonEditLogic.isChanged(sampleValidState.copy(era = BirthEra.HEISEI), sampleInitialPerson))
        assertTrue("年の変更", PersonEditLogic.isChanged(sampleValidState.copy(year = "26"), sampleInitialPerson))
        assertTrue("月の変更", PersonEditLogic.isChanged(sampleValidState.copy(month = "2"), sampleInitialPerson))
        assertTrue("日の変更", PersonEditLogic.isChanged(sampleValidState.copy(day = "2"), sampleInitialPerson))
    }

    @Test
    fun chg_06_メモの変更() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(note = "新しいメモ"), sampleInitialPerson))
    }

    // ======================================================================================
    // 2. バリデーションテスト (validate)
    // ======================================================================================

    @Test
    fun val_01_正常な入力() {
        assertEquals(PersonEditValidationResult.SUCCESS, PersonEditLogic.validate(sampleValidState))
        assertTrue(PersonEditLogic.isValid(sampleValidState))
    }

    @Test
    fun val_02_姓が空() {
        val state = sampleValidState.copy(lastName = " ")
        assertEquals(PersonEditValidationResult.EMPTY_LAST_NAME, PersonEditLogic.validate(state))
        assertFalse(PersonEditLogic.isValid(state))
    }

    @Test
    fun val_03_名が空() {
        val state = sampleValidState.copy(firstName = "")
        assertEquals(PersonEditValidationResult.EMPTY_FIRST_NAME, PersonEditLogic.validate(state))
        assertFalse(PersonEditLogic.isValid(state))
    }

    @Test
    fun val_04_ふりがな_姓_が空() {
        // 現在の Logic 実装を確認し、もしチェックしていない場合は仕様に合わせて Logic も修正する必要があるかもしれません。
        // 現状の Logic ではふりがなの空チェックが抜けている可能性があります。
        val state = sampleValidState.copy(lastNameFurigana = "")
        assertEquals(PersonEditValidationResult.EMPTY_LAST_FURIGANA, PersonEditLogic.validate(state))
    }

    @Test
    fun val_05_ふりがな_名_が空() {
        val state = sampleValidState.copy(firstNameFurigana = "")
        assertEquals(PersonEditValidationResult.EMPTY_FIRST_FURIGANA, PersonEditLogic.validate(state))
    }

    @Test
    fun val_06_生年月日が不正() {
        val state = sampleValidState.copy(month = "2", day = "30")
        assertEquals(PersonEditValidationResult.INVALID_BIRTHDAY, PersonEditLogic.validate(state))
        assertFalse(PersonEditLogic.isValid(state))
    }

    // ======================================================================================
    // 3. Entity 生成テスト (createPerson)
    // ======================================================================================

    @Test
    fun crt_01_プロパティの詰め替え() {
        val entity = PersonEditLogic.createPerson(sampleValidState, null)
        assertEquals("山田", entity.lastName)
        assertEquals("太郎", entity.firstName)
        assertEquals("ヤマダ", entity.lastNameFurigana)
        assertEquals("タロウ", entity.firstNameFurigana)
        assertEquals("メモ", entity.note)
        assertEquals(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), entity.birthday)
    }

    @Test
    fun crt_02_空白のトリミング() {
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
    fun crt_03_IDの維持() {
        val entity = PersonEditLogic.createPerson(sampleValidState, sampleInitialPerson)
        assertEquals("10", entity.id) // initialPerson の ID "10" が維持されること
    }

    @Test(expected = IllegalArgumentException::class)
    fun crt_04_不正データでの例外() {
        val state = sampleValidState.copy(year = "99", era = BirthEra.SHOWA)
        PersonEditLogic.createPerson(state, null)
    }
}
