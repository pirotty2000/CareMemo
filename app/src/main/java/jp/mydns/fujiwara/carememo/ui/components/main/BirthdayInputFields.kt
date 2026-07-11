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
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 生年月日の元号定義
 */
enum class BirthEra(val displayNameRes: Int) {
    AD(R.string.common_era_ad),
    SHOWA(R.string.common_era_showa),
    HEISEI(R.string.common_era_heisei),
    REIWA(R.string.common_era_reiwa)
}

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
        val yInput = year.value.toIntOrNull() ?: return null
        val m = month.value.toIntOrNull() ?: return null
        val d = day.value.toIntOrNull() ?: return null

        val westernYear = when (era.value) {
            BirthEra.SHOWA -> yInput + 1925
            BirthEra.HEISEI -> yInput + 1988
            BirthEra.REIWA -> yInput + 2018
            BirthEra.AD -> yInput
        }

        return try {
            val instant = LocalDate.of(westernYear, m, d)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
            DateTimeUtils.normalizeBirthday(instant)
        } catch (_: Exception) {
            null
        }
    }

    val isYearError: Boolean
        get() {
            val y = year.value.toIntOrNull() ?: return true
            return when (era.value) {
                BirthEra.SHOWA -> y !in 1..64
                BirthEra.HEISEI -> y !in 1..31
                BirthEra.REIWA -> y !in 1..99
                BirthEra.AD -> y !in 1900..2100
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
            val westernYear = when (era.value) {
                BirthEra.SHOWA -> yInput + 1925
                BirthEra.HEISEI -> yInput + 1988
                BirthEra.REIWA -> yInput + 2018
                BirthEra.AD -> yInput
            }
            return try {
                d < 1 || d > YearMonth.of(westernYear, m).lengthOfMonth()
            } catch (_: Exception) {
                true
            }
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
                    value = stringResource(state.era.value.displayNameRes),
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
                            text = { Text(stringResource(e.displayNameRes), style = MaterialTheme.typography.bodyMedium) },
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
