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

    private val now = Instant.now()
    private val sampleInput = ConditionEditInput(
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = now
    )
    private val sampleSnapshot = sampleInput.copy()

    // region 2. 変更検知テスト (isChanged)

    @Test
    fun CHG_01_isChanged_noChange() {
        assertFalse(PersonConditionLogic.isChanged(sampleInput, sampleSnapshot))
    }

    @Test
    fun CHG_02_isChanged_titleChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(title = "new title"), sampleSnapshot))
    }

    @Test
    fun CHG_03_isChanged_conditionChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(condition = "new content"), sampleSnapshot))
    }

    @Test
    fun CHG_04_isChanged_authorChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(author = "new author"), sampleSnapshot))
    }

    @Test
    fun CHG_05_isChanged_recordTimeChanged() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(recordTime = now.plusSeconds(1)), sampleSnapshot))
    }

    @Test
    fun CHG_06_isChanged_noSnapshot() {
        assertFalse(PersonConditionLogic.isChanged(sampleInput, null))
    }

    // endregion

    // region 3. バリデーションテスト (validate / isValid)

    @Test
    fun VAL_01_validate_success() {
        assertEquals(PersonConditionValidationResult.SUCCESS, PersonConditionLogic.validate(sampleInput))
        assertTrue(PersonConditionLogic.isValid(sampleInput))
    }

    @Test
    fun VAL_02_validate_emptyCondition() {
        val input = sampleInput.copy(condition = "  ")
        assertEquals(PersonConditionValidationResult.EMPTY_CONDITION, PersonConditionLogic.validate(input))
        assertFalse(PersonConditionLogic.isValid(input))
    }

    @Test
    fun VAL_03_validate_emptyAuthor() {
        val input = sampleInput.copy(author = "")
        assertEquals(PersonConditionValidationResult.EMPTY_AUTHOR, PersonConditionLogic.validate(input))
        assertFalse(PersonConditionLogic.isValid(input))
    }

    @Test
    fun VAL_04_validate_nullTime() {
        val input = sampleInput.copy(recordTime = null)
        assertEquals(PersonConditionValidationResult.INVALID_TIME, PersonConditionLogic.validate(input))
        assertFalse(PersonConditionLogic.isValid(input))
    }

    @Test
    fun VAL_05_validate_conditionTooLong() {
        val longCondition = "a".repeat(AppSpecifications.Condition.Validation.MAX_LENGTH_MEMO + 1)
        val input = sampleInput.copy(condition = longCondition)
        assertEquals(PersonConditionValidationResult.CONDITION_TOO_LONG, PersonConditionLogic.validate(input))
    }

    @Test
    fun VAL_06_validate_titleTooLong() {
        val longTitle = "t".repeat(AppSpecifications.Condition.Validation.MAX_LENGTH_TITLE + 1)
        val input = sampleInput.copy(title = longTitle)
        assertEquals(PersonConditionValidationResult.TITLE_TOO_LONG, PersonConditionLogic.validate(input))
    }

    // endregion

    // region 4. Entity 生成テスト (createRecord)

    @Test
    fun CRT_01_createRecord_mapping() {
        val result = PersonConditionLogic.createRecord("person-1", "id-1", sampleInput)
        assertEquals("id-1", result.id)
        assertEquals("person-1", result.personId)
        assertEquals(sampleInput.title, result.title)
        assertEquals(sampleInput.condition, result.condition)
        assertEquals(sampleInput.author, result.author)
        assertEquals(sampleInput.recordTime, result.recordTime)
    }

    @Test
    fun CRT_02_createRecord_trimming() {
        val input = sampleInput.copy(
            title = "  title  ",
            condition = "  content  ",
            author = "  author  "
        )
        val result = PersonConditionLogic.createRecord("p1", "id1", input)
        assertEquals("title", result.title)
        assertEquals("content", result.condition)
        assertEquals("author", result.author)
    }

    @Test
    fun CRT_03_createRecord_newId() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val result = PersonConditionLogic.createRecord("p1", newId, sampleInput)
        assertNotEquals(newId, result.id)
        assertFalse(IdLogic.isNew(result.id))
    }

    @Test
    fun CRT_04_createRecord_maintainId() {
        val result = PersonConditionLogic.createRecord("p1", "persisted-id", sampleInput)
        assertEquals("persisted-id", result.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun CRT_05_createRecord_invalidTime() {
        PersonConditionLogic.createRecord("p1", "id1", sampleInput.copy(recordTime = null))
    }

    // endregion
}
