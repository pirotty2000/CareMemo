package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.ui.utils.PhoneNumberVisualTransformation
import jp.mydns.fujiwara.carememo.ui.components.base.*
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactEditViewModel
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactViewEvent

/**
 * UI Action：緊急連絡先編集画面におけるユーザー操作の集約定義
 */
sealed interface EmergencyContactEditUiAction {
    data class UpdateContact(val reducer: (EmergencyContact) -> EmergencyContact) : EmergencyContactEditUiAction
    data object SaveClick : EmergencyContactEditUiAction
    data object CancelClick : EmergencyContactEditUiAction
    data object ConfirmDiscard : EmergencyContactEditUiAction
    data object DismissDialog : EmergencyContactEditUiAction
}

/**
 * Screen：EmergencyContactEditScreen
 *
 * 【役割】
 * 緊急連絡先（SCR-M-004）の新規登録および既存情報の修正を行うための独立した画面です。
 * 施設種別（医師・家族等）、名称、電話番号、および優先順位の入力を担当します。
 *
 * 【主な機能】
 * ・入力フォーム：`AppTextField` およびドロップダウンを用いた連絡先情報の編集。
 * ・バリデーション連携：ViewModel からの `isValid` 状態に基づいた保存ボタンの制御。
 * ・書式整形：`PhoneNumberVisualTransformation` を用いた電話番号の読みやすい表示。
 * ・破棄保護：未保存での離脱時に `AppDialog` による変更破棄の確認。
 */
@Composable
fun EmergencyContactEditScreen(
    viewModel: EmergencyContactEditViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ViewModel からの画面遷移イベントを監視
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is EmergencyContactViewEvent.NavigateBack,
                is EmergencyContactViewEvent.SaveSuccess,
                is EmergencyContactViewEvent.DeleteSuccess -> {
                    navController.popBackStack()
                }
            }
        }
    }

    val handleAction: (EmergencyContactEditUiAction) -> Unit = remember(viewModel, navController) {
        { action ->
            when (action) {
                is EmergencyContactEditUiAction.UpdateContact -> {
                    viewModel.updateEditingContact(action.reducer)
                }
                EmergencyContactEditUiAction.SaveClick -> {
                    viewModel.saveContact()
                }
                EmergencyContactEditUiAction.CancelClick -> {
                    navController.popBackStack()
                }
                EmergencyContactEditUiAction.ConfirmDiscard -> {
                    navController.popBackStack()
                }
                EmergencyContactEditUiAction.DismissDialog -> {
                    // ダイアログを閉じる制御は Content 側で行う（または状態を ViewModel へ戻す）
                    // ここでは ViewModel にダイアログ状態がないため、Content 側のローカル状態で閉じる。
                    // もし ViewModel で管理するなら、ここで resetDiscardDialogRequest() 等を呼ぶ。
                }
            }
        }
    }

    EmergencyContactEditContent(
        uiState = uiState,
        onAction = handleAction,
        modifier = modifier,
    )
}

