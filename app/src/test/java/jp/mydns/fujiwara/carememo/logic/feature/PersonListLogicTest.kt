package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Logic層テスト：PersonListLogic
 */
class PersonListLogicTest {

    private val defaultBirthday = Instant.parse("1950-01-01T00:00:00Z")

    // region 2. 五十音行判定テスト (getSection)

    @Test
    fun SEC_01_getSection_aLine() {
        assertEquals("あ", PersonListLogic.getSection("あ"))
        assertEquals("あ", PersonListLogic.getSection("い"))
        assertEquals("あ", PersonListLogic.getSection("お"))
    }

    @Test
    fun SEC_02_getSection_kaLine() {
        assertEquals("か", PersonListLogic.getSection("か"))
        assertEquals("か", PersonListLogic.getSection("こ"))
    }

    @Test
    fun SEC_03_getSection_voicedAndSemiVoiced() {
        assertEquals("か", PersonListLogic.getSection("が"))
        assertEquals("さ", PersonListLogic.getSection("ざ"))
        assertEquals("た", PersonListLogic.getSection("だ"))
        assertEquals("は", PersonListLogic.getSection("ば"))
        assertEquals("は", PersonListLogic.getSection("ぱ"))
    }

    @Test
    fun SEC_04_getSection_sokuonAndYouon() {
        assertEquals("た", PersonListLogic.getSection("っ"))
        assertEquals("や", PersonListLogic.getSection("ゃ"))
    }

    @Test
    fun SEC_05_getSection_waLine() {
        assertEquals("わ", PersonListLogic.getSection("わ"))
        assertEquals("わ", PersonListLogic.getSection("を"))
        assertEquals("わ", PersonListLogic.getSection("ん"))
    }

    @Test
    fun SEC_06_getSection_others() {
        assertEquals("他", PersonListLogic.getSection("A"))
        assertEquals("他", PersonListLogic.getSection("1"))
        assertEquals("他", PersonListLogic.getSection("-"))
        assertEquals("他", PersonListLogic.getSection(""))
    }

    // endregion

    // region 3. リストフィルタリングテスト (filterPersons)

    private val testPersons = listOf(
        Person(id = "1", lastName = "浅井", firstName = "太郎", lastNameFurigana = "あさい", firstNameFurigana = "たろう", birthday = defaultBirthday),
        Person(id = "2", lastName = "加藤", firstName = "次郎", lastNameFurigana = "かとう", firstNameFurigana = "じろう", birthday = defaultBirthday),
        Person(id = "3", lastName = "佐藤", firstName = "三郎", lastNameFurigana = "さとう", firstNameFurigana = "さぶろう", birthday = defaultBirthday)
    )

    @Test
    fun FLT_01_filterPersons_all() {
        val result = PersonListLogic.filterPersons(testPersons, "全", null)
        assertEquals(3, result.size)
    }

    @Test
    fun FLT_02_filterPersons_section() {
        val result = PersonListLogic.filterPersons(testPersons, "か", null)
        assertEquals(1, result.size)
        assertEquals("加藤", result[0].lastName)
    }

