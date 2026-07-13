package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
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
 * 仕様書：doc/test/TEST_SPEC_UI_PersonMedication.md に準拠
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
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    // ======================================================================================
    // 1. 詳細画面共通コンポーネント (COM)
    // ======================================================================================

    @Test
    fun com01_to_03_header_isDisplayed_and_actionsWork() {
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, 
                    onBack = { backCalled = true }, 
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        composeTestRule.onNodeWithTag("Medication_BackButton").assertIsDisplayed().performClick()
        assert(backCalled)

        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge").assertIsDisplayed()
        composeTestRule.onNodeWithText("山田", substring = true).assertExists()
        composeTestRule.onNodeWithText("76歳", substring = true).assertExists()

        composeTestRule.onNodeWithTag("Medication_PdfButton").assertIsDisplayed()
    }

    @Test
    fun com04_categoryBar_navigationWorks() {
        var navigatedCategory: Category? = null
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, 
                    onNavigateToCategory = { navigatedCategory = it }, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performClick()
        assert(navigatedCategory == Category.BP_AND_PULSE)
    }

    // ======================================================================================
    // 2. 個別コンポーネント単体テスト (CP)
    // ======================================================================================

    @Test
    fun cp01_loadingState_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = true, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_Loading").assertIsDisplayed()
    }

    @Test
    fun cp02_cp06_phoneMode_default_showsCalendarAndMonth() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_Calendar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Medication_MonthText_Phone").assertTextContains("2026(令和8)年07月")
    }

    @Test
    fun cp03_phoneMode_historyMode_showsHistoryTable() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = true, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_HistoryTable").assertIsDisplayed()
    }

    @Test
    fun cp04_phoneMode_historyEmpty_showsMessage() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = true, onHistoryModeChange = {},
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithText("記録がありません").assertIsDisplayed()
    }

    @Test
    fun cp05_tabletMode_showsBothCalendarAndHistory() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenTablet(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, 
                    onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_Calendar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Medication_HistoryTable").assertIsDisplayed()
    }

    // ======================================================================================
    // 3. 画面全体の挙動・結合テスト (BH)
    // ======================================================================================

    @Test
    fun bh01_monthNavigation_callsCallbacks() {
        var prevCalled = false
        var nextCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenPhone(
                    currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                    recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                    onPreviousMonth = { prevCalled = true }, onNextMonth = { nextCalled = true }, onBack = {}, onNavigateToCategory = {}, 
                    onShowPdfSettings = {}, onDayClick = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("Medication_MonthPrev_Phone").performClick()
        assert(prevCalled)
        composeTestRule.onNodeWithTag("Medication_MonthNext_Phone").performClick()
        assert(nextCalled)
    }

    @Test
    fun bh02_dayClick_opensDialog() {
        var clickedDate: LocalDate? = null
        composeTestRule.setContent {
            CareMemoTheme {
                var showDialogDate by remember { mutableStateOf<LocalDate?>(null) }
                Box {
                    PersonMedicationScreenPhone(
                        currentPerson = mockPerson, isNameMaskingEnabled = false, isLoading = false, selectedMonth = YearMonth.of(2026, 7),
                        recordsByDate = emptyMap(), personCategorySummary = null, isHistoryMode = false, onHistoryModeChange = {},
                        onPreviousMonth = {}, onNextMonth = {}, onBack = {}, onNavigateToCategory = {}, 
                        onShowPdfSettings = {}, 
                        onDayClick = { showDialogDate = it; clickedDate = it },
                        snackbarHostState = remember { SnackbarHostState() }
                    )
                    if (showDialogDate != null) {
                        jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                            date = showDialogDate!!, personId = 1, records = emptyList(),
                            onDismiss = { showDialogDate = null }, onConfirm = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("Medication_DayCell_2026-07-10").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("Medication_Dialog_Save").assertIsDisplayed()
        assert(clickedDate == LocalDate.of(2026, 7, 10))
    }

    @Test
    fun bh03_dialogSave_callsCallback() {
        var savedRecords: List<MedicationRecord?>? = null
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.medication.MedicationInputDialog(
                    date = LocalDate.of(2026, 7, 10),
                    personId = 1,
                    records = emptyList(),
                    onDismiss = {},
                    onConfirm = { savedRecords = it }
                )
            }
        }
        // 「服用」テキスト（朝の枠）をクリック
        composeTestRule.onAllNodesWithText("服用").onFirst().performClick()
        // 保存ボタンをクリック
        composeTestRule.onNodeWithTag("Medication_Dialog_Save").performClick()
        composeTestRule.waitForIdle()
        
        assert(savedRecords != null)
        assert(savedRecords!![0]?.status == 2)
    }
}
