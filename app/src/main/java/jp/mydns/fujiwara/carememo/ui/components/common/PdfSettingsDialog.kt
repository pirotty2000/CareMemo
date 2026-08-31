package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.components.base.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PDFの抽出範囲を定義する列挙型
 */
enum class ExportRange(val displayNameRes: Int) {
    /** 全期間 */
    ALL(R.string.export_range_all),
    /** 最新の1件のみ（所見メモ用） */
    LATEST(R.string.export_range_latest),
    /** 直近1ヶ月 */
    ONE_MONTH(R.string.export_range_one_month),
    /** 直近3ヶ月 */
    THREE_MONTHS(R.string.export_range_three_months),
    /** 直近6ヶ月 */
    SIX_MONTHS(R.string.export_range_six_months),
    /** カスタム期間指定 */
    CUSTOM(R.string.export_range_custom)
}

/**
 * PDFの並び順を定義する列挙型
 */
enum class ExportOrder(val displayNameRes: Int) {
    /** 日付の新しい順（降順） */
    NEWEST_FIRST(R.string.export_order_newest),
    /** 日付の古い順（昇順） */
    OLDEST_FIRST(R.string.export_order_oldest)
}

/**
 * Component：PdfSettingsDialog
 *
 * 【役割】
 * PDFを出力する際の詳細設定（期間、並び順、セキュリティ等）をユーザーが指定するためのダイアログを提供します。
 *
 * 【主な機能】
 * ・抽出範囲の選択（全期間、直近、カスタム期間指定など）。
 * ・DatePicker によるカスタム期間（開始日・終了日）の指定。
 * ・並び順（昇順/降順）および写真を含めるかどうかの設定。
 * ・出力されるPDFに対するパスワード保護の設定（生体認証連携）。
 *
 * 【想定する利用場所】
 * 各カテゴリ（健康、所見、服薬）の詳細画面からのPDF出力時。
 *
 * 【このコンポーネントでは行わないこと】
 * 実際のPDF生成処理（PdfExporter および PdfExportActionHandler が担当）。
 */

/**
 * PDFを出力する際の詳細設定ダイアログを表示します。
 *
 * @param category 対象のカテゴリ
 * @param onDismiss ダイアログを閉じる際のコールバック
 * @param modifier 修飾子
 * @param onRequireAuthentication セキュリティ設定の変更やパスワード表示に際して認証が必要な場合に呼び出されるコールバック
 * @param onExport 設定完了後にPDF生成を開始するためのコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsDialog(
    category: Category,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    onExport: (ExportRange, ExportOrder, Instant?, Instant?, Boolean, String?) -> Unit,
) {
    var selectedRange by remember { mutableStateOf(ExportRange.ALL) }
    var selectedOrder by remember { mutableStateOf(ExportOrder.NEWEST_FIRST) }
    var includePhotos by remember { mutableStateOf(true) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // パスワード設定用 (デフォルトONで、保護を推奨)
    var protectWithPassword by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isPasswordValid = password.length >= AppSpecifications.Constraints.System.Security.MIN_PASSWORD_LENGTH

    // --- 日付選択ダイアログ（開始日） ---
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate ?: endDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDate = datePickerState.selectedDateMillis
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.decision))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    // --- 日付選択ダイアログ（終了日） ---
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDate = datePickerState.selectedDateMillis
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.decision))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("PdfSettingsDialog"),
        title = { Text(stringResource(R.string.pdf_settings_title, stringResource(category.displayNameRes))) },
        text = {
            AppDialogContent {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- セキュリティ（最上位に配置） ---
                    Text(stringResource(R.string.security), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = protectWithPassword,
                                role = Role.Switch,
                                onValueChange = { checked ->
                                    if (checked) {
                                        protectWithPassword = true
                                        isPasswordVisible = false
                                    } else {
                                        // OFFにする場合は認証を求める（誤操作による保護解除を防止）
                                        onRequireAuthentication(
                                            R.string.security_auth_title,
                                            R.string.security_auth_reason_change_settings
                                        ) {
                                            protectWithPassword = false
                                        }
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.protect_pdf_with_password))
                        Switch(
                            checked = protectWithPassword,
                            onCheckedChange = null // 行側の toggleable で制御
                        )
                    }

                    if (protectWithPassword) {
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            type = AppTextFieldType.PASSWORD,
                            label = { Text(stringResource(R.string.pdf_password_label)) },
                            placeholder = { Text(stringResource(R.string.pdf_password_placeholder)) },
                            supportingText = {
                                if (!isPasswordValid && password.isNotEmpty()) {
                                    Text(stringResource(R.string.pdf_password_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.pdf_password_hint))
                                }
                            },
                            isError = !isPasswordValid && password.isNotEmpty(),
                            onFocusChanged = { /* Touched 制御を入れるならここに ViewModel 連携が必要だが、Dialog はステートレスに近いので現状維持 */ },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (isPasswordVisible) {
                                        isPasswordVisible = false
                                    } else {
                                        // パスワードを表示する場合は認証を求める
                                        onRequireAuthentication(
                                            R.string.security_auth_title,
                                            R.string.security_auth_reason_show_password
                                        ) {
                                            isPasswordVisible = true
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 抽出範囲 ---
                    Text(stringResource(R.string.extract_range), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportRange.entries
                        .filter { range ->
                            // 「最新の1件のみ」は所見メモ以外では意味がないため除外
                            if (range == ExportRange.LATEST) {
                                category == Category.CONDITION_AT_VISIT
                            } else {
                                true
                            }
                        }
                        .forEach { range ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (range == selectedRange),
                                        onClick = {
                                            selectedRange = range
                                        },
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (range == selectedRange), onClick = { selectedRange = range })
                                Text(text = stringResource(range.displayNameRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    
                    // カスタム期間選択時の日付ボタン表示
                    if (selectedRange == ExportRange.CUSTOM) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(startDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: stringResource(R.string.start_date))
                            }
                            Text("〜")
                            OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                                Text(endDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yy/MM/dd")) } ?: stringResource(R.string.end_date))
                            }
                        }
                    }

                    // --- オプション（写真の有無など） ---
                    if (category.hasOption) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = includePhotos,
                                    role = Role.Switch,
                                    onValueChange = { includePhotos = it }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.include_photos))
                            Switch(
                                checked = includePhotos,
                                onCheckedChange = null
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // --- 並び順 ---
                    Text(stringResource(R.string.export_order), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExportOrder.entries.forEach { order ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (order == selectedOrder),
                                    onClick = {
                                        selectedOrder = order
                                    },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (order == selectedOrder), onClick = { selectedOrder = order })
                            Text(text = stringResource(order.displayNameRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppDialogConfirmButton(
                text = stringResource(R.string.create_pdf),
                type = AppDialogActionType.ACTION,
                onClick = {
                    onExport(
                        selectedRange,
                        selectedOrder,
                        startDate?.let { Instant.ofEpochMilli(it) },
                        endDate?.let { Instant.ofEpochMilli(it) },
                        includePhotos,
                        if (protectWithPassword) password else null
                    )
                },
                enabled = run {
                    // カスタム期間の場合は少なくとも片方の入力が必要
                    val isCustomRangeValid = if (selectedRange == ExportRange.CUSTOM) (startDate != null || endDate != null) else true
                    // パスワード保護時は桁数チェックを通過している必要がある
                    val isPasswordSetupValid = if (protectWithPassword) isPasswordValid else true
                    isCustomRangeValid && isPasswordSetupValid
                }
            )
        },
        dismissButton = {
            AppDialogDismissButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss
            )
        }
    )
}
