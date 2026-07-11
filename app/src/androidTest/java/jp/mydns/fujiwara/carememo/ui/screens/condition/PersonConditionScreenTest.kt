package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：PersonCondition (状態・所見記録)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_PersonCondition.md
 */
class PersonConditionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // テスト用の共通モックデータ
    private val mockPerson = Person(
        id = 1, 
        lastName = "山田", 
        firstName = "太郎", 
        lastNameFurigana = "ヤマダ", 
        firstNameFurigana = "タロウ", 
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    private val mockRecords = listOf(
        ConditionAtVisit(
            id = 1, 
            personId = 1, 
            title = "定期巡回", 
            condition = "顔色もよく、元気に過ごされています。", 
            author = "記録者A", 
            recordTime = Instant.now()
        )
    )

    private val mockPhotos = listOf(
        ConditionPhoto(
            id = 1, 
            conditionId = 1, 
            personId = 1,
            photoFileName = "photo1.jpg", 
            thumbnailFileName = "thumb1.jpg", 
            capturedAt = Instant.now(),
            caption = "表情"
        )
    )

    // ======================================================================================
    // 1. 詳細画面共通コンポーネント (Header / CategoryBar)
    // ======================================================================================

    @Test
    fun com01_to_03_header_components_displayed_and_work() {
        var backCalled = false
        var pdfSettingsCalled = false

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = mockPerson, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = emptyList(), isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = -1, onSelectedIdChange = {},
                    onBack = { backCalled = true }, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = { pdfSettingsCalled = true },
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // COM-01: 戻るボタン
        composeTestRule.onNodeWithTag("ConditionScreen_BackButton").assertIsDisplayed().performClick()
        assert(backCalled)

        // COM-02: 利用者情報 (氏名と年齢)
        composeTestRule.onNodeWithTag("PersonHeader_NameAndAge", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("山田", substring = true)
            .assertTextContains("歳", substring = true)

        // COM-03: PDF出力ボタン
        composeTestRule.onNodeWithTag("ConditionScreen_PdfButton").assertIsDisplayed().performClick()
        assert(pdfSettingsCalled)
    }

    @Test
    fun com04_categoryBar_navigation_works() {
        var navigatedCategory: Category? = null
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = emptyList(), isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = -1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = { navigatedCategory = it }, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // COM-04: カテゴリ選択バー
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE")
            .performScrollTo()
            .performClick()
        assert(navigatedCategory == Category.BP_AND_PULSE)
    }

    // ======================================================================================
    // 2. 個別コンポーネント単体テスト (PersonConditionScreenContent)
    // ======================================================================================

    @Test
    fun cp01_dateSelection_isDisplayed_withEra() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-01: 日付選択表示 (和暦付き)
        composeTestRule.onNodeWithText("(", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp02_memoInputArea_showsExistingMemo() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-02: メモ入力エリア (閲覧モード)
        composeTestRule.onNodeWithText("顔色もよく、元気に過ごされています。").assertIsDisplayed()
    }

    @Test
    fun cp03_photoList_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = mapOf(1 to true),
                    photos = mockPhotos, isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-03: 写真リスト表示
        composeTestRule.onNodeWithTag("Condition_PhotoList").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("表情").assertIsDisplayed()
    }

    @Test
    fun cp04_no_photo_state_works() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-04: 写真なし状態
        composeTestRule.onNodeWithText("写真はありません").assertIsDisplayed()
    }

    @Test
    fun cp05_tablet_two_column_layout() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenTablet(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onDeleteRecord = {}, onSaveRecord = { _, _ -> }, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }
        // CP-05: タブレット版 2カラム表示
        composeTestRule.onNodeWithTag("Condition_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Condition_DetailPane").assertIsDisplayed()
    }

    // ======================================================================================
    // 3. 画面全体の挙動・結合テスト (PersonConditionScreen)
    // ======================================================================================

    @Test
    fun bh01_save_memo_calls_callback() {
        var saveCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = emptyList(), isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = emptyMap(),
                    photos = emptyList(), isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", 
                    selectedId = 0, // 新規作成モード
                    onSelectedIdChange = {}, onBack = {}, onNavigateToCategory = {}, 
                    onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> }, 
                    onShowPdfSettings = {}, onDeleteRecord = {}, 
                    onSaveRecord = { _, _ -> saveCalled = true }, 
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // 記録者と所見メモを入力
        composeTestRule.onNodeWithText("所見メモ").performTextInput("元気です")
        
        // BH-01: メモの保存
        composeTestRule.onNodeWithTag("Condition_SaveButton").performClick()
        assert(saveCalled)
    }

    @Test
    fun bh02_expand_photo_calls_callback() {
        var expandCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    personCategorySummary = null, records = mockRecords, isLoading = false,
                    searchQuery = "", onSearchQueryChange = {}, conditionPhotoMap = mapOf(1 to true),
                    photos = mockPhotos, isProcessing = false, isAnyDialogOpen = false,
                    defaultRecorderName = "記録者", selectedId = 1, onSelectedIdChange = {},
                    onBack = {}, onNavigateToCategory = {}, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> expandCalled = true }, 
                    onShowPdfSettings = {}, onDeleteRecord = {}, onSaveRecord = { _, _ -> }, 
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        // BH-02: 写真の拡大表示
        // サムネイルをタップ
        composeTestRule.onNodeWithContentDescription("表情").performClick()
        assert(expandCalled)
    }

    @Test
    fun bh03_duplicate_date_shows_error_dialog() {
        val detailViewModel = mockk<PersonDetailViewModel>(relaxed = true)
        val conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)
        
        val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>()
        every { conditionViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()
        
        // StateFlows の初期値を設定
        every { conditionViewModel.filteredRecords } returns MutableStateFlow(emptyList())
        every { conditionViewModel.isLoading } returns MutableStateFlow(false)
        every { detailViewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { detailViewModel.personCategorySummary } returns MutableStateFlow(null)
        every { conditionViewModel.searchQuery } returns MutableStateFlow("")
        every { conditionViewModel.conditionPhotoMap } returns MutableStateFlow(emptyMap())
        every { conditionViewModel.currentConditionPhotos } returns MutableStateFlow(emptyList())
        every { conditionViewModel.isProcessing } returns MutableStateFlow(false)
        every { detailViewModel.defaultRecorderName } returns MutableStateFlow("テスト者")

        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreen(
                    viewModel = detailViewModel,
                    conditionViewModel = conditionViewModel,
                    personId = 1,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onBack = {},
                    onNavigateToCategory = {},
                    onNavigateToPhotoPreview = { _, _, _ -> },
                    onNavigateToFullScreen = { _, _ -> }
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

        // ダイアログが表示されるまで待機
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
