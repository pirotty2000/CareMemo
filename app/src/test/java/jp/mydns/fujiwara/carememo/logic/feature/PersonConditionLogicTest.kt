package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppThresholds
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：PersonConditionLogic
 */
class PersonConditionLogicTest {

    private val defaultTime = Instant.parse("2023-10-01T10:00:00Z")
    private val defaultAuthor = "テスト担当者"

    // region 1. isChanged テスト

    @Test
    fun isChanged_noChange_returnsFalse() {
        val initial = ConditionAtVisit(1, 1, "タイトル", "内容", "作者", defaultTime)
        val current = PersonConditionUiState("タイトル", "内容", "作者", defaultTime)
        
        assertFalse(PersonConditionLogic.isChanged(current, initial, "作者"))
    }

    @Test
    fun isChanged_titleChanged_returnsTrue() {
        val initial = ConditionAtVisit(1, 1, "タイトル", "内容", "作者", defaultTime)
        val current = PersonConditionUiState("変更後のタイトル", "内容", "作者", defaultTime)
        
        assertTrue(PersonConditionLogic.isChanged(current, initial, "作者"))
    }

    @Test
    fun isChanged_conditionChanged_returnsTrue() {
        val initial = ConditionAtVisit(1, 1, "タイトル", "内容", "作者", defaultTime)
        val current = PersonConditionUiState("タイトル", "変更後の内容", "作者", defaultTime)
        
        assertTrue(PersonConditionLogic.isChanged(current, initial, "作者"))
    }

    @Test
    fun isChanged_authorChanged_returnsTrue() {
        val initial = ConditionAtVisit(1, 1, "タイトル", "内容", "作者", defaultTime)
        val current = PersonConditionUiState("タイトル", "内容", "新しい作者", defaultTime)
        
        assertTrue(PersonConditionLogic.isChanged(current, initial, "作者"))
    }

    @Test
    fun isChanged_timeChanged_returnsTrue() {
        val initial = ConditionAtVisit(1, 1, "タイトル", "内容", "作者", defaultTime)
        val current = PersonConditionUiState("タイトル", "内容", "作者", defaultTime.plusSeconds(3600))
        
        assertTrue(PersonConditionLogic.isChanged(current, initial, "作者"))
    }

    @Test
    fun isChanged_newRecordNoInput_returnsFalse() {
        val current = PersonConditionUiState(author = defaultAuthor)
        
        assertFalse(PersonConditionLogic.isChanged(current, null, defaultAuthor))
    }

    @Test
    fun isChanged_newRecordWithInput_returnsTrue() {
        val current = PersonConditionUiState(condition = "何か入力", author = defaultAuthor)
        
        assertTrue(PersonConditionLogic.isChanged(current, null, defaultAuthor))
    }

    // endregion

    // region 2. validate テスト

    @Test
    fun validate_validInput_returnsSuccess() {
        val current = PersonConditionUiState("タイトル", "正常な内容", "作者", defaultTime)
        
        assertEquals(PersonConditionValidationResult.SUCCESS, PersonConditionLogic.validate(current))
    }

    @Test
    fun validate_emptyCondition_returnsEmptyCondition() {
        val current = PersonConditionUiState("タイトル", "  ", "作者", defaultTime)
        
        assertEquals(PersonConditionValidationResult.EMPTY_CONDITION, PersonConditionLogic.validate(current))
    }

    @Test
    fun validate_emptyAuthor_returnsEmptyAuthor() {
        val current = PersonConditionUiState("タイトル", "内容", "", defaultTime)
        
        assertEquals(PersonConditionValidationResult.EMPTY_AUTHOR, PersonConditionLogic.validate(current))
    }

    @Test
    fun validate_nullTime_returnsInvalidTime() {
        val current = PersonConditionUiState("タイトル", "内容", "作者", null)
        
        assertEquals(PersonConditionValidationResult.INVALID_TIME, PersonConditionLogic.validate(current))
    }

    @Test
    fun validate_tooLongCondition_returnsTooLong() {
        val longText = "a".repeat(AppThresholds.CONDITION_MAX_LENGTH + 1)
        val current = PersonConditionUiState("タイトル", longText, "作者", defaultTime)
        
        assertEquals(PersonConditionValidationResult.CONDITION_TOO_LONG, PersonConditionLogic.validate(current))
    }

    // endregion

    // region 3. createRecord テスト

    @Test
    fun createRecord_mapsFieldsCorrectly() {
        val state = PersonConditionUiState(" Title ", " Body ", " Author ", defaultTime)
        val record = PersonConditionLogic.createRecord(100, 200, state)
        
        assertEquals(200, record.id)
        assertEquals(100, record.personId)
        assertEquals("Title", record.title)
        assertEquals("Body", record.condition)
        assertEquals("Author", record.author)
        assertEquals(defaultTime, record.recordTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun createRecord_nullTime_throwsException() {
        val state = PersonConditionUiState("タイトル", "内容", "作者", null)
        PersonConditionLogic.createRecord(1, 0, state)
    }

    // endregion

    // region 4. isValid テスト

    @Test
    fun isValid_success_returnsTrue() {
        val state = PersonConditionUiState("タイトル", "内容", "作者", defaultTime)
        assertTrue(PersonConditionLogic.isValid(state))
    }

    @Test
    fun isValid_fail_returnsFalse() {
        val state = PersonConditionUiState("タイトル", "", "作者", defaultTime)
        assertFalse(PersonConditionLogic.isValid(state))
    }

    // endregion
}
