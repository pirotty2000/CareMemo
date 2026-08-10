package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.viewmodel.DeleteOrRestorePersonViewModel
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
        state = state.copy(selectedIds = DeleteOrRestorePersonLogic.toggleSelection(state.selectedIds, "1").toImmutableSet())
        assertEquals(setOf("1"), state.selectedIds)
        
        // 削除
        state = state.copy(selectedIds = DeleteOrRestorePersonLogic.toggleSelection(state.selectedIds, "1").toImmutableSet())
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

    @Test
    fun lg_01_toggleSelection_add() {
        val current = setOf("1", "2")
        val result = DeleteOrRestorePersonLogic.toggleSelection(current, "3")
        assertEquals(setOf("1", "2", "3"), result)
    }

    @Test
    fun lg_02_toggleSelection_remove() {
        val current = setOf("1", "2", "3")
        val result = DeleteOrRestorePersonLogic.toggleSelection(current, "2")
        assertEquals(setOf("1", "3"), result)
    }

    @Test
    fun lg_03_selectAll() {
        val persons = listOf(
            Person(id = "1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()),
            Person(id = "2", lastName = "C", firstName = "D", lastNameFurigana = "C", firstNameFurigana = "D", birthday = Instant.now()),
            Person(id = "5", lastName = "E", firstName = "F", lastNameFurigana = "E", firstNameFurigana = "F", birthday = Instant.now())
        )
        val result = DeleteOrRestorePersonLogic.selectAll(persons)
        assertEquals(setOf("1", "2", "5"), result)
    }

    @Test
    fun lg_04_filterTargets() {
        val persons = listOf(
            Person(id = "1", lastName = "A", firstName = "B", lastNameFurigana = "A", firstNameFurigana = "B", birthday = Instant.now()),
            Person(id = "2", lastName = "C", firstName = "D", lastNameFurigana = "C", firstNameFurigana = "D", birthday = Instant.now()),
            Person(id = "3", lastName = "E", firstName = "F", lastNameFurigana = "E", firstNameFurigana = "F", birthday = Instant.now())
        )
        val selectedIds = setOf("1", "3")
        val result = DeleteOrRestorePersonLogic.filterTargets(persons, selectedIds)
        
        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("3", result[1].id)
    }

    @Test
    fun lg_05_selectAll_empty() {
        val result = DeleteOrRestorePersonLogic.selectAll(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun lg_06_validate_success() {
        val selectedIds = setOf("1")
        val result = DeleteOrRestorePersonLogic.validate(selectedIds)
        assertEquals(DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.SUCCESS, result)
    }

    @Test
    fun lg_07_validate_no_selection() {
        val selectedIds = emptySet<String>()
        val result = DeleteOrRestorePersonLogic.validate(selectedIds)
        assertEquals(DeleteOrRestorePersonLogic.DeleteOrRestoreValidationResult.NO_SELECTION, result)
    }
}
