package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState
import org.junit.Rule
import org.junit.Test

/**
 * SCR-M-004 EmergencyContactEditScreen の UI テスト
 */
class EmergencyContactEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cp01_initialDisplay_New() {
        val contact = EmergencyContact(personId = "p1", facilityName = "", contactType = "DOCTOR")
        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactEditContent(
                    uiState = EmergencyContactUiState(editingContact = contact, initialContact = contact),
                    onNavigateBack = {},
                    onUpdateContact = {},
                    onSaveClick = {}
                )
            }
        }

        // 初期表示：施設名が空
        composeTestRule.onNodeWithTag("EmergencyContact_FacilityField").assertTextContains("")
        // 保存ボタンが非活性 (isValid=false)
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun cp03_validation_enabledWhenValid() {
        val contact = EmergencyContact(personId = "p1", facilityName = "A病院", contactType = "DOCTOR")
        composeTestRule.setContent {
            CareMemoTheme {
                // isValid=true を想定した状態
                EmergencyContactEditContent(
                    uiState = EmergencyContactUiState(editingContact = contact, initialContact = contact),
                    onNavigateBack = {},
                    onUpdateContact = {},
                    onSaveClick = {}
                )
            }
        }

        // 施設名が入っていれば活性化（UiState.isValid が true なら）
        // 実際には UiState の定義に EmergencyContactLogic.isValid(editingContact) があるので反映される
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").assertIsEnabled()
    }

    @Test
    fun cp04_phoneField_focusBehavior() {
        var contact by mutableStateOf(EmergencyContact(personId = "p1", facilityName = "A", phoneNumber = "09012345678", contactType = "DOCTOR"))
        
        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactEditContent(
                    uiState = EmergencyContactUiState(editingContact = contact, initialContact = contact),
                    onNavigateBack = {},
                    onUpdateContact = { reducer -> contact = reducer(contact) },
                    onSaveClick = {}
                )
            }
        }

        // 非フォーカス時：ハイフンあり
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").assertTextContains("090-1234-5678")
        
        // フォーカス時：ハイフンなし
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").performClick()
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").assertTextContains("09012345678")
    }

    @Test
    fun bh02_discardDialog_displayed() {
        val initial = EmergencyContact(personId = "p1", facilityName = "A", contactType = "DOCTOR")
        val current = initial.copy(facilityName = "B") // 変更あり
        
        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactEditContent(
                    uiState = EmergencyContactUiState(editingContact = current, initialContact = initial),
                    onNavigateBack = {},
                    onUpdateContact = {},
                    onSaveClick = {}
                )
            }
        }

        // キャンセルボタン（ handleBack ➔ showDiscardDialog = true ）
        composeTestRule.onNodeWithTag("EmergencyContact_CancelButton").performClick()
        
        // 破棄確認ダイアログが表示されること
        composeTestRule.onNodeWithTag("EmergencyContact_DiscardDialog").assertIsDisplayed()
    }
}
