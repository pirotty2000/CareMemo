package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
                onDone = { focusManager.clearFocus() }
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
