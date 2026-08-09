@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

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

    private val sampleInput = ConditionEditInput(
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = now
    )

    private val sampleSnapshot = ConditionEditInput(
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
        assertFalse(PersonConditionLogic.isChanged(sampleInput, sampleSnapshot))
    }

    @Test
    fun chg_02_内容の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(condition = "新しい内容"), sampleSnapshot))
    }

    @Test
    fun chg_03_記録者の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(author = "別の記録者"), sampleSnapshot))
    }

    @Test
    fun chg_04_日時の変更() {
        assertTrue(PersonConditionLogic.isChanged(sampleInput.copy(recordTime = now.plusSeconds(10)), sampleSnapshot))
    }

    // ======================================================================================
    // 2. バリデーションテスト (validate)
    // ======================================================================================

    @Test
    fun val_01_正常() {
        assertEquals(PersonConditionValidationResult.SUCCESS, PersonConditionLogic.validate(sampleInput))
    }

    @Test
    fun val_02_内容が空() {
        assertEquals(PersonConditionValidationResult.EMPTY_CONDITION, PersonConditionLogic.validate(sampleInput.copy(condition = " ")))
    }

    @Test
    fun val_03_記録者が空() {
        assertEquals(PersonConditionValidationResult.EMPTY_AUTHOR, PersonConditionLogic.validate(sampleInput.copy(author = "")))
    }
}
