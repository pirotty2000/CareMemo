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
            CompactTextField(
                value = year,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 4) {
                        onYearChange(filtered)
                        if (filtered.length == 4) monthFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1.3f),
                onFocusChanged = { if (it.isFocused) onYearChange("") },
                suffix = { Text("年", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { monthFocusRequester.requestFocus() })
            )
            CompactTextField(
                value = month,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) {
                        onMonthChange(filtered)
                        if (filtered.length == 2) dayFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(monthFocusRequester),
                onFocusChanged = { if (it.isFocused) onMonthChange("") },
                suffix = { Text("月", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { dayFocusRequester.requestFocus() })
            )
            CompactTextField(
                value = day,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) {
                        onDayChange(filtered)
                        if (filtered.length == 2) hourFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(dayFocusRequester),
                onFocusChanged = { if (it.isFocused) onDayChange("") },
                suffix = { Text("日", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { hourFocusRequester.requestFocus() })
            )
            CompactTextField(
                value = hour,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) {
                        onHourChange(filtered)
                        if (filtered.length == 2) minuteFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(hourFocusRequester),
                onFocusChanged = { if (it.isFocused) onHourChange("") },
                suffix = { Text("時", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { minuteFocusRequester.requestFocus() })
            )
            CompactTextField(
                value = minute,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    if (filtered.length <= 2) onMinuteChange(filtered)
                },
                modifier = Modifier.weight(1f).focusRequester(minuteFocusRequester),
                onFocusChanged = { if (it.isFocused) onMinuteChange("") },
                suffix = { Text("分", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }
    }
}
