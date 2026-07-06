package jp.mydns.fujiwara.carememo.ui.components.common

/**
 * Component：DateTimeInputFields
 *
 * 【役割】：
 * 記録日時（年月日時分）を入力するためのUIコンポーネントと、その状態管理を提供する。
 *
 * 【主な機能】：
 * ・年、月、日、時、分の各数値入力フィールドの提供（CompactTextField を利用）。
 * ・入力完了時や最大桁数到達時の自動フォーカス移動。
 * ・入力値のバリデーションと、Instant への変換ロジック。
 * ・新規記録時や編集時における初期日時の柔軟な設定。
 *
 * 【想定する利用場所】：
 * 健康記録、所見メモ、一括入力画面などの記録日時設定箇所。
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.main.CompactTextField
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
) {
    /**
     * 入力値から Instant を生成する。不正な入力の場合は null を返す。
     * 年・月・日は必須。時・分が未入力（空文字）の場合は 00 として扱う。
     */
    fun toInstant(): Instant? {
        val y = year.value.toIntOrNull() ?: return null
        val m = month.value.toIntOrNull() ?: return null
        val d = day.value.toIntOrNull() ?: return null

        // 時・分は空文字の場合 00 とみなす。数値変換できない場合は null。
        val h = if (hour.value.isBlank()) 0 else hour.value.toIntOrNull() ?: return null
        val min = if (minute.value.isBlank()) 0 else minute.value.toIntOrNull() ?: return null

        return try {
            ZonedDateTime.of(
                y, m, d, h, min, 0, 0,
                ZoneId.systemDefault()
            ).toInstant()
        } catch (_: Exception) {
            // 日付の妥当性（例：2月30日など）で失敗した場合は null を返す
            null
        }
    }
}

/**
 * DateTimeInputState を生成・保持する Composable
 */
@Composable
fun rememberDateTimeInputState(initialInstant: Instant? = null): DateTimeInputState {
    val zdt = (initialInstant ?: Instant.now()).atZone(ZoneId.systemDefault())
    
    val year = rememberSaveable(initialInstant) { mutableStateOf(zdt.year.toString()) }
    val month = rememberSaveable(initialInstant) { mutableStateOf(zdt.monthValue.toString()) }
    val day = rememberSaveable(initialInstant) { mutableStateOf(zdt.dayOfMonth.toString()) }
    val hour = rememberSaveable(initialInstant) { mutableStateOf("%02d".format(zdt.hour)) }
    val minute = rememberSaveable(initialInstant) { mutableStateOf("%02d".format(zdt.minute)) }

    return remember(initialInstant) {
        DateTimeInputState(year, month, day, hour, minute)
    }
}

@Composable
fun DateTimeInputFields(
    state: DateTimeInputState,
    autoFocusHour: Boolean = true
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
        autoFocusHour = autoFocusHour
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
    autoFocusHour: Boolean = true
) {
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
                modifier = Modifier.weight(1.3f)
            )
            DateTimeUnitField(
                value = month,
                onValueChange = onMonthChange,
                maxLength = 2,
                label = "月",
                modifier = Modifier.weight(1f)
            )
            DateTimeUnitField(
                value = day,
                onValueChange = onDayChange,
                maxLength = 2,
                label = "日",
                modifier = Modifier.weight(1f),
                imeAction = if (autoFocusHour) ImeAction.Next else ImeAction.Done
            )
            DateTimeUnitField(
                value = hour,
                onValueChange = onHourChange,
                maxLength = 2,
                label = "時",
                modifier = Modifier.weight(1f)
            )
            DateTimeUnitField(
                value = minute,
                onValueChange = onMinuteChange,
                maxLength = 2,
                label = "分",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Done
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
    imeAction: ImeAction = ImeAction.Next,
) {
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        type = AppTextFieldType.INTEGER,
        suffix = { Text(label, style = MaterialTheme.typography.bodySmall) },
        maxLength = maxLength,
        imeAction = imeAction
    )
}
