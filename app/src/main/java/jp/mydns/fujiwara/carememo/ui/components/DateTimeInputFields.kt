package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 日時入力フィールドのステートを管理するクラス
 */
class DateTimeInputState(
    val year: MutableState<String>,
    val month: MutableState<String>,
    val day: MutableState<String>,
    val hour: MutableState<String>,
    val minute: MutableState<String>,
    val monthFocusRequester: FocusRequester = FocusRequester(),
    val dayFocusRequester: FocusRequester = FocusRequester(),
    val hourFocusRequester: FocusRequester = FocusRequester(),
    val minuteFocusRequester: FocusRequester = FocusRequester(),
) {
    /**
     * 入力値から Instant を生成する。不正な入力の場合は null を返す。
     */
    fun toInstant(): Instant? {
        return try {
            ZonedDateTime.of(
                year.value.toInt(), month.value.toInt(), day.value.toInt(),
                hour.value.toInt(), minute.value.toInt(), 0, 0,
                ZoneId.systemDefault()
            ).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 指定された Instant で各フィールドの値を更新する
     */
    fun setFromInstant(instant: Instant) {
        val zdt = instant.atZone(ZoneId.systemDefault())
        year.value = zdt.year.toString()
        month.value = zdt.monthValue.toString()
        day.value = zdt.dayOfMonth.toString()
        hour.value = "%02d".format(zdt.hour)
        minute.value = "%02d".format(zdt.minute)
    }
}

/**
 * DateTimeInputState を生成・保持する Composable
 */
@Composable
fun rememberDateTimeInputState(initialInstant: Instant? = null): DateTimeInputState {
    val zdt = (initialInstant ?: Instant.now()).atZone(ZoneId.systemDefault())
    
    val year = rememberSaveable { mutableStateOf(zdt.year.toString()) }
    val month = rememberSaveable { mutableStateOf(zdt.monthValue.toString()) }
    val day = rememberSaveable { mutableStateOf(zdt.dayOfMonth.toString()) }
    val hour = rememberSaveable { mutableStateOf("%02d".format(zdt.hour)) }
    val minute = rememberSaveable { mutableStateOf("%02d".format(zdt.minute)) }

    return remember {
        DateTimeInputState(year, month, day, hour, minute)
    }
}

@Composable
fun DateTimeInputFields(
    state: DateTimeInputState
) {
    DateTimeInputFields(
        year = state.year.value,
        onYearChange = { state.year.value = it },
        month = state.month.value,
        onMonthChange = { state.month.value = it },
        day = state.day.value,
        onDayChange = { state.day.value = it },
        hour = state.hour.value,
        onHourChange = { state.hour.value = it },
        minute = state.minute.value,
        onMinuteChange = { state.minute.value = it },
        monthFocusRequester = state.monthFocusRequester,
        dayFocusRequester = state.dayFocusRequester,
        hourFocusRequester = state.hourFocusRequester,
        minuteFocusRequester = state.minuteFocusRequester
    )
}

@Composable
fun DateTimeInputFields(
    year: String,
    onYearChange: (String) -> Unit,
    month: String,
    onMonthChange: (String) -> Unit,
    day: String,
    onDayChange: (String) -> Unit,
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    monthFocusRequester: FocusRequester,
    dayFocusRequester: FocusRequester,
    hourFocusRequester: FocusRequester,
    minuteFocusRequester: FocusRequester
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "記録日時",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateTimeUnitField(
                value = year,
                onValueChange = onYearChange,
                maxLength = 4,
                label = "年",
                modifier = Modifier.weight(1.3f),
                nextFocusRequester = monthFocusRequester
            )
            DateTimeUnitField(
                value = month,
                onValueChange = onMonthChange,
                maxLength = 2,
                label = "月",
                modifier = Modifier.weight(1f).focusRequester(monthFocusRequester),
                nextFocusRequester = dayFocusRequester
            )
            DateTimeUnitField(
                value = day,
                onValueChange = onDayChange,
                maxLength = 2,
                label = "日",
                modifier = Modifier.weight(1f).focusRequester(dayFocusRequester),
                nextFocusRequester = hourFocusRequester
            )
            DateTimeUnitField(
                value = hour,
                onValueChange = onHourChange,
                maxLength = 2,
                label = "時",
                modifier = Modifier.weight(1f).focusRequester(hourFocusRequester),
                nextFocusRequester = minuteFocusRequester
            )
            DateTimeUnitField(
                value = minute,
                onValueChange = onMinuteChange,
                maxLength = 2,
                label = "分",
                modifier = Modifier.weight(1f).focusRequester(minuteFocusRequester),
                imeAction = ImeAction.Done,
                onDone = {
                    focusManager.clearFocus()
                }
            )
        }
    }
}

@Composable
private fun DateTimeUnitField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    label: String,
    modifier: Modifier = Modifier,
    nextFocusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    CompactTextField(
        value = value,
        onValueChange = {
            val filtered = it.filter { c -> c.isDigit() }
            if (filtered.length <= maxLength) {
                onValueChange(filtered)
                if (filtered.length == maxLength) nextFocusRequester?.requestFocus()
            }
        },
        modifier = modifier,
        onFocusChanged = { if (it.isFocused) onValueChange("") },
        suffix = { Text(label, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { nextFocusRequester?.requestFocus() },
            onDone = { onDone?.invoke() }
        )
    )
}
