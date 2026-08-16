package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI Component Test: DateTimeInputFields
 * 1文字入力ごとの整合性を検証し、ViewModel からの逆流によるリセットループを防止します。
 */
class DateTimeInputFieldsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * CMP-DT-01: インクリメンタル・タイピングの検証 (日)
     * 1文字入力した際に、勝手に "01" などに整形（逆流）されないことを確認。
     */
    @Test
    fun CMP_DT_01_IncrementalTyping_Day() {
        val initialInstant = Instant.parse("2026-08-12T22:46:00Z")
        // 端末のタイムゾーンでの「日」を計算（JSTなら 13 になる）
        val expectedInitialDay = initialInstant.atZone(java.time.ZoneId.systemDefault()).dayOfMonth.toString()
        
        composeTestRule.setContent {
            CareMemoTheme {
                val state = rememberDateTimeInputState(initialInstant)
                DateTimeInputFields(state = state)
            }
        }

        // 初期値の確認
        val dayField = composeTestRule.onNodeWithTag("DateTimeUnit_Day")
        dayField.assertTextContains(expectedInitialDay)

        // 1文字削除してから "1" を入力
        dayField.performTextReplacement("") 
        dayField.performTextInput("1")
        
        // 即座に "1" であることを検証（"01" などに勝手に補完されないこと）
        dayField.assertTextContains("1")

        // 2文字目を入力（"12" になることを確認）
        dayField.performTextInput("2")
        dayField.assertTextContains("12")
    }

    /**
     * CMP_DT_02: インクリメンタル・タイピングの検証 (月)
     */
    @Test
    fun CMP_DT_02_IncrementalTyping_Month() {
        val initialInstant = Instant.parse("2026-08-12T22:46:00Z")
        val expectedInitialMonth = initialInstant.atZone(java.time.ZoneId.systemDefault()).monthValue.toString()
        
        composeTestRule.setContent {
            CareMemoTheme {
                val state = rememberDateTimeInputState(initialInstant)
                DateTimeInputFields(state = state)
            }
        }

        val monthField = composeTestRule.onNodeWithTag("DateTimeUnit_Month")
        monthField.assertTextContains(expectedInitialMonth)

        monthField.performTextReplacement("")
        monthField.performTextInput("1")
        
        // 1 を入れた瞬間に 01 にならないことを確認
        monthField.assertTextContains("1")

        monthField.performTextInput("2")
        monthField.assertTextContains("12")
    }

    /**
     * CMP_DT_03: ステートレス版での外部更新競合の検証
     * ViewModel からの値の逆流を想定し、1文字入力が維持されるか確認します。
     */
    @Test
    fun CMP_DT_03_Stateless_IncrementalTyping_Day() {
        var dayValue by mutableStateOf("12")
        
        composeTestRule.setContent {
            CareMemoTheme {
                DateTimeInputFields(
                    year = "2026", onYearChange = {},
                    month = "8", onMonthChange = {},
                    day = dayValue,
                    onDayChange = { dayValue = it }, // 外部ステート（ViewModelを模倣）を更新
                    hour = "22", onHourChange = {},
                    minute = "46", onMinuteChange = {}
                )
            }
        }

        val dayField = composeTestRule.onNodeWithTag("DateTimeUnit_Day")
        
        // 1文字消去して "1" にする
        dayField.performTextReplacement("")
        dayField.performTextInput("1")
        
        // 外部ステート経由で値が戻ってきても "1" が維持されていること
        dayField.assertTextContains("1")
        
        // 続けて "2" を入力
        dayField.performTextInput("2")
        dayField.assertTextContains("12")
    }
}
