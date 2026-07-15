@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant

/**
 * PersonMedicationScreen (服薬管理) の UI テスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-PM-001_PersonMedicationScreen.md に準拠
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // テスト用のモックデータ
    private val mockPerson = Person(
        id = 1,
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z"),
    )

    // ======================================================================================
    // 1. 共通コンポーネントテスト (Header / CategoryBar)
    // ======================================================================================

    @Test
    fun COM_01_backButton_works() {
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.now(),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, 
                    onBack = { backCalled = true }, 
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("MedicationScreen_BackButton").assertIsDisplayed().performClick()
        assert(backCalled)
    }

    @Test
    fun COM_02_personInfo_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.now(),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田", substring = true).assertExists()
        // 年齢が表示されていること
        composeTestRule.onNodeWithText("歳", substring = true).assertExists()
    }

    @Test
    fun COM_03_pdfButton_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.now(),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("MedicationScreen_PdfButton").assertIsDisplayed()
    }

    @Test
    fun COM_04_categoryBar_navigationWorks() {
        var navigatedCategory: Category? = null
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.now(),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, 
                    onNavigateToCategory = { navigatedCategory = it }, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
        
        // 健康記録(バイタル)への遷移を検証
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performScrollTo().performClick()
        assert(navigatedCategory == Category.BP_AND_PULSE)

        // 所見メモへの遷移を検証
        composeTestRule.onNodeWithTag("CategoryChip_CONDITION_AT_VISIT").performScrollTo().performClick()
        assert(navigatedCategory == Category.CONDITION_AT_VISIT)
    }

    @Test
    fun COM_05_longName_isAppropriatelyDisplayed() {
        val longNamePerson = mockPerson.copy(lastName = "壽限無壽限無五劫の擦り切れ海砂利水魚の水行末雲来末風来末")
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = longNamePerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.now(),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // 表示が崩れず（ボタンが隠れず）に表示されていることを確認
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_PdfButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_BackButton").assertIsDisplayed()
    }

    // ======================================================================================
    // 2. コンポーネント単体テスト (PersonMedicationScreenContent)
    // ======================================================================================

    @Test
    fun CP_01_calendar_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false,
                    selectedMonth = YearMonth.now(),
                    isLoading = false,
                    recordsByDate = emptyMap(),
                    isHistoryMode = false,
                    onHistoryModeChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_Calendar").assertIsDisplayed()
    }

    @Test
    fun CP_02_historyTable_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false, selectedMonth = YearMonth.now(), isLoading = false, 
                    recordsByDate = emptyMap(), isHistoryMode = true, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onDayClick = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_HistoryTable").assertIsDisplayed()
    }

    @Test
    fun CP_03_emptyInputDialog_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                    date = LocalDate.of(2026, 7, 10), personId = 1, records = emptyList(),
                    onDismiss = {}, onConfirm = { }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_SaveButton").assertIsDisplayed()
    }

    @Test
    fun CP_04_inputDialog_isDisplayedAndInteractable() {
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                    date = LocalDate.of(2026, 7, 10), personId = 1, records = emptyList(),
                    onDismiss = {}, onConfirm = { }
                )
            }
        }
        // スロットの項目が表示されていることを確認
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用", useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    // ======================================================================================
    // 3. 画面全体の挙動・結合テスト (PersonMedicationScreen)
    // ======================================================================================

    @Test
    fun BH_01_saveMedicationRecord_works() {
        var confirmCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                    date = LocalDate.of(2026, 7, 10), personId = 1, records = emptyList(),
                    onDismiss = {}, onConfirm = { confirmCalled = true }
                )
            }
        }
        // ステータスを選択して保存
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用", useUnmergedTree = true).onFirst().performClick()
        composeTestRule.onNodeWithTag("Medication_SaveButton").performClick()
        
        assert(confirmCalled)
    }

    @Test
    fun BH_02_deleteMedicationRecord_works() {
        var confirmCalled = false
        val existingRecord = MedicationRecord(id = 1, personId = 1, dosageDate = "2026-07-10", timeSlot = 0, status = 2, recordTime = Instant.now())
        
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                    date = LocalDate.of(2026, 7, 10), personId = 1, records = listOf(existingRecord),
                    onDismiss = {}, onConfirm = { confirmCalled = true }
                )
            }
        }
        // 既存のステータス（服用）を再度タップして解除
        composeTestRule.onAllNodesWithTag("Medication_StatusChip_服用", useUnmergedTree = true).onFirst().performClick()
        composeTestRule.onNodeWithTag("Medication_SaveButton").performClick()
        
        assert(confirmCalled)
    }

    @Test
    fun BH_03_monthSwitching_works() {
        var nextCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = { nextCalled = true }, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_MonthNext_Phone").performClick()
        assert(nextCalled)
    }

    @Test
    fun BH_04_pdfSettings_showsMedicationSpecificItemsOnly() {
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.common.PdfSettingsDialog(
                    category = Category.MEDICATION,
                    onDismiss = {},
                    onExport = { _, _, _, _, _, _ -> }
                )
            }
        }
        // 所見特有の項目が表示されていないことを確認
        composeTestRule.onNodeWithText("最新の1件のみ").assertDoesNotExist()
        composeTestRule.onNodeWithText("写真を含める").assertDoesNotExist()
    }
}
