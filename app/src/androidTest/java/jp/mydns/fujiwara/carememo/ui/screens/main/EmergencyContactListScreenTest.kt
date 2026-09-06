package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactScreenState
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Test: EmergencyContactListScreen (SCR-M-003)
 */
class EmergencyContactListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_personName_isDisplayedInHeader() {
        val personName = "テスト利用者"
        setContent {
            EmergencyContactListContentWrapper(personName = personName)
        }
        composeTestRule.onNodeWithText(personName, substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_02_contactList_rendersItems() {
        val contacts = listOf(
            EmergencyContact(id = "c1", personId = "p1", facilityName = "A病院", phoneNumber = "0312345678", contactType = "DOCTOR"),
            EmergencyContact(id = "c2", personId = "p1", facilityName = "B訪問看護", phoneNumber = "09011112222", contactType = "NURSING_STATION")
        )

        setContent {
            EmergencyContactListContentWrapper(contacts = contacts)
        }

        composeTestRule.onNodeWithTag("EmergencyContactItem_c1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("EmergencyContactItem_c2").assertIsDisplayed()

        composeTestRule.onNodeWithText("A病院").assertIsDisplayed()
        composeTestRule.onNodeWithText("03-1234-5678").assertIsDisplayed()
    }

    @Test
    fun DSP_03_emptyState_isDisplayed() {
        setContent {
            EmergencyContactListContentWrapper(contacts = emptyList())
        }
        composeTestRule.onNodeWithText("連絡先が登録されていません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_addButton_triggersCallback() {
        var addClicked = false
        setContent {
            EmergencyContactListContentWrapper(onAddClick = { addClicked = true })
        }
        composeTestRule.onNodeWithTag("MedicalContactList_AddButton").performClick()
        assert(addClicked)
    }

    @Test
    fun ACT_03_ACT_04_deleteProcess_showsDialogAndConfirms() {
        val contact = EmergencyContact(id = "c1", personId = "p1", facilityName = "Target Hospital", contactType = "DOCTOR")
        var deleteConfirmed = false

        setContent {
            EmergencyContactListContentWrapper(
                contacts = listOf(contact),
                onDeleteConfirm = { deleteConfirmed = true }
            )
        }

        // Open menu
        composeTestRule.onNodeWithContentDescription("メニュー", substring = true).performClick()
        // Click delete in menu (use unmerged tree if necessary, but usually text works)
        composeTestRule.onNodeWithText("削除", substring = true).performClick()

        // Verify dialog displayed
        composeTestRule.onNodeWithText("連絡先の削除").assertIsDisplayed()
        
        // Confirm delete in dialog
        composeTestRule.onAllNodesWithText("削除").onLast().performClick()
        
        assert(deleteConfirmed)
    }

    //endregion

    // --- Helpers ---

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent {
            CareMemoTheme {
                content()
            }
        }
        composeTestRule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun EmergencyContactListContentWrapper(
        personName: String = "Name",
        contacts: List<EmergencyContact> = emptyList(),
        onAddClick: () -> Unit = {},
        onDeleteConfirm: (EmergencyContact) -> Unit = {}
    ) {
        EmergencyContactListContent(
            uiState = EmergencyContactUiState(
                screenState = EmergencyContactScreenState.Active,
                personName = personName,
                contacts = contacts.toImmutableList()
            ),
            onAction = { action ->
                when (action) {
                    EmergencyContactListUiAction.AddClick -> onAddClick()
                    is EmergencyContactListUiAction.DeleteConfirm -> onDeleteConfirm(action.contact)
                    else -> {}
                }
            }
        )
    }
}
