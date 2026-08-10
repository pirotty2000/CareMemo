package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import io.mockk.*
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonDetailUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonHealthUiState
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Instrumented Test: GraphExpansionScreen (SCR-PH-003)
 * 
 * 仕様書: doc/test/screen/TEST_SPEC_SCR-PH-003_GraphExpansionScreen.md に準拠
 */
class GraphExpansionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val detailViewModel = mockk<PersonDetailUiStateViewModel>(relaxed = true)
    private val healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)
    private val navController = mockk<NavHostController>(relaxed = true)

    private val testPerson = Person(
        id = "u1", lastName = "山田", firstName = "太郎",
        lastNameFurigana = "", firstNameFurigana = "",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val mockRecords = listOf(
        BpAndPulse(id = "1", personId = "u1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
    )

    @Before
    fun setup() {
        every { detailViewModel.uiState } returns MutableStateFlow(
            PersonDetailUiState(person = testPerson, personId = "u1", currentCategory = Category.BP_AND_PULSE)
        )
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(personId = "u1"))
        every { healthViewModel.getHealthRecords(any()) } returns MutableStateFlow(mockRecords.toImmutableList())
        every { healthViewModel.viewEvent } returns MutableSharedFlow()
        every { healthViewModel.uiEventFlow } returns MutableSharedFlow()
    }

    private fun setContent(
        isLoading: Boolean = false,
        records: List<HistoryRecord> = mockRecords
    ) {
        every { healthViewModel.getHealthRecords(any()) } returns MutableStateFlow(records.toImmutableList())
        every { healthViewModel.uiState } returns MutableStateFlow(PersonHealthUiState(isLoading = isLoading, personId = "u1"))

        composeTestRule.setContent {
            val context = LocalContext.current
            // Use applicationContext to avoid orientation locking crashes in test environment
            CompositionLocalProvider(LocalContext provides context.applicationContext) {
                CareMemoTheme {
                    GraphExpansionScreen(
                        detailViewModel = detailViewModel,
                        healthViewModel = healthViewModel,
                        initialGraphIndex = 0,
                        navController = navController
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    //region 2. 表示テスト (Display)

    @Test
    fun DSP_01_basicLayout_isDisplayed() {
        setContent()
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GraphExpansion_GraphList").assertIsDisplayed()
    }

    @Test
    fun DSP_02_headerTitle_isCorrect() {
        setContent()
        // Should contain masked name and category name
        composeTestRule.onNodeWithTag("GraphExpansion_HeaderTitle")
            .assertTextContains("山田　太郎", substring = true)
            .assertTextContains("血圧・脈拍", substring = true)
    }

    @Test
    fun DSP_04_emptyState_isDisplayed() {
        setContent(records = emptyList())
        composeTestRule.onNodeWithTag("GraphExpansion_EmptyState").assertIsDisplayed()
        composeTestRule.onNodeWithText("記録がありません", substring = true).assertIsDisplayed()
    }

    @Test
    fun DSP_05_loadingIndicator_isDisplayed() {
        setContent(isLoading = true, records = emptyList())
        composeTestRule.onNodeWithTag("GraphExpansion_Loading").assertIsDisplayed()
    }

    //endregion

    //region 3. 操作・インタラクションテスト (Interaction)

    @Test
    fun ACT_01_graphList_isScrollable() {
        // Mock multiple graphs (Category.BP_AND_PULSE has 2 graphs: Vital and BodyTemp usually, 
        // depending on HealthChartHelper implementation)
        setContent()
        val list = composeTestRule.onNodeWithTag("GraphExpansion_GraphList")
        list.assert(hasScrollAction())
    }

    //endregion

    //region 4. ナビゲーション・副作用検証 (Navigation)

    @Test
    fun NAV_01_backButton_popsBackStack() {
        setContent()
        composeTestRule.onNodeWithTag("GraphExpansion_BackButton").performClick()
        verify { navController.popBackStack() }
    }

    //endregion
}
