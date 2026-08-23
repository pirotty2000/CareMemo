package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.ui.components.base.AppTextFieldType
import jp.mydns.fujiwara.carememo.ui.components.base.AppCompactTextField
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Component：DateTimeInputFields
 *
 * 【役割】
 * 記録日時（年月日時分）を入力するためのUIコンポーネントと、その状態管理機能を提供します。
 *
 * 【主な機能】
 * ・年、月、日、時、分の各数値入力フィールドの提供（AppCompactTextField を利用）。
 * ・入力完了時や最大桁数到達時の自動フォーカス移動。
 * ・入力値のバリデーションと、Instant への変換ロジックの内包。
 * ・新規記録時や編集時における初期日時の柔軟な設定。
 *
 * 【想定する利用場所】
 * 健康記録、所見メモ、服薬記録、一括入力画面などの記録日時設定箇所。
 *
 * 【このコンポーネントでは行わないこと】
 * 秒単位の入力管理（アプリの仕様として分単位までを記録対象とするため）。
 */

/**
 * 全体像：日時入力部品（DateTimeInput）
 *
 * ■ DateTimeInputFields (コンテナ)
 * │
 * ├─ [1] DateTimeUnitField (年：4桁)
 * ├─ [2] DateTimeUnitField (月：2桁)
 * ├─ [3] DateTimeUnitField (日：2桁)
 * ├─ [4] DateTimeUnitField (時：2桁)
 * └─ [5] DateTimeUnitField (分：2桁)
 *      └─ 内部で AppCompactTextField (ui/components/base/AppCompactTextField.kt) を利用
 */

/**
 * 日時入力フィールドのステートを管理するクラス
 * 5つの入力フィールドの状態をカプセル化し、業務ロジック（Instant変換等）を提供します。
 */
class DateTimeInputState(
    val year: MutableState<String>,
    val month: MutableState<String>,
    val day: MutableState<String>,
    val hour: MutableState<String>,
    val minute: MutableState<String>,
) {
    /**
     * 現在の入力値から Instant を生成します。
     *
     * ・年・月・日は必須です。
     * ・時・分が未入力（空文字）の場合は 00 として扱います。
     * ・不正な日付（例：2月30日）や数値以外が含まれる場合は null を返します。
     *
     * @return 生成された Instant、または不正な入力時は null
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
            // 日付の妥当性チェックで失敗した場合は null を返す
            null
        }
    }
}

/**
 * DateTimeInputState を生成・保持する Composable
 *
 * @param initialInstant 初期日時（null の場合は現在日時が使用されます）
 * @return 保持された DateTimeInputState
 */
@Composable
fun rememberDateTimeInputState(initialInstant: Instant? = null): DateTimeInputState {
    // rememberSaveable の inputs には saveable な型（Long等）を渡す必要があるため変換する
    val key = initialInstant?.toEpochMilli()
    val zdt = (initialInstant ?: Instant.now()).atZone(ZoneId.systemDefault())
    
    // 画面回転やプロセス再生成に対応するため rememberSaveable を使用
    val year = rememberSaveable(key) { mutableStateOf(zdt.year.toString()) }
    val month = rememberSaveable(key) { mutableStateOf(zdt.monthValue.toString()) }
    val day = rememberSaveable(key) { mutableStateOf(zdt.dayOfMonth.toString()) }
    val hour = rememberSaveable(key) { mutableStateOf("%02d".format(zdt.hour)) }
    val minute = rememberSaveable(key) { mutableStateOf("%02d".format(zdt.minute)) }

    return remember(key) {
        DateTimeInputState(year, month, day, hour, minute)
    }
}

/**
 * 日時入力フィールドのセットを表示する Composable
 *
 * @param state 日時入力の状態管理オブジェクト
 * @param modifier 修飾子
 * @param autoFocusHour 日の入力完了後、自動で時へフォーカスを移すか（日付のみの入力時は false を推奨）
 */
@Composable
fun DateTimeInputFields(
    state: DateTimeInputState,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
        autoFocusHour = autoFocusHour
    )
}

/**
 * 日時入力フィールドのセットを表示する Composable（ステートレス版）
 */
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
    modifier: Modifier = Modifier,
    autoFocusHour: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.common_date_time_label),
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
                label = stringResource(R.string.common_year_suffix),
                modifier = Modifier.weight(1.3f).testTag("DateTimeUnit_Year")
            )
            DateTimeUnitField(
                value = month,
                onValueChange = onMonthChange,
                maxLength = 2,
                label = stringResource(R.string.common_month_suffix),
                modifier = Modifier.weight(1f).testTag("DateTimeUnit_Month")
            )
            DateTimeUnitField(
                value = day,
                onValueChange = onDayChange,
                maxLength = 2,
                label = stringResource(R.string.common_day_suffix),
                modifier = Modifier.weight(1f).testTag("DateTimeUnit_Day"),
                // 日付までで入力を止める場合は Done、時まで続ける場合は Next を指定
                imeAction = if (autoFocusHour) ImeAction.Next else ImeAction.Done
            )
            DateTimeUnitField(
                value = hour,
                onValueChange = onHourChange,
                maxLength = 2,
                label = stringResource(R.string.common_hour_suffix),
                modifier = Modifier.weight(1f).testTag("DateTimeUnit_Hour")
            )
            DateTimeUnitField(
                value = minute,
                onValueChange = onMinuteChange,
                maxLength = 2,
                label = stringResource(R.string.common_minute_suffix),
                modifier = Modifier.weight(1f).testTag("DateTimeUnit_Minute"),
                imeAction = ImeAction.Done
            )
        }
    }
}

/**
 * 各日時単位（年、月、日など）の入力用小型フィールド
 */
@Composable
private fun DateTimeUnitField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    AppCompactTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        type = AppTextFieldType.INTEGER,
        suffix = { Text(label, style = MaterialTheme.typography.bodySmall) },
        maxLength = maxLength,
        imeAction = imeAction
    )
}