/**
 * 緊急連絡先編集のレイアウト本体 (Stateless)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactEditContent(
    uiState: EmergencyContactUiState,
    onAction: (EmergencyContactEditUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contact = uiState.editingContact ?: return
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 戻る操作の制御
    val handleBack = {
        if (uiState.isChanged) {
            showDiscardDialog = true
        } else {
            onAction(EmergencyContactEditUiAction.CancelClick)
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { 
                    Text(stringResource(if (IdLogic.isNew(contact.id)) R.string.medical_contact_add_title else R.string.medical_contact_edit_title)) 
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 種別選択
                ContactTypeDropdown(
                    selectedType = contact.contactType,
                    onAction = onAction,
                    modifier = Modifier.testTag("EmergencyContact_TypeDropdown")
                )

                // 施設名・事業所名・続柄
                AppTextField(
                    value = contact.facilityName,
                    onValueChange = { newValue ->
                        onAction(EmergencyContactEditUiAction.UpdateContact { it.copy(facilityName = newValue) })
                    },
                    label = { Text(stringResource(R.string.medical_contact_facility_label)) },
                    placeholder = { Text(stringResource(R.string.medical_contact_facility_placeholder)) },
                    maxLength = AppSpecifications.MedicalContact.Validation.MAX_LENGTH_FACILITY_NAME,
                    modifier = Modifier.fillMaxWidth().testTag("EmergencyContact_FacilityField")
                )

                // 担当者名・個人名
                AppTextField(
                    value = contact.personName ?: "",
                    onValueChange = { newValue ->
                        onAction(EmergencyContactEditUiAction.UpdateContact { it.copy(personName = newValue) })
                    },
                    label = { Text(stringResource(R.string.medical_contact_person_label)) },
                    placeholder = { Text(stringResource(R.string.medical_contact_person_placeholder)) },
                    maxLength = AppSpecifications.MedicalContact.Validation.MAX_LENGTH_PERSON_NAME,
                    modifier = Modifier.fillMaxWidth().testTag("EmergencyContact_PersonField")
                )

                // 電話番号
                var isPhoneFocused by remember { mutableStateOf(false) }
                AppTextField(
                    value = contact.phoneNumber ?: "",
                    onValueChange = { newValue ->
                        onAction(EmergencyContactEditUiAction.UpdateContact { it.copy(phoneNumber = newValue) })
                    },
                    type = AppTextFieldType.PHONE,
                    label = { Text(stringResource(R.string.medical_contact_phone_label)) },
                    placeholder = { Text(stringResource(R.string.medical_contact_phone_placeholder)) },
                    visualTransformation = if (isPhoneFocused) {
                        VisualTransformation.None
                    } else {
                        PhoneNumberVisualTransformation()
                    },
                    supportingText = { Text(stringResource(R.string.medical_contact_phone_note)) },
                    maxLength = AppSpecifications.MedicalContact.Validation.MAX_LENGTH_PHONE_NUMBER,
                    onFocusChanged = { isPhoneFocused = it.isFocused },
                    modifier = Modifier.fillMaxWidth().testTag("EmergencyContact_PhoneField")
                )

                // 優先度
                AppTextField(
                    value = contact.priority.toString(),
                    onValueChange = { newValue ->
                        val p = newValue.toIntOrNull() ?: AppSpecifications.MedicalContact.Validation.DEFAULT_PRIORITY
                        onAction(EmergencyContactEditUiAction.UpdateContact { it.copy(priority = p) })
                    },
                    type = AppTextFieldType.INTEGER,
                    label = { Text(stringResource(R.string.medical_contact_priority_label)) },
                    maxLength = 2,
                    modifier = Modifier.fillMaxWidth().testTag("EmergencyContact_PriorityField")
                )

                Spacer(modifier = Modifier.weight(1f))

                // 操作ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = handleBack,
                        modifier = Modifier.weight(1f).testTag("EmergencyContact_CancelButton")
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = { onAction(EmergencyContactEditUiAction.SaveClick) },
                        enabled = uiState.isValid,
                        modifier = Modifier.weight(1f).testTag("EmergencyContact_SaveButton")
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }

    // 破棄確認ダイアログ
    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            modifier = Modifier.testTag("EmergencyContact_DiscardDialog"),
            title = { Text(stringResource(R.string.common_confirm_discard_title)) },
            text = {
                AppDialogContent(text = stringResource(R.string.common_confirm_discard_message))
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = stringResource(R.string.common_discard),
                    onClick = {
                        showDiscardDialog = false
                        onAction(EmergencyContactEditUiAction.ConfirmDiscard)
                    },
                    type = AppDialogActionType.DELETE
                )
            },
            dismissButton = {
                AppDialogDismissButton(
                    text = stringResource(R.string.common_continue_editing),
                    onClick = { showDiscardDialog = false }
                )
            }
        )
    }
}

/**
 * 連絡先種別選択用のドロップダウン
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactTypeDropdown(
    selectedType: String,
    onAction: (EmergencyContactEditUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val types = AppSpecifications.MedicalContact.Types.ORDERED_TYPES

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = EmergencyContactMapping.getLabel(selectedType),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.medical_contact_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(EmergencyContactMapping.getLabel(type)) },
                    onClick = {
                        onAction(EmergencyContactEditUiAction.UpdateContact { it.copy(contactType = type) })
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun EmergencyContactEditContentPreview_New() {
    val contact = EmergencyContact(personId = "1", facilityName = "", contactType = "DOCTOR")
    CareMemoTheme {
        EmergencyContactEditContent(
            uiState = EmergencyContactUiState(editingContact = contact, initialContact = contact),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencyContactEditContentPreview_Edit() {
    val contact = EmergencyContact(
        personId = "1", 
        facilityName = "○○クリニック", 
        personName = "○○先生", 
        phoneNumber = "0311112222",
        contactType = "DOCTOR"
    )
    CareMemoTheme {
        EmergencyContactEditContent(
            uiState = EmergencyContactUiState(editingContact = contact, initialContact = contact),
            onAction = {}
        )
    }
}
