@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PersonConditionLogicTest {

    private val initial = ConditionAtVisit(
        id = 1,
        personId = 1,
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = Instant.ofEpochMilli(1000)
    )

    private val baseState = PersonConditionUiState(
        title = "タイトル",
        condition = "内容",
        author = "記録者",
        recordTime = Instant.ofEpochMilli(1000)
    )

    @Test
    fun `CH_01_変更がない場合はfalseを返すこと`() {
        assertFalse(PersonConditionLogic.isChanged(baseState, initial, "記録者"))
    }

    @Test
    fun `CH_02_タイトルが変わればtrueを返すこと`() {
        assertTrue(PersonConditionLogic.isChanged(baseState.copy(title = "新タイトル"), initial, "記録者"))
    }

    @Test
    fun `CH_03_新規作成時でデフォルト値ならfalseを返すこと`() {
        val state = PersonConditionUiState(author = "管理者")
        assertFalse(PersonConditionLogic.isChanged(state, null, "管理者"))
    }

    @Test
    fun `VL_01_必須項目が埋まっていれば有効`() {
        assertTrue(PersonConditionLogic.isValid(baseState))
    }

    @Test
    fun `VL_02_記録者が空なら無効`() {
        assertFalse(PersonConditionLogic.isValid(baseState.copy(author = "")))
    }

    @Test
    fun `VL_03_内容が空なら無効`() {
        assertFalse(PersonConditionLogic.isValid(baseState.copy(condition = "")))
    }
}
