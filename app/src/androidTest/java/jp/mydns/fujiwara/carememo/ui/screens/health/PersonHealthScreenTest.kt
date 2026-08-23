package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: PersonHealthScreen (SCR-PH-001)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-001_PersonHealthScreen.md に準拠
 */
class PersonHealthScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    //region 2. Adaptive Layout 検証 (Adaptive)

    @Test
    fun ADP_01_phoneLayout_isUsed_onCompactWidth() {
        setContent(widthClass = WindowWidthSizeClass.Compact)
        composeTestRule.onNodeWithTag("HealthScreen_PhoneContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_TabletContent").assertDoesNotExist()
    }

    @Test
    fun ADP_02_tabletLayout_isUsed_onExpandedWidth() {
        setContent(widthClass = WindowWidthSizeClass.Expanded)
        composeTestRule.onNodeWithTag("HealthScreen_TabletContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_PhoneContent").assertDoesNotExist()
    }

    //endregion

    //region 3. コンポーネント描画検証 (Components)

    @Test
    fun CPN_01_historyList_rendersItems() {
        val record = HeightAndWeight(id = "h1", personId = "p1", height = 170.0, weight = 65.0, recordTime = Instant.now())
        setContent(
            healthState = PersonHealthUiState(
                currentCategory = Category.HEIGHT_AND_WEIGHT,
                records = listOf(record).toImmutableList(),
                preferredShowHistory = true
            )
        )
        composeTestRule.onNodeWithTag("HealthScreen_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithText("170.0", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("65.0", substring = true).assertIsDisplayed()
    }

    @Test
    fun CPN_03_emptyState_isDisplayed_whenNoRecords() {
        setContent(
            healthState = PersonHealthUiState(records = emptyList<jp.mydns.fujiwara.carememo.data.HistoryRecord>().toImmutableList(), preferredShowHistory = true)
        )
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    //endregion

    //region 4. 状態・インタラクション検証 (Interaction)

    @Test
    fun ACT_02_modeSwitch_triggersViewModel() {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        // Add a dummy record to avoid EmptyState, which hides the segmented buttons
        val record = HeightAndWeight(id = "h1", personId = "p1", height = 170.0, weight = 60.0, recordTime = Instant.now())
        
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(
            preferredShowHistory = true,
            records = persistentListOf(record)
        ))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // Tap the graph tab/segmented button
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        verify { healthViewModel.updatePreferredShowHistory(false) }
    }

    @Test
    fun ACT_04_saveButton_triggersViewModel() {
        val newId = AppSpecifications.Id.NEW_RECORD_ID
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        // Set state to editing with valid input
        every { healthViewModel.uiState } returns MutableStateFlow(
            PersonHealthUiState(selectedRecordId = newId, isEditing = true, isSaveEnabled = true)
        )

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        // The tag is "HealthField_SaveButton" in PersonHealthComponents.kt
        composeTestRule.onNodeWithTag("HealthField_SaveButton").performClick()
        verify { healthViewModel.saveCurrentEdit() }
    }

    //endregion

    //region 5. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_02_backButton_navigatesBack() {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        
        // 確実に back が呼ばれる状態にする (編集モードではない状態)
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(selectedRecordId = null))

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }

        composeTestRule.onNodeWithTag("HealthScreen_BackButton").performClick()
        composeTestRule.waitForIdle() // UIスレッドの待機
        verify { detailViewModel.navigateBackToMain() }
    }

    //endregion

    //region 6. セキュリティ検証 (Security)

    @Test
    fun SEC_01_pdfExport_requiresAuthentication() {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()
        val person = jp.mydns.fujiwara.carememo.data.Person(
            id = "p1",
            lastName = "Test",
            firstName = "User",
            lastNameFurigana = "てすと",
            firstNameFurigana = "ゆーざー",
            birthday = Instant.EPOCH
        )
        val onRequireAuthentication = mockk<(Int?, Int?, () -> Unit) -> Unit>(relaxed = true)

        // PDFダイアログが表示される状態にする
        every { detailViewModel.uiState } returns MutableStateFlow(PersonDetailUiState(person = person))
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(records = persistentListOf()))

        composeTestRule.setContent {
            CareMemoTheme {
                // state variable showPdfSettingsDialog is internal to PersonHealthScreen, 
                // but we can trigger it by clicking the PDF button if we provide a mock that handles it.
                // However, the simplest way is to test PdfExportActionHandler directly if it's exported,
                // but here we test via Screen.
                
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onRequireAuthentication = onRequireAuthentication
                )
            }
        }

        // 1. PDFボタンをタップしてダイアログを表示
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").performClick()

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
            every { uiState } returns MutableStateFlow(PersonDetailUiState())
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun createMockHealthViewModel(): PersonHealthViewModel {
        return mockk<PersonHealthViewModel>(relaxed = true).apply {
            every { uiState } returns MutableStateFlow(PersonHealthUiState())
            every { isNameMaskingEnabled } returns MutableStateFlow(false)
            every { defaultRecorderName } returns MutableStateFlow("")
            every { viewEvent } returns MutableSharedFlow()
            every { uiEventFlow } returns MutableSharedFlow()
        }
    }

    private fun setContent(
        widthClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        detailState: PersonDetailUiState = PersonDetailUiState(),
        healthState: PersonHealthUiState = PersonHealthUiState()
    ) {
        val detailViewModel = createMockDetailViewModel()
        val healthViewModel = createMockHealthViewModel()

        every { detailViewModel.uiState } returns MutableStateFlow(detailState)
        every { healthViewModel.uiState } returns MutableStateFlow(healthState)

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    detailViewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    navController = mockk(relaxed = true),
                    widthSizeClass = widthClass
                )
            }
        }
    }
}
