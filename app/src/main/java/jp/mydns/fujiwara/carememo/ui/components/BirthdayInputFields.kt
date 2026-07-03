package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 生年月日の元号定義
 */
enum class BirthEra(val displayNameRes: Int) {
    AD(R.string.era_ad),
    SHOWA(R.string.era_showa),
    HEISEI(R.string.era_heisei),
    REIWA(R.string.era_reiwa)
}

/**
 * 生年月日入力のステート管理クラス
 */
class BirthdayInputState(
    val era: MutableState<BirthEra>,
    val year: MutableState<String>,
    val month: MutableState<String>,
    val day: MutableState<String>,
    val yearFocusRequester: FocusRequester = FocusRequester(),
    val monthFocusRequester: FocusRequester = FocusRequester(),
    val dayFocusRequester: FocusRequester = FocusRequester(),
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
            LocalDate.of(westernYear, m, d)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
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

    val isValid: Boolean
        get() = year.value.isNotBlank() && month.value.isNotBlank() && day.value.isNotBlank() &&
                !isYearError && !isMonthError && !isDayError
}

@Composable
fun rememberBirthdayInputState(initialInstant: Instant? = null): BirthdayInputState {
    val initialDate = initialInstant?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()

    val initialEra = remember(initialInstant) {
        when {
            initialDate.year in 1926..1989 -> BirthEra.SHOWA
            initialDate.year in 1990..2019 -> BirthEra.HEISEI
            initialDate.year >= 2020 -> BirthEra.REIWA
            else -> BirthEra.AD
        }
    }

    val era = rememberSaveable(initialInstant) { mutableStateOf(initialEra) }

    val initialYearText = remember(initialInstant) {
        val y = initialDate.year
        when (initialEra) {
            BirthEra.SHOWA -> (y - 1925).toString()
            BirthEra.HEISEI -> (y - 1988).toString()
            BirthEra.REIWA -> (y - 2018).toString()
            BirthEra.AD -> y.toString()
        }
    }

    val year = rememberSaveable(initialInstant) { mutableStateOf(initialYearText) }
    val month = rememberSaveable(initialInstant) { mutableStateOf(initialDate.monthValue.toString()) }
    val day = rememberSaveable(initialInstant) { mutableStateOf(initialDate.dayOfMonth.toString()) }

    return remember(initialInstant) {
        BirthdayInputState(era, year, month, day)
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
        Text(stringResource(R.string.birthday), style = MaterialTheme.typography.labelMedium)
        
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
                OutlinedTextField(
                    value = stringResource(state.era.value.displayNameRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
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
            CompactTextField(
                value = state.year.value,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    val maxLength = if (state.era.value == BirthEra.AD) 4 else 2
                    if (filtered.length <= maxLength) {
                        state.year.value = filtered
                        if (filtered.length == maxLength) state.monthFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(state.yearFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = state.isYearError,
                suffix = { Text(stringResource(R.string.year_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 月入力
            CompactTextField(
                value = state.month.value,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) {
                        state.month.value = filtered
                        if (filtered.length == 2) state.dayFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(state.monthFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = state.isMonthError,
                suffix = { Text(stringResource(R.string.month_suffix), style = MaterialTheme.typography.labelSmall) }
            )

            // 日入力
            CompactTextField(
                value = state.day.value,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) state.day.value = filtered
                },
                modifier = Modifier.weight(1f).focusRequester(state.dayFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = state.isDayError,
                suffix = { Text(stringResource(R.string.day_suffix), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
