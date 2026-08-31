package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：BirthdayInputFields
 *
 * 【役割】
 * 利用者の生年月日（和暦・西暦対応）を入力するためのUIコンポーネントと、その状態管理機能を提供します。
 *
 * 【主な機能】
 * ・元号（西暦、昭和、平成、令和）の選択ドロップダウンの提供。
 * ・年、月、日の数値入力フィールド（AppCompactTextField）の提供。
 * ・入力値のバリデーション（元号ごとの最大年数チェック、存在しない日付のチェック等）。
 * ・入力完了時や最大桁数到達時の自動フォーカス移動。
 * ・入力値を Instant 形式へ変換するロジックの内包。
 *
 * 【想定する利用場所】
 * ・利用者登録・編集画面（PersonEditScreenContent）。
 *
 * 【このコンポーネントでは行わないこと】
 * ・氏名や性別など、生年月日以外の情報の管理。
 */

/**
 * 全体像：生年月日入力（Birthday Input）
 *
 * ■ PersonEditScreenContent (利用者登録・編集画面)
 * │
 * └─ [1] BirthdayInputFields (★本コンポーネント：コンテナ)
 *      ├─ ExposedDropdownMenuBox (元号選択：西暦、昭和、平成、令和)
 *      ├─ AppCompactTextField (年入力：ADなら4桁、和暦なら2桁)
 *      ├─ AppCompactTextField (月入力：2桁)
 *      └─ AppCompactTextField (日入力：2桁)
 *           └─ 内部で JapaneseDateLogic を使用した妥当性チェック
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.logic.common.BirthEra
import jp.mydns.fujiwara.carememo.logic.common.JapaneseDateLogic
import jp.mydns.fujiwara.carememo.ui.mapping.BirthEraDisplayMapper
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import java.time.Instant
import java.time.ZoneOffset

/**
 * 生年月日入力のステートを管理するクラス。
 * 和暦・西暦の選択状態と、それぞれの数値入力値を保持し、バリデーションを提供します。
 *
 * @property era 元号（西暦、昭和、平成、令和）の選択状態
 * @property year 年の入力文字列
 * @property month 月の入力文字列
 * @property day 日の入力文字列
 */
class BirthdayInputState(
    val era: MutableState<BirthEra>,
    val year: MutableState<String>,
    val month: MutableState<String>,
    val day: MutableState<String>,
) {
    /**
     * 入力内容を Instant に変換します。
     * 和暦の場合は西暦に変換した上で計算します。
     *
     * @return 変換後の Instant。入力が不完全または不正な日付（2月30日等）の場合は null。
     */
    fun toInstant(): Instant? {
        val y = year.value.toIntOrNull() ?: return null
        val m = month.value.toIntOrNull() ?: return null
        val d = day.value.toIntOrNull() ?: return null

        // JapaneseDateLogic を使用して物理的妥当性を含めて変換
        return JapaneseDateLogic.toLocalDate(era.value, y, m, d)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
    }

    /** 年の入力値が元号の範囲外、または数値でない場合に true */
    val isYearError: Boolean
        get() {
            val y = year.value.toIntOrNull() ?: return true
            return when (era.value) {
                BirthEra.SHOWA -> y !in 1..AppSpecifications.JapaneseCalendar.Era.Showa.MAX_YEAR
                BirthEra.HEISEI -> y !in 1..AppSpecifications.JapaneseCalendar.Era.Heisei.MAX_YEAR
                BirthEra.REIWA -> y !in 1..AppSpecifications.JapaneseCalendar.Era.Reiwa.MAX_YEAR
                BirthEra.AD -> y !in AppSpecifications.JapaneseCalendar.MIN_DATE.year..AppSpecifications.JapaneseCalendar.MAX_WESTERN_YEAR
            }
        }

    /** 月の入力値が 1..12 の範囲外、または数値でない場合に true */
    val isMonthError: Boolean
        get() {
            val m = month.value.toIntOrNull() ?: return true
            return m !in 1..12
        }

    /** 日の入力値が暦の上で存在しない場合（例: 2月30日）、または数値でない場合に true */
    val isDayError: Boolean
        get() {
            val yInput = year.value.toIntOrNull() ?: return true
            val m = month.value.toIntOrNull() ?: return true
            val d = day.value.toIntOrNull() ?: return true
            
            // LocalDate への変換（および物理的妥当性チェック）は JapaneseDateLogic に集約
            val date = JapaneseDateLogic.toLocalDate(era.value, yInput, m, d)
            return date == null
        }
}

/**
 * 生年月日入力フィールド群を表示する Composable。
 * 元号選択ドロップダウンと、年・月・日の数値フィールドをレイアウトします。
 *
 * @param state 入力状態を管理する BirthdayInputState
 * @param modifier 修飾子
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayInputFields(
    state: BirthdayInputState,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    onFocusChanged: (String, Boolean) -> Unit = { _, _ -> }
) {
    var eraExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.main_label_birthday),
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // --- 元号選択ドロップダウン ---
            ExposedDropdownMenuBox(
                expanded = eraExpanded,
                onExpandedChange = { eraExpanded = !eraExpanded },
                modifier = Modifier.weight(1.2f)
            ) {
                AppCompactTextField(
                    value = stringResource(BirthEraDisplayMapper.getDisplayNameRes(state.era.value)),
                    onValueChange = {},
                    readOnly = true,
                    isError = isError,
                    suffix = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("PersonEdit_EraSelector")
                )
                ExposedDropdownMenu(
                    expanded = eraExpanded,
                    onDismissRequest = { eraExpanded = false }
                ) {
                    BirthEra.entries.forEach { e ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = stringResource(BirthEraDisplayMapper.getDisplayNameRes(e)),
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            modifier = Modifier.testTag("EraItem_${e.name}"),
                            onClick = {
                                state.era.value = e
                                eraExpanded = false
                            }
                        )
                    }
                }
            }

            // --- 年入力フィールド ---
            AppCompactTextField(
                value = state.year.value,
                onValueChange = { state.year.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthYear"),
                type = AppTextFieldType.INTEGER,
                maxLength = if (state.era.value == BirthEra.AD) 4 else 2,
                isError = isError || state.isYearError,
                onFocusChanged = { if (!it.isFocused) onFocusChanged("year", false) },
                suffix = { Text(stringResource(R.string.common_year_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // --- 月入力フィールド ---
            AppCompactTextField(
                value = state.month.value,
                onValueChange = { state.month.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthMonth"),
                type = AppTextFieldType.INTEGER,
                maxLength = 2,
                isError = isError || state.isMonthError,
                onFocusChanged = { if (!it.isFocused) onFocusChanged("month", false) },
                suffix = { Text(stringResource(R.string.common_month_suffix), style = MaterialTheme.typography.labelSmall) }
            )

            // --- 日入力フィールド ---
            AppCompactTextField(
                value = state.day.value,
                onValueChange = { state.day.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthDay"),
                type = AppTextFieldType.INTEGER,
                maxLength = 2,
                isError = isError || state.isDayError,
                onFocusChanged = { if (!it.isFocused) onFocusChanged("day", false) },
                suffix = { Text(stringResource(R.string.common_day_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }
        
        // 全体のエラーメッセージを表示
        if (isError && supportingText != null) {
            Box(modifier = Modifier.padding(start = 4.dp)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                ) {
                    supportingText()
                }
            }
        }
    }
}
