@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `CH_01_新規時で空なら変更なし`() {
        assertFalse(PersonEditLogic.isChanged(PersonEditUiState(), null))
    }

    @Test
    fun `CH_02_新規時で入力ありなら変更あり`() {
        assertTrue(PersonEditLogic.isChanged(PersonEditUiState(lastName = "佐藤"), null))
    }

    @Test
    fun `CH_03_既存時で値が同じなら変更なし`() {
        assertFalse(PersonEditLogic.isChanged(sampleValidState, sampleInitialPerson))
    }

    @Test
    fun `CH_04_既存時で苗字が変われば変更あり`() {
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(lastName = "田中"), sampleInitialPerson))
    }

    @Test
    fun `CH_05_既存時で元号が変われば変更あり`() {
        // 西暦1950年は昭和25年なので、平成に変えれば変更あり
        assertTrue(PersonEditLogic.isChanged(sampleValidState.copy(era = BirthEra.HEISEI), sampleInitialPerson))
    }

    // --- バリデーション (isValid) ---

    @Test
    fun `VL_01_全項目正しく入力されていれば有効`() {
        assertTrue(PersonEditLogic.isValid(sampleValidState))
    }

    @Test
    fun `VL_02_苗字が空なら無効`() {
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(lastName = "")))
    }

    @Test
    fun `VL_05_不正な日付なら無効`() {
        assertFalse(PersonEditLogic.isValid(sampleValidState.copy(month = "2", day = "30")))
    }

    // --- Entity 生成 (createPerson) ---

    @Test
    fun `CP_01_新規Entity生成時にIDは0で値が反映されること`() {
        val entity = PersonEditLogic.createPerson(sampleValidState, null)
        assertNotNull(entity)
        assertEquals(0, entity!!.id)
        assertEquals("山田", entity.lastName)
        assertEquals(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), entity.birthday)
    }

    @Test
    fun `CP_02_既存Entity更新時にIDが維持されること`() {
        val state = sampleValidState.copy(lastName = " 田中 ") // スペースあり
        val entity = PersonEditLogic.createPerson(state, sampleInitialPerson)
        assertNotNull(entity)
        assertEquals(10, entity!!.id)
        assertEquals("田中", entity.lastName) // trim されていること
    }

    @Test
    fun `CP_03_日付が不正ならnullを返すこと`() {
        val state = sampleValidState.copy(year = "99", era = BirthEra.SHOWA) // 昭和99年は存在しない
        val entity = PersonEditLogic.createPerson(state, null)
        assertNull(entity)
    }
}