    @Test
    fun FLT_03_filterPersons_searchIds() {
        val result = PersonListLogic.filterPersons(testPersons, "全", listOf("1", "3"))
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "1" })
        assertTrue(result.any { it.id == "3" })
    }

    @Test
    fun FLT_04_filterPersons_combined() {
        val result = PersonListLogic.filterPersons(testPersons, "あ", listOf("1", "10"))
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun FLT_05_filterPersons_noMatch() {
        val result = PersonListLogic.filterPersons(testPersons, "な", null)
        assertTrue(result.isEmpty())
    }

    // endregion

    // region 4. UI状態変換テスト (createPersonUiState)

    @Test
    fun UI_01_createPersonUiState_masking() {
        val person = Person(id = "1", lastName = "田中", firstName = "太郎", lastNameFurigana = "たなか", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val state = PersonListLogic.createPersonUiState(person, true, null)
        
        assertEquals("田○\u3000太○", state.maskedName)
        assertEquals("た○○\u3000た○○", state.maskedFurigana)
    }

    @Test
    fun UI_02_createPersonUiState_ageCalculation() {
        // 現在の日付から逆算して年齢を検証（DateTimeUtilsが正しく使われていることの確認）
        val birthday = LocalDate.now().minusYears(80).atStartOfDay(ZoneOffset.UTC).toInstant()
        val person = Person(id = "1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = birthday)
        val state = PersonListLogic.createPersonUiState(person, false, null)
        
        assertEquals(80, state.age)
    }

    @Test
    fun UI_03_createPersonUiState_formattedBirthday() {
        val birthday = LocalDate.of(1950, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val person = Person(id = "1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = birthday)
        val state = PersonListLogic.createPersonUiState(person, false, null)
        
        // DateTimeUtils.formatDateJapaneseEra を通じて和暦になっていること
        assertTrue(state.formattedBirthday.contains("昭和25年"))
    }

    @Test
    fun UI_04_createPersonUiState_summary() {
        val person = Person(id = "1", lastName = "田中", firstName = "太郎", lastNameFurigana = "たなか", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val summary = PersonCategorySummary(hasHeightWeight = true)
        val state = PersonListLogic.createPersonUiState(person, false, summary)
        
        assertTrue(state.summary.hasHeightWeight)
        assertFalse(state.summary.hasBpAndPulse)
    }

    @Test
    fun UI_05_createPersonUiState_nullSummary() {
        val person = Person(id = "1", lastName = "田中", firstName = "太郎", lastNameFurigana = "たなか", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val state = PersonListLogic.createPersonUiState(person, false, null)
        
        assertNotNull(state.summary)
        assertFalse(state.summary.hasHeightWeight)
    }

    // endregion

    // region 5. 重複判定テスト (validateDuplicate)

    @Test
    fun DUP_01_validateDuplicate_noDuplicate() {
        val input = Person(id = "NEW", lastName = "新規", firstName = "太郎", lastNameFurigana = "しんき", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val result = PersonListLogic.validateDuplicate(input, null)
        assertEquals(PersonDuplicateResult.SUCCESS, result)
    }

    @Test
    fun DUP_02_validateDuplicate_activeDuplicate() {
        val input = Person(id = "NEW", lastName = "既存", firstName = "太郎", lastNameFurigana = "きぞん", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val existing = Person(id = "persisted-1", lastName = "既存", firstName = "太郎", lastNameFurigana = "きぞん", firstNameFurigana = "たろう", birthday = defaultBirthday, deletedAt = null)
        val result = PersonListLogic.validateDuplicate(input, existing)
        assertEquals(PersonDuplicateResult.DUPLICATE_ACTIVE, result)
    }

    @Test
    fun DUP_03_validateDuplicate_archivedDuplicate() {
        val input = Person(id = "NEW", lastName = "既存", firstName = "太郎", lastNameFurigana = "きぞん", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val existing = Person(id = "persisted-1", lastName = "既存", firstName = "太郎", lastNameFurigana = "きぞん", firstNameFurigana = "たろう", birthday = defaultBirthday, deletedAt = 12345L)
        val result = PersonListLogic.validateDuplicate(input, existing)
        assertEquals(PersonDuplicateResult.DUPLICATE_ARCHIVED, result)
    }

    @Test
    fun DUP_04_validateDuplicate_selfUpdate() {
        val input = Person(id = "p-1", lastName = "自分", firstName = "太郎", lastNameFurigana = "じぶん", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val existing = Person(id = "p-1", lastName = "自分", firstName = "太郎", lastNameFurigana = "じぶん", firstNameFurigana = "たろう", birthday = defaultBirthday)
        val result = PersonListLogic.validateDuplicate(input, existing)
        assertEquals(PersonDuplicateResult.SUCCESS, result)
    }

    // endregion
}
