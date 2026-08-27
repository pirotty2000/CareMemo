package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.logic.feature.EmergencyContactLogic
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactEditViewModel
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactViewEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Test: EmergencyContactEditScreen (SCR-M-004)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-M-004_EmergencyContactEditScreen.md に準拠
 */
class EmergencyContactEditScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_initialDisplay_newMode() {
        val contact = EmergencyContact(personId = "p1", facilityName = "", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = contact, isEditing = true)
        }
        composeTestRule.onNodeWithTag("EmergencyContact_FacilityField").assertTextContains("")
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").assertIsNotEnabled()
    }

    @Test
    fun DSP_02_initialDisplay_editMode() {
        val contact = EmergencyContact(personId = "p1", facilityName = "A病院", phoneNumber = "0312345678", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = contact, isEditing = true)
        }
        composeTestRule.onNodeWithTag("EmergencyContact_FacilityField").assertTextContains("A病院")
        // Note: Phone field displays with transformation when not focused
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").assertTextContains("03-1234-5678")
    }

    @Test
    fun DSP_03_saveButton_isEnabled_whenValid() {
        // Valid contact (facilityName not blank)
        val validContact = EmergencyContact(personId = "p1", facilityName = "A", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = validContact)
        }
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").assertIsEnabled()
    }

    @Test
    fun DSP_03_saveButton_isDisabled_whenInvalid() {
        // Invalid contact (facilityName blank)
        val invalidContact = EmergencyContact(personId = "p1", facilityName = "", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = invalidContact)
        }
        composeTestRule.onNodeWithTag("EmergencyContact_SaveButton").assertIsNotEnabled()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_inputChange_triggersCallback() {
        var capturedValue = ""
        val contact = EmergencyContact(personId = "p1", facilityName = "", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(
                contact = contact,
                onUpdateContact = { reducer -> capturedValue = reducer(contact).facilityName }
            )
        }
        composeTestRule.onNodeWithTag("EmergencyContact_FacilityField").performTextInput("Clinic")
        assert(capturedValue == "Clinic")
    }

    @Test
    fun ACT_02_phoneField_removesHyphensOnFocus() {
        val contact = EmergencyContact(personId = "p1", facilityName = "A", phoneNumber = "09011112222", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = contact)
        }
        
        // Initial state: hyphens present
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").assertTextContains("090-1111-2222")
        
        // Focused state: hyphens removed
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").performClick()
        composeTestRule.onNodeWithTag("EmergencyContact_PhoneField").assertTextContains("09011112222")
    }

    @Test
    fun ACT_04_cancelWithChanges_showsDiscardDialog() {
        val contact = EmergencyContact(personId = "p1", facilityName = "Changed", contactType = "DOCTOR")
        setContent {
            EmergencyContactEditContentWrapper(contact = contact, isChanged = true)
        }
        
        composeTestRule.onNodeWithTag("EmergencyContact_CancelButton").performClick()
        composeTestRule.onNodeWithTag("EmergencyContact_DiscardDialog").assertIsDisplayed()
    }

    //endregion

    //region 4. ナビゲーション・イベント実行テスト (Navigation & Side Effects)

    @Test
    fun NAV_01_navigateBack_onSaveSuccess() {
        val viewModel = mockk<EmergencyContactEditViewModel>(relaxed = true)
        val navController = mockk<NavHostController>(relaxed = true)
        // Use extraBufferCapacity to ensure tryEmit succeeds
        val viewEventFlow = MutableSharedFlow<EmergencyContactViewEvent>(extraBufferCapacity = 1)
        
        val contact = EmergencyContact(personId = "p1", facilityName = "A", contactType = "DOCTOR")
        every { viewModel.uiState } returns MutableStateFlow(EmergencyContactUiState(editingContact = contact))
        every { viewModel.viewEvent } returns viewEventFlow
        every { viewModel.uiEventFlow } returns MutableSharedFlow()

        composeTestRule.setContent {
            CareMemoTheme {
                EmergencyContactEditScreen(viewModel = viewModel, navController = navController)
            }
        }

        composeTestRule.runOnIdle {
            viewEventFlow.tryEmit(EmergencyContactViewEvent.SaveSuccess)
        }
        
        composeTestRule.waitForIdle()
        verify { navController.popBackStack() }
    }

    //endregion

    // --- Helpers ---

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CareMemoTheme {
                content()
            }
        }
    }

    @Composable
    private fun EmergencyContactEditContentWrapper(
        contact: EmergencyContact,
        isEditing: Boolean = false,
        isChanged: Boolean = false,
        onUpdateContact: ((EmergencyContact) -> EmergencyContact) -> Unit = {}
    ) {
        val initialContact = if (isChanged) contact.copy(facilityName = "diff") else contact
        EmergencyContactEditContent(
            uiState = EmergencyContactUiState(
                editingContact = contact,
                initialContact = initialContact,
                isEditing = isEditing,
                isChanged = EmergencyContactLogic.isChanged(contact, initialContact),
                isValid = EmergencyContactLogic.isValid(contact)
            ),
            onAction = { action ->
                when (action) {
                    is EmergencyContactEditUiAction.UpdateContact -> onUpdateContact(action.reducer)
                    else -> {}
                }
            }
        )
    }
}
