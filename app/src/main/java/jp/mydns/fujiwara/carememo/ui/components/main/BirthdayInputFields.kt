package jp.mydns.fujiwara.carememo.ui.components.main

/**
 * Component：BirthdayInputFields
 *
 * 【役割】：
 * 利用者の生年月日を入力するためのUIコンポーネントと、その状態管理を提供する。
 *
 * 【主な機能】：
 * ・元号（西暦、昭和、平成、令和）の選択ドロップダウンの提供。
 * ・年、月、日の数値入力フィールドの提供（AppCompactTextField を利用）。
 * ・入力完了時や最大桁数到達時の自動フォーカス移動。
 * ・入力値のバリデーション（存在しない日付のチェック等）と、Instant への変換。
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
 * 生年月日入力のステート管理クラス
 */
class BirthdayInputState(
    val era: MutableState<BirthEra>,
    val year: MutableState<String>,
    val month: MutableState<String>,
    val day: MutableState<String>,
) {
    /**
     * 入力内容を Instant に変換。不正な場合は null。
     */
    fun toInstant(): Instant? {
        val y = year.value.toIntOrNull() ?: return null
        val m = month.value.toIntOrNull() ?: return null
        val d = day.value.toIntOrNull() ?: return null

        return JapaneseDateLogic.toLocalDate(era.value, y, m, d)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
    }

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

    val isMonthError: Boolean
        get() {
            val m = month.value.toIntOrNull() ?: return true
            return m !in 1..12
        }

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
 * 生年月日入力フィールド群
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayInputFields(
    state: BirthdayInputState,
    modifier: Modifier = Modifier
) {
    var eraExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.main_label_birthday), style = MaterialTheme.typography.labelMedium)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 元号選択
            ExposedDropdownMenuBox(
                expanded = eraExpanded,
                onExpandedChange = { eraExpanded = !eraExpanded },
                modifier = Modifier.weight(1f)
            ) {
                AppCompactTextField(
                    value = stringResource(BirthEraDisplayMapper.getDisplayNameRes(state.era.value)),
                    onValueChange = {},
                    readOnly = true,
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
                            text = { Text(stringResource(BirthEraDisplayMapper.getDisplayNameRes(e)), style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                state.era.value = e
                                eraExpanded = false
                            }
                        )
                    }
                }
            }

            // 年入力
            AppCompactTextField(
                value = state.year.value,
                onValueChange = { state.year.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthYear"),
                type = AppTextFieldType.INTEGER,
                maxLength = if (state.era.value == BirthEra.AD) 4 else 2,
                isError = state.isYearError,
                suffix = { Text(stringResource(R.string.common_year_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 月入力
            AppCompactTextField(
                value = state.month.value,
                onValueChange = { state.month.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthMonth"),
                type = AppTextFieldType.INTEGER,
                maxLength = 2,
                isError = state.isMonthError,
                suffix = { Text(stringResource(R.string.common_month_suffix), style = MaterialTheme.typography.labelSmall) }
            )

            // 日入力
            AppCompactTextField(
                value = state.day.value,
                onValueChange = { state.day.value = it },
                modifier = Modifier.weight(1f).testTag("PersonEdit_BirthDay"),
                type = AppTextFieldType.INTEGER,
                maxLength = 2,
                isError = state.isDayError,
                suffix = { Text(stringResource(R.string.common_day_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
