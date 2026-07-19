package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic層テスト：DeleteOrRestorePersonLogic
 */
class DeleteOrRestorePersonLogicTest {

    @Test
    fun ui_01_initialState() {
        val state = DeleteOrRestorePersonUiState()
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.RESTORE, state.mode)
        assertTrue(state.archivedPersons.isEmpty())
        assertTrue(state.selectedIds.isEmpty())
        assertFalse(state.isLoading)
        assertTrue(state.isNameMaskingEnabled)
    }

    @Test
    fun ui_02_selectionIntegrity() {
        var state = DeleteOrRestorePersonUiState()
        
        // 追加
        state = state.copy(selectedIds = state.selectedIds + 1)
        assertEquals(setOf(1), state.selectedIds)
        
        // 削除
        state = state.copy(selectedIds = state.selectedIds - 1)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun ui_03_maskingSync() {
        val state = DeleteOrRestorePersonUiState(isNameMaskingEnabled = false)
        assertFalse(state.isNameMaskingEnabled)
    }

    @Test
    fun mod_01_switchToDeleteMode() {
        val state = DeleteOrRestorePersonUiState(mode = DeleteOrRestorePersonViewModel.OperationMode.DELETE)
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.DELETE, state.mode)
    }

    @Test
    fun mod_02_switchToRestoreMode() {
        val state = DeleteOrRestorePersonUiState(mode = DeleteOrRestorePersonViewModel.OperationMode.RESTORE)
        assertEquals(DeleteOrRestorePersonViewModel.OperationMode.RESTORE, state.mode)
    }
}
