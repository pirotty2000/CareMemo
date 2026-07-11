@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import jp.mydns.fujiwara.carememo.viewmodel.BatchInputViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI層テスト：BatchInputScreen (健康記録一括入力)
 * 
 * 仕様書: doc/test/TEST_SPEC_UI_PersonHealth.md (BH-04)
 */
class BatchInputScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val mockPerson = Person(
        id = 1, 
        lastName = "山田", 
        firstName = "太郎", 
        lastNameFurigana = "ヤマダ", 
        firstNameFurigana = "タロウ", 
        birthday = Instant.now()
    )

    @Test
    fun bh04_duplicate_categories_show_error_dialog() {
        val viewModel = mockk<BatchInputViewModel>(relaxed = true)
        val uiEventFlow = MutableSharedFlow<BaseViewModel.UiEvent>()
        
        every { viewModel.uiEventFlow } returns uiEventFlow.asSharedFlow()
        every { viewModel.currentPerson } returns MutableStateFlow(mockPerson)
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.isNameMaskingEnabled } returns MutableStateFlow(false)
        every { viewModel.isSaving } returns MutableStateFlow(false)
        every { viewModel.isInputValid } returns MutableStateFlow(true)
        every { viewModel.recordTime } returns MutableStateFlow(Instant.now())
        
        // 各入力項目の初期値
        every { viewModel.height } returns MutableStateFlow("")
        every { viewModel.weight } returns MutableStateFlow("")
        every { viewModel.bpSystolic } returns MutableStateFlow("")
        every { viewModel.bpDiastolic } returns MutableStateFlow("")
        every { viewModel.sat } returns MutableStateFlow("")
        every { viewModel.pulse } returns MutableStateFlow("")
        every { viewModel.bodyTemperature } returns MutableStateFlow("")
        every { viewModel.glucose } returns MutableStateFlow("")
        every { viewModel.hba1c } returns MutableStateFlow("")

        composeTestRule.setContent {
            CareMemoTheme {
                BatchInputScreen(
                    viewModel = viewModel,
                    personId = 1,
                    onBack = {}
                )
            }
        }

        // 初期描画と LaunchedEffect の起動を待機
        composeTestRule.waitForIdle()

        // 重複カテゴリ（身長・体重）を含むエラーイベントを発生させる
        // カテゴリ名は ViewModel 内で "__RES__" プレフィックス付きで生成される想定
        val categoryNameRes = "__RES__${R.string.common_category_height_weight}"
        
        composeTestRule.runOnUiThread {
            runBlocking {
                uiEventFlow.emit(BaseViewModel.UiEvent.ShowErrorDialogRes(
                    R.string.common_error_title_save,
                    R.string.batch_err_duplicate_blocked,
                    listOf(categoryNameRes)
                ))
            }
        }

        // ダイアログが表示されるまで待機
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("保存エラー").fetchSemanticsNodes().isNotEmpty()
        }

        // ダイアログが表示され、置換されたカテゴリ名が含まれているか確認
        composeTestRule.onNodeWithText("保存エラー").assertIsDisplayed()
        
        // ダイアログ特有のメッセージ文言を検索して、一意に特定する
        // これにより、背景の「身長・体重」セクションタイトルとの混同を避ける
        composeTestRule.onNodeWithText("既に以下のデータが登録されています", substring = true).assertIsDisplayed()

        // 置換されたカテゴリ名（身長・体重）が含まれていることを、全ノードから確認（少なくとも一つあればOK）
        val expectedCategoryName = composeTestRule.activity.getString(R.string.common_category_height_weight)
        composeTestRule.onAllNodesWithText(expectedCategoryName, substring = true)
            .assertCountEquals(2) // 画面タイトルとダイアログ内の2箇所にあるはず
        
        composeTestRule.onNodeWithText("閉じる").assertIsDisplayed()
        composeTestRule.onNodeWithText("閉じる").performClick()
        composeTestRule.onNodeWithText("閉じる").assertDoesNotExist()
    }
}
