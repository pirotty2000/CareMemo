package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Logic層テスト：PersonDetailLogic
 */
class PersonDetailLogicTest {

    // region 2. UI状態テスト (UiState)

    @Test
    fun UI_01_initialState() {
        val state = PersonDetailUiState()
        assertNull(state.personId)
        assertNull(state.person)
        assertNull(state.personSummary)
        assertEquals(Category.HEIGHT_AND_WEIGHT, state.currentCategory)
        assertFalse(state.isLoading)
    }

    // endregion
}
