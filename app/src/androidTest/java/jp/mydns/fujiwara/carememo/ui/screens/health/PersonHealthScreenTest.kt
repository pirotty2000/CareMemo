package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonHealthViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonHealth (バイタル管理)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_PersonHealth.md
 */
class PersonHealthScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // テスト用の共通モックデータ
    private val mockRecords = listOf(
        jp.mydns.fujiwara.carememo.data.BpAndPulse(
            id = 1, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now()
        )
    )

    // ======================================================================================
    // 1. 詳細画面共通コンポーネント (Header / CategoryBar)
    // ======================================================================================

    @Test
    fun com01_header_backButton_works() {
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, selectedRecordId = -1,
                    onSelectedRecordIdChange = {}, onBack = { backCalled = true }, 
                    onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}, onShowPdfSettings = {}, 
                    onDeleteRecord = {}, onSaveRecord = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("HealthScreen_BackButton").assertIsDisplayed().performClick()
        assert(backCalled)
    }

    @Test
    fun com02_header_personInfo_isDisplayed() {
        val mockPerson = Person(
            id = 1, lastName = "山田", firstName = "太郎", lastNameFurigana = "ヤマダ", firstNameFurigana = "タロウ", 
            birthday = Instant.parse("1950-01-01T00:00:00Z")
        )
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = mockPerson, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, selectedRecordId = -1,
                    onSelectedRecordIdChange = {}, onBack = {}, onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDeleteRecord = {}, onSaveRecord = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("山田", substring = true)
            .assertTextContains("歳", substring = true)
    }

    @Test
    fun com03_header_pdfButton_works() {
        var pdfSettingsCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, selectedRecordId = -1,
                    onSelectedRecordIdChange = {}, onBack = {}, onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}, onShowPdfSettings = { pdfSettingsCalled = true }, 
                    onDeleteRecord = {}, onSaveRecord = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        composeTestRule.onNodeWithTag("HealthScreen_PdfButton").assertIsDisplayed().performClick()
        assert(pdfSettingsCalled)
    }

    @Test
    fun com04_categoryBar_navigation_works() {
        var navigatedCategory: Category? = null
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, selectedRecordId = -1,
                    onSelectedRecordIdChange = {}, onBack = {}, onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = { navigatedCategory = it }, onShowPdfSettings = {}, 
                    onDeleteRecord = {}, onSaveRecord = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // COM-04: カテゴリ選択バー
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
        // 他のカテゴリ（例：身長・体重）をタップ。確実に表示されるようスクロールを挟む
        composeTestRule.onNodeWithTag("CategoryChip_HEIGHT_AND_WEIGHT")
            .performScrollTo()
            .performClick()
        
        assert(navigatedCategory == Category.HEIGHT_AND_WEIGHT)
    }

    // ======================================================================================
    // 2. 個別コンポーネント単体テスト (PersonHealthScreenContent)
    // ======================================================================================

    @Test
    fun cp01_inputForm_isDisplayed_onSelection() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, 
                    selectedRecordId = 0, // 新規登録モード
                    onSelectedRecordIdChange = {}, onBack = {}, onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDeleteRecord = {}, onSaveRecord = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-01: 入力フォーム表示
        composeTestRule.onNodeWithTag("HealthScreen_InputForm").assertIsDisplayed()
        composeTestRule.onNodeWithText("保存").assertIsDisplayed()
    }

    @Test
    fun cp02_historyGraph_switch_works() {
        var isHistoryMode = true
        composeTestRule.setContent {
            CareMemoTheme {
                var currentShowHistory by remember { mutableStateOf(true) }
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, 
                    records = mockRecords, // EmptyStateを避けるためにデータを渡す
                    isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = currentShowHistory, 
                    onPreferredShowHistoryChange = { 
                        currentShowHistory = it
                        isHistoryMode = it 
                    }, 
                    selectedRecordId = -1, onSelectedRecordIdChange = {}, onBack = {}, 
                    onNavigateToGraphExpansion = { _, _, _ -> }, onNavigateToCategory = {}, onShowPdfSettings = {}, 
                    onDeleteRecord = {}, onSaveRecord = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // CP-02: 履歴/グラフ切り替え
        composeTestRule.onNodeWithTag("HealthScreen_HistoryGraphSwitch").assertIsDisplayed()
        
        // グラフへ切り替え
        composeTestRule.onNodeWithTag("HealthScreen_Tab_Graph").performClick()
        composeTestRule.waitForIdle()
        assert(!isHistoryMode)
        
        // 履歴へ切り替え
        composeTestRule.onNodeWithTag("HealthScreen_Tab_History").performClick()
        composeTestRule.waitForIdle()
        assert(isHistoryMode)
    }

    @Test
    fun cp03_graph_empty_state_shows_message() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = false, // グラフ表示モード
                    onPreferredShowHistoryChange = {}, selectedRecordId = -1, onSelectedRecordIdChange = {}, onBack = {}, 
                    onNavigateToGraphExpansion = { _, _, _ -> }, onNavigateToCategory = {}, onShowPdfSettings = {}, 
                    onDeleteRecord = {}, onSaveRecord = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-03: グラフ空状態
        // グラフエリア内の「記録がありません」が表示されていること
        composeTestRule.onNodeWithText("記録がありません").assertIsDisplayed()
    }

    @Test
    fun cp04_tablet_two_column_layout() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenTablet(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, 
                    records = mockRecords, // EmptyStateを避けるためにデータを渡す
                    isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    selectedRecordId = -1, onSelectedRecordIdChange = {}, onBack = {}, 
                    onNavigateToGraphExpansion = { _, _, _ -> }, onNavigateToCategory = {}, onShowPdfSettings = {}, 
                    onDeleteRecord = {}, onSaveRecord = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-04: タブレット版 2カラム表示
        composeTestRule.onNodeWithTag("HealthScreen_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HealthScreen_GraphArea").assertIsDisplayed()
    }

    // ======================================================================================
    // 3. 画面全体の挙動・結合テスト (PersonHealthScreen)
    // ======================================================================================

    @Test
    fun bh01_save_record_calls_callback() {
        var saveCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, records = emptyList(), isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = true, onPreferredShowHistoryChange = {}, 
                    selectedRecordId = 0, // 新規作成
                    onSelectedRecordIdChange = {}, onBack = {}, onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDeleteRecord = {}, 
                    onSaveRecord = { saveCalled = true },
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // BH-01: データ保存の動作
        // 1. 体温を入力して保存ボタンを有効化する
        composeTestRule.onNodeWithText("体温").performTextReplacement("36.5")
        
        // 2. 保存ボタンをタップ
        composeTestRule.onNodeWithText("保存").performClick()
        assert(saveCalled)
    }

    @Test
    fun bh02_expand_graph_calls_callback() {
        var expandCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreenPhone(
                    personId = 1, currentCategory = Category.BP_AND_PULSE, 
                    records = mockRecords, 
                    isLoading = false,
                    currentPerson = null, personCategorySummary = null, isNameMaskingEnabled = false,
                    preferredShowHistory = false, // グラフ表示
                    onPreferredShowHistoryChange = {}, selectedRecordId = -1, onSelectedRecordIdChange = {}, onBack = {}, 
                    onNavigateToGraphExpansion = { _, _, _ -> expandCalled = true }, 
                    onNavigateToCategory = {}, onShowPdfSettings = {}, onDeleteRecord = {}, onSaveRecord = {}, 
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // BH-02: グラフ拡大遷移
        // グラフが複数あるため、「拡大表示」アイコンのうち最初の一つをタップする
        composeTestRule.onAllNodesWithContentDescription("拡大表示")
            .onFirst()
            .performClick()

        assert(expandCalled)
    }

    @Test
    fun bh03_duplicate_date_shows_error_dialog() {
        val detailViewModel = mockk<PersonDetailViewModel>(relaxed = true)
        val healthViewModel = mockk<PersonHealthViewModel>(relaxed = true)
        
        val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>()
        every { healthViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()
        
        // StateFlows の初期値を設定
        val mockPerson = Person(
            id = 1, 
            lastName = "山田", 
            firstName = "太郎", 
            lastNameFurigana = "ヤマダ", 
            firstNameFurigana = "タロウ", 
            birthday = Instant.now()
        )
        every { healthViewModel.records } returns MutableStateFlow(emptyList())
        every { healthViewModel.isLoading } returns MutableStateFlow(false)
        every { detailViewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null)

        composeTestRule.setContent {
            CareMemoTheme {
                PersonHealthScreen(
                    viewModel = detailViewModel,
                    healthViewModel = healthViewModel,
                    initialCategoryType = Category.BP_AND_PULSE,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToGraphExpansion = { _, _, _ -> },
                    onNavigateToCategory = {}
                )
            }
        }

        // 初期描画と LaunchedEffect の起動を待機
        composeTestRule.waitForIdle()

        // 重複エラーイベントを発生させる
        composeTestRule.runOnUiThread {
            runBlocking {
                uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialogRes(
                    R.string.common_error_title_save,
                    R.string.common_err_duplicate_blocked_simple
                ))
            }
        }

        // ダイアログが表示されるまで待機 (タイムアウト 5秒)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("保存エラー").fetchSemanticsNodes().isNotEmpty()
        }

        // ダイアログが表示されているか確認
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        composeTestRule.onNodeWithText("閉じる").assertIsDisplayed()
        
        // 閉じるボタンをタップしてダイアログが消えることを確認
        composeTestRule.onNodeWithText("閉じる").performClick()
        composeTestRule.onNodeWithText("閉じる").assertDoesNotExist()
    }
}
