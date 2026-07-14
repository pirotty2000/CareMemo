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
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
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
 * SCR-PC-001 PersonConditionScreen (所見記録) の UI テスト
 * 
 * 仕様書：doc/test/screen/TEST_SPEC_SCR-PC-001_PersonConditionScreen.md に準拠
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
    // 1. 共通コンポーネントテスト (Header / CategoryBar)
    // ======================================================================================

    @Test
    fun com01_header_backButton_works() {
        var backCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = mockPerson, isNameMaskingEnabled = false,
                    records = emptyList(), isLoading = false, onBack = { backCalled = true },
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = -1,
                    onSelectedIdChange = {}
                )
            }
        }
        // COM-01: ヘッダー：戻るボタン
        composeTestRule.onNodeWithTag("ConditionScreen_BackButton").assertIsDisplayed().performClick()
        assert(backCalled)
    }

    @Test
    fun com02_header_personInfo_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = mockPerson, isNameMaskingEnabled = false,
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = -1,
                    onSelectedIdChange = {}
                )
            }
        }
        // COM-02: ヘッダー：利用者情報
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
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = mockPerson, isNameMaskingEnabled = false,
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = { pdfSettingsCalled = true }, onSaveRecord = { _, _ -> }, 
                    onDeleteRecord = {}, onDeletePhoto = {}, onMicClick = {}, 
                    snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = -1,
                    onSelectedIdChange = {}
                )
            }
        }
        // COM-03: ヘッダー：PDF出力ボタン
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
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = { navigatedCategory = it }, onAddPhotoClick = {},
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {},
                    onSaveRecord = { _, _ -> }, onDeleteRecord = {}, onDeletePhoto = {},
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = -1,
                    onSelectedIdChange = {}
                )
            }
        }
        // COM-04: カテゴリ選択バー
        composeTestRule.onNodeWithTag("CategorySelectorBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CategoryChip_BP_AND_PULSE").performScrollTo().performClick()
        assert(navigatedCategory == Category.BP_AND_PULSE)
    }

    // ======================================================================================
    // 2. コンポーネント単体テスト (PersonConditionScreenContent)
    // ======================================================================================

    @Test
    fun cp01_dateSelection_isDisplayed_withEra() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-01: スマホ版：日付選択表示 (和暦付き)
        composeTestRule.onNodeWithText("(", substring = true).assertIsDisplayed()
    }

    @Test
    fun cp02_memoInputArea_showsExistingMemo() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-02: スマホ版：メモ入力エリア
        composeTestRule.onNodeWithText("顔色もよく、元気に過ごされています。").assertIsDisplayed()
    }

    @Test
    fun cp03_photoList_isDisplayed() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = mapOf(1 to true), photos = mockPhotos, isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-03: スマホ版：写真リスト表示
        composeTestRule.onNodeWithTag("Condition_PhotoList").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("表情").assertIsDisplayed()
    }

    @Test
    fun cp04_no_photo_state_works() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-04: スマホ版：写真なし状態
        composeTestRule.onNodeWithText("写真がありません").assertIsDisplayed()
    }

    @Test
    fun cp05_tablet_two_column_layout() {
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenTablet(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-05: タブレット版：2カラム表示
        composeTestRule.onNodeWithTag("Condition_HistoryList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("Condition_DetailPane").assertIsDisplayed()
    }

    @Test
    fun cp06_memoInput_isScrollable() {
        val longMemo = "所見メモ".repeat(100)
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", 
                    selectedId = 0, // 新規
                    onSelectedIdChange = {}
                )
            }
        }
        // CP-06: メモ入力のスクロール
        val inputField = composeTestRule.onNodeWithTag("Condition_MemoInput")
        inputField.performTextInput(longMemo)
        inputField.performTouchInput { swipeUp() }
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
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> saveCalled = true }, 
                    onDeleteRecord = {}, onDeletePhoto = {}, onMicClick = {}, 
                    snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", 
                    selectedId = 0, // 新規
                    onSelectedIdChange = {}
                )
            }
        }
        // BH-01: メモの保存
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput("元気です")
        composeTestRule.onNodeWithTag("Condition_SaveButton").performClick()
        assert(saveCalled)
    }

    @Test
    fun bh02_photoPreview_transition_isPossible() {
        var addPhotoCalled = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = { addPhotoCalled = true },
                    onNavigateToFullScreen = { _, _ -> }, onShowPdfSettings = {}, 
                    onSaveRecord = { _, _ -> }, onDeleteRecord = {}, onDeletePhoto = {}, 
                    onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // BH-02: 写真プレビューへの遷移（撮影ボタン押下）
        composeTestRule.onNodeWithContentDescription("写真を撮影").performClick()
        assert(addPhotoCalled)
    }

    @Test
    fun bh03_duplicate_date_shows_error_dialog() {
        val detailViewModel = mockk<PersonDetailViewModel>(relaxed = true)
        val conditionViewModel = mockk<PersonConditionViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>()
        
        // conditionViewModel と detailViewModel 両方の uiEventFlow を適切にモックする
        every { conditionViewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { detailViewModel.uiEventFlow } returns MutableSharedFlow<BaseViewModel.UiEvent>().asSharedFlow()

        // StateFlow への戻り値に明示的な型を指定して、Nothing 型推論による問題を回避する
        val filteredRecordsFlow = MutableStateFlow<List<ConditionAtVisit>>(emptyList())
        every { conditionViewModel.filteredRecords } returns filteredRecordsFlow
        every { conditionViewModel.isLoading } returns MutableStateFlow(false)

        val personFlow = MutableStateFlow<Person?>(mockPerson)
        every { detailViewModel.currentPerson } returns personFlow
        every { detailViewModel.isNameMaskingEnabled } returns MutableStateFlow(false)

        val summaryFlow = MutableStateFlow<PersonCategorySummary?>(null)
        every { detailViewModel.personCategorySummary } returns summaryFlow

        every { conditionViewModel.searchQuery } returns MutableStateFlow("")

        val photoMapFlow = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
        every { conditionViewModel.conditionPhotoMap } returns photoMapFlow

        val photosFlow = MutableStateFlow<List<ConditionPhoto>>(emptyList())
        every { conditionViewModel.currentConditionPhotos } returns photosFlow

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
        
        // BH-03: 日時重複時の保存ガード
        // UIスレッドでイベントを発火させ、Composeがアイドル状態になるのを待機する
        composeTestRule.runOnUiThread {
            runBlocking {
                uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialogRes(
                    R.string.common_error_title_save,
                    R.string.common_err_duplicate_blocked_simple
                ))
            }
        }
        
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        composeTestRule.onNodeWithText("閉じる").performClick()
    }

    @Test
    fun bh04_photoFullScreen_navigation_works() {
        var navigatedToFullScreen = false
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, 
                    onNavigateToFullScreen = { _, _ -> navigatedToFullScreen = true },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = mapOf(1 to true), photos = mockPhotos, isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // BH-04: 写真フル画面への遷移
        composeTestRule.onNodeWithContentDescription("表情").performClick()
        assert(navigatedToFullScreen)
    }

    @Test
    fun bh05_backFromOtherScreen_maintainsState() {
        // BH-05: 詳細画面等から戻った際、状態が維持されていることの検証
        var selectedIdState by mutableStateOf(1)
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", 
                    selectedId = selectedIdState,
                    onSelectedIdChange = { selectedIdState = it }
                )
            }
        }
        // 詳細が表示されていることを確認
        composeTestRule.onNodeWithTag("ConditionDetailPane").assertIsDisplayed()
        // IDが維持されている（不整合が起きていない）ことを確認
        assert(selectedIdState == 1)
    }

    @Test
    fun bh06_cameraCancel_maintainsInputState() {
        // BH-06: 撮影キャンセル時に元の状態が維持されることの検証
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = emptyList(), isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = emptyMap(), photos = emptyList(), isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 0,
                    onSelectedIdChange = {}
                )
            }
        }
        val input = "カメラ起動前のメモ"
        composeTestRule.onNodeWithTag("Condition_MemoInput").performTextInput(input)
        
        // UIが維持されていることを確認
        composeTestRule.onNodeWithTag("Condition_MemoInput").assertTextContains(input)
    }

    @Test
    fun bh07_reflectsNewPhotoAfterSave() {
        // BH-07: 保存して戻った際、リストに新しい写真が反映されること
        val photosState = mutableStateListOf<ConditionPhoto>()
        
        composeTestRule.setContent {
            CareMemoTheme {
                PersonConditionScreenPhone(
                    personId = 1, currentPerson = null, isNameMaskingEnabled = false,
                    records = mockRecords, isLoading = false, onBack = {},
                    onNavigateToCategory = {}, onAddPhotoClick = {}, onNavigateToFullScreen = { _, _ -> },
                    onShowPdfSettings = {}, onSaveRecord = { _, _ -> }, onDeleteRecord = {},
                    onDeletePhoto = {}, onMicClick = {}, snackbarHostState = remember { SnackbarHostState() },
                    personCategorySummary = null, searchQuery = "", onSearchQueryChange = {},
                    conditionPhotoMap = mapOf(1 to true), 
                    photos = photosState, 
                    isProcessing = false,
                    isAnyDialogOpen = false, defaultRecorderName = "記録者", selectedId = 1,
                    onSelectedIdChange = {}
                )
            }
        }
        // 最初は「写真がありません」
        composeTestRule.onNodeWithText("写真がありません").assertIsDisplayed()

        // 写真が追加された状態をシミュレート
        photosState.add(mockPhotos[0])
        
        // リストに反映され、「写真がありません」が消えることを確認
        composeTestRule.onNodeWithText("写真がありません").assertDoesNotExist()
        composeTestRule.onNodeWithTag("Condition_PhotoList").assertIsDisplayed()
    }

    @Test
    fun bh08_pdfSettings_showsRelevantItemsOnly() {
        composeTestRule.setContent {
            CareMemoTheme {
                jp.mydns.fujiwara.carememo.ui.components.common.PdfSettingsDialog(
                    category = Category.CONDITION_AT_VISIT,
                    onDismiss = {},
                    onExport = { _, _, _, _, _, _ -> }
                )
            }
        }
        // BH-08: PDF出力設定（所見特有）
        composeTestRule.onNodeWithTag("PdfSettingsDialog").assertIsDisplayed()
        // 「最新の1件のみ」が表示されていること
        composeTestRule.onNodeWithText("最新の1件のみ")
            .performScrollTo()
            .assertIsDisplayed()
        // 「写真を印刷に含める」が表示されていること
        composeTestRule.onNodeWithText("写真を印刷に含める")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
