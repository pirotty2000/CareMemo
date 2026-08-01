package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState
import org.junit.Rule
import org.junit.Test

/**
 * SCR-M-003 EmergencyContactListScreen の UI テスト
 */
class EmergencyContactListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cp01_initialDisplay_Normal() {
        val contacts = listOf(
            EmergencyContact(id = "1", personId = "p1", facilityName = "A病院", personName = "田中", phoneNumber = "0312345678", contactType = "DOCTOR"),
            EmergencyContact(id = "2", personId = "p1", facilityName = "B訪問看護", phoneNumber = "09011112222", contactType = "NURSING_STATION")
        )

        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactListContent(
                    uiState = EmergencyContactUiState(personName = "利用者名", contacts = contacts),
                    onNavigateBack = {},
                    onAddClick = {},
                    onEditClick = {},
                    onDeleteConfirm = {}
                )
            }
        }

        // 施設名が表示されていること
        composeTestRule.onNodeWithText("A病院").assertIsDisplayed()
        composeTestRule.onNodeWithText("B訪問看護").assertIsDisplayed()
        
        // 整形された電話番号が表示されていること (formatPhoneNumber)
        composeTestRule.onNodeWithText("03-1234-5678").assertIsDisplayed()
        composeTestRule.onNodeWithText("090-1111-2222").assertIsDisplayed()
    }

    @Test
    fun cp02_initialDisplay_Empty() {
        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactListContent(
                    uiState = EmergencyContactUiState(personName = "利用者名", contacts = emptyList()),
                    onNavigateBack = {},
                    onAddClick = {},
                    onEditClick = {},
                    onDeleteConfirm = {}
                )
            }
        }

        // 空メッセージが表示されること
        composeTestRule.onNodeWithText("連絡先が登録されていません").assertIsDisplayed()
    }

    @Test
    fun bh01_addButtonClick() {
        var addClicked = false
        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactListContent(
                    uiState = EmergencyContactUiState(personName = "名"),
                    onNavigateBack = {},
                    onAddClick = { addClicked = true },
                    onEditClick = {},
                    onDeleteConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("MedicalContactList_AddButton").performClick()
        assert(addClicked)
    }
}
