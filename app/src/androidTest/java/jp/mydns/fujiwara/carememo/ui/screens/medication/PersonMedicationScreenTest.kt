package jp.mydns.fujiwara.carememo.ui.screens.medication

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonMedicationUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/**
 * Instrumented Test: PersonMedicationScreen (SCR-PM-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PM-001_PersonMedicationScreen.md に準拠
 */
class PersonMedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_TabletContent").assertDoesNotExist()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("MedicationScreen_TabletContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MedicationScreen_PhoneContent").assertDoesNotExist()
    }

    //endregion

    //region 3. コンポーネント描画検証 (Components)

    @Test
    fun CPN_01_calendar_rendersSpecifiedMonth() {
        val month = YearMonth.of(2024, 1)
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false,
                    selectedMonth = month,
                    isLoading = false,
                    recordsByDate = persistentMapOf(),
                    isHistoryMode = false,
                    onHistoryModeChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {}
                )
            }
        }
        // Use the actual utility to get the expected header string (it includes the era)
        val expectedHeader = DateTimeUtils.formatYearMonthHeader(month)
        composeTestRule.onNodeWithText(expectedHeader, substring = true).assertIsDisplayed()
    }

    @Test
    fun CPN_03_emptyState_isDisplayed_whenNoRecords() {
        // In history mode, empty records should show EmptyState
        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreenContent(
                    isExpanded = false,
                    selectedMonth = YearMonth.now(),
                    isLoading = false,
                    recordsByDate = persistentMapOf(),
                    isHistoryMode = true, // History mode
                    onHistoryModeChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. 状態・インタラクション検証 (Interaction)

    @Test
    fun ACT_01_nextMonthButton_triggersViewModel() {
        val detailViewModel = createMockDetailViewModel()
        val medicationViewModel = createMockMedicationViewModel()
        
        every { medicationViewModel.uiState } returns MutableStateFlow(PersonMedicationUiState(personId = "p1"))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel, 
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // Tap Next Month
        composeTestRule.onNodeWithTag("Medication_MonthNext_Phone").performClick()
        verify { medicationViewModel.nextMonth() }
    }

    //endregion

    //region 5. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_navigatesBack() {
        val detailViewModel = createMockDetailViewModel()
        val medicationViewModel = createMockMedicationViewModel()

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("MedicationScreen_BackButton").performClick()
        verify { detailViewModel.navigateBackToMain() }
    }

    //endregion

    //region 6. セキュリティ検証 (Security)

    @Test
    fun SEC_01_pdfExport_requiresAuthentication() {
        val detailViewModel = createMockDetailViewModel()
        val medicationViewModel = createMockMedicationViewModel()
        val person = jp.mydns.fujiwara.carememo.data.Person(
            id = "p1",
            lastName = "Test",
            firstName = "User",
            lastNameFurigana = "てすと",
            firstNameFurigana = "ゆーざー",
            birthday = java.time.Instant.EPOCH
        )
        val onRequireAuthentication = mockk<(Int?, Int?, () -> Unit) -> Unit>(relaxed = true)

        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState(person = person, personId = "p1"))
        every { medicationViewModel.uiState } returns MutableStateFlow(PersonMedicationUiState(
            personId = "p1",
            allRecords = persistentListOf(
                jp.mydns.fujiwara.carememo.data.MedicationRecord(
                    id = "m1",
                    personId = "p1",
                    dosageDate = "2026-08-17",
                    timeSlot = 0,
                    status = 2, // TAKEN (Entity.ktの定義を確認する必要があるが、一旦数値で代用するか、importを直す)
                    recordTime = java.time.Instant.now()
                )
            )
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onRequireAuthentication = onRequireAuthentication
                )
            }
        }

        // 1. PDFボタンをタップしてダイアログを表示
        composeTestRule.onNodeWithTag("MedicationScreen_PdfButton").performClick()

        // 2. パスワードを入力
        composeTestRule.onNode(hasSetTextAction() and hasAnyChild(hasText("PDF閲覧用パスワード", substring = true)), useUnmergedTree = true).performTextInput("123456")

        // 3. ダイアログ内の「PDFを作成」ボタンをタップ
        composeTestRule.onNodeWithText("PDFを作成").performClick()

        // 4. 認証要求が正しいパラメータで呼ばれたか検証
        verify(timeout = 5000) {
            onRequireAuthentication(
                jp.mydns.fujiwara.carememo.R.string.security_auth_title,
                jp.mydns.fujiwara.carememo.R.string.security_auth_reason_pdf_export,
                any()
            )
        }
    }

    //endregion

    // --- Helpers ---

    private fun createMockDetailViewModel(): PersonDetailUiStateViewModel {
        return mockk<PersonDetailUiStateViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonDetailUiState(personId = "p1"))
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun createMockMedicationViewModel(): PersonMedicationViewModel {
        return mockk<PersonMedicationViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonMedicationUiState(personId = "p1"))
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        medicationState: PersonMedicationUiState = PersonMedicationUiState(personId = "p1")
    ) {
        val detailViewModel = createMockDetailViewModel()
        val medicationViewModel = createMockMedicationViewModel()

        every { medicationViewModel.uiState } returns MutableStateFlow(medicationState)

        composeTestRule.setContent {
            CareMemoTheme {
                PersonMedicationScreen(
                    detailViewModel = detailViewModel,
                    medicationViewModel = medicationViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
