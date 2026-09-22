package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Logic層テスト：PersonConditionLogic
 */
class PersonConditionLogicTest {

    private val sampleInput = ConditionEditInput(
        title = "タイトル",
        condition = "良好です",
        author = "記録者A",
        recordTime = Instant.now()
    )

    // region 2. バリデーションテスト (validate / isValid)

    @Test
    fun VAL_01_validate_success() {
        assertEquals(PersonConditionValidationResult.SUCCESS, PersonConditionLogic.validate(sampleInput))
        assertTrue(PersonConditionLogic.isValid(sampleInput))
    }

    @Test
    fun VAL_02_validate_emptyCondition() {
        val input = sampleInput.copy(condition = " ")
        assertEquals(PersonConditionValidationResult.EMPTY_CONDITION, PersonConditionLogic.validate(input))
        assertFalse(PersonConditionLogic.isValid(input))
    }

    @Test
    fun VAL_03_validate_emptyAuthor() {
        val input = sampleInput.copy(author = "")
        assertEquals(PersonConditionValidationResult.EMPTY_AUTHOR, PersonConditionLogic.validate(input))
    }

    @Test
    fun VAL_04_validate_titleTooLong() {
        val longTitle = "a".repeat(AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE + 1)
        val input = sampleInput.copy(title = longTitle)
        assertEquals(PersonConditionValidationResult.TITLE_TOO_LONG, PersonConditionLogic.validate(input))
    }

    @Test
    fun VAL_05_validate_conditionTooLong() {
        val longMemo = "m".repeat(AppSpecifications.Condition.Validation.MAX_LENGTH_MEMO + 1)
        val input = sampleInput.copy(condition = longMemo)
        assertEquals(PersonConditionValidationResult.CONDITION_TOO_LONG, PersonConditionLogic.validate(input))
    }

    // endregion

    // region 3. 変更検知テスト (isChanged)

    @Test
    fun CHG_01_isChanged_noChange() {
        assertFalse(PersonConditionLogic.isChanged(sampleInput, sampleInput))
    }

    @Test
    fun CHG_02_isChanged_titleChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(title = "新題名"), sampleInput))
    }

    @Test
    fun CHG_03_isChanged_memoChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(condition = "変化あり"), sampleInput))
    }

    @Test
    fun CHG_04_isChanged_nullSnapshot() {
        assertFalse(PersonConditionLogic.isChanged(sampleInput, null))
    }

    // endregion

    // region 4. Record 生成テスト (createRecord)

    @Test
    fun CRT_01_createRecord_mapping() {
        val personId = "u1"
        val recordId = "p1"
        val result = PersonConditionLogic.createRecord(personId, recordId, sampleInput)

        assertEquals(recordId, result.id)
        assertEquals(personId, result.personId)
        assertEquals("タイトル", result.title)
        assertEquals("良好です", result.condition)
        assertEquals("記録者A", result.author)
        assertEquals(sampleInput.recordTime, result.recordTime)
    }

    @Test
    fun CRT_02_createRecord_trimming() {
        val input = sampleInput.copy(title = " タイトル ", author = " 名前 ")
        val result = PersonConditionLogic.createRecord("u1", "p1", input)

        assertEquals("タイトル", result.title)
        assertEquals("名前", result.author)
    }

    @Test
    fun CRT_03_createRecord_newId() {
        val result = PersonConditionLogic.createRecord("u1", AppSpecifications.Id.NEW_RECORD_ID, sampleInput)
        assertFalse(IdLogic.isNew(result.id))
    }

    // endregion
}
