@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * LOG-PC-001 PersonConditionLogic のテスト
 */
class PersonConditionLogicTest {

    private val now = Instant.now()
    private val defaultAuthor = "記録者"

    private val sampleRecord = ConditionAtVisit(
        id = 1,
        personId = 1,
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = now
    )

    private val sampleState = PersonConditionUiState(
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = now
    )

    // ======================================================================================
    // 1. 変更検知テスト (isChanged)
    // ======================================================================================

    @Test
    fun chg_01_変更なし() {
        assertFalse(PersonConditionLogic.isChanged(sampleState, sampleRecord, defaultAuthor))
    }

    @Test
    fun chg_02_内容の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleState.copy(condition = "新しい内容"), sampleRecord, defaultAuthor))
    }

    @Test
    fun chg_03_記録者の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleState.copy(author = "別の記録者"), sampleRecord, defaultAuthor))
    }

    @Test
    fun chg_04_日時の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleState.copy(recordTime = now.plusSeconds(10)), sampleRecord, defaultAuthor))
    }

    // ======================================================================================
    // 2. バリデーションテスト (validate)
    // ======================================================================================

    @Test
    fun val_01_正常() {
        assertEquals(PersonConditionValidationResult.SUCCESS, PersonConditionLogic.validate(sampleState))
    }

    @Test
    fun val_02_内容が空() {
        assertEquals(PersonConditionValidationResult.EMPTY_CONDITION, PersonConditionLogic.validate(sampleState.copy(condition = " ")))
    }

    @Test
    fun val_03_記録者が空() {
        assertEquals(PersonConditionValidationResult.EMPTY_AUTHOR, PersonConditionLogic.validate(sampleState.copy(author = "")))
    }
}
