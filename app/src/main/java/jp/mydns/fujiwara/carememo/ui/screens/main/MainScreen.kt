package jp.mydns.fujiwara.carememo.ui.screens.main

/**
 * Screen : MainScreen
 *
 * 【画面名】：
 * 利用者一覧画面
 *
 * 【役割】：
 * 登録された利用者（ケア対象者）の一覧を表示し、各記録カテゴリへの橋渡しや、
 * 利用者情報の管理（登録・変更・サービス終了処理）を行うアプリのメインエントランス。
 *
 * 【主な機能】：
 * ・利用者一覧表示（名前のマスキング、年齢、最新記録状況のバッジ表示、誕生日通知）
 * ・絞り込み検索（五十音順インデックスおよび検索バーによるフリーワード検索）
 * ・利用者管理（論理削除とUndo機能）
 * ・カテゴリ遷移（利用者選択時のボトムシートから健康記録・所見メモ・服薬管理・一括入力へ遷移）
 *
 * 【遷移】：
 * → PersonHealthScreen (詳細画面：健康記録「身長・体重」「バイタル」「血糖値・HbA1c」)
 * → PersonConditionScreen (詳細画面：「所見メモ」)
 * → PersonMedicationScreen (詳細画面：「服薬管理」)
 * → BatchInputScreen (健康記録の一括入力)
 * → PersonEditScreen (利用者登録・編集)
 * → SettingsScreen (アプリ設定)
 *
 * 【使用するViewModel】：
 * PersonListViewModel
 *
 * 【備考】：
 * ViewModelとの接続、ナビゲーション、スナックバー／ダイアログのイベント制御を担当。
 * 実際のUIレイアウトは MainScreenContent.kt に委譲。
 *
 * ---
 * 最終更新日: 2026/07/20 (UUID対応)
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import kotlinx.coroutines.launch

/**
 * 利用者一覧画面のメインエントランス。
 * ViewModelとの接続、UI状態の監視、ダイアログやボトムシートの表示制御を行う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    onNavigateToDetail: (String, Category) -> Unit, // 各カテゴリ
    onNavigateToBatchInput: (String) -> Unit,       // 一括入力画面
    onNavigateToAddPerson: () -> Unit,              // 利用者の新規登録
    onNavigateToEditPerson: (String) -> Unit,       // 利用者の編集
    onNavigateToSettings: () -> Unit,               // 設定・管理画面
    onNavigateToMedicalContacts: (String) -> Unit   // 緊急連絡先の管理
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val userEndedFormat = stringResource(R.string.main_snackbar_user_ended)
    val undoLabel = stringResource(R.string.common_undo)
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // 緊急連絡先選択用のボトムシート状態
    val contactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // ViewModelからのイベントを監視 (共通通知イベントを使用)
    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collect { event ->
            when (event) {
                is BaseUiStateViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is BaseUiStateViewModel.UiEvent.ShowSnackbarRes -> {
                    snackbarHostState.showSnackbar(context.getString(event.resId, *event.args.toTypedArray()))
                }
                is BaseUiStateViewModel.UiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseUiStateViewModel.UiEvent.ShowInfoDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                is BaseUiStateViewModel.UiEvent.ShowErrorDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                }
                is BaseUiStateViewModel.UiEvent.ShowErrorDialogRes -> {
                    dialogTitle = context.getString(event.titleResId)
                    dialogMessage = context.getString(event.messageResId, *event.args.toTypedArray())
                }
                else -> {}
            }
        }
    }

    //-- ui/screens/main/MainScreenContent.kt
    MainScreenContent(
        userList = uiState.userList,
        isLoading = uiState.isLoading,
        isNameMaskingEnabled = uiState.isNameMaskingEnabled,
        searchQuery = uiState.searchQuery,
        selectedSection = uiState.selectedSection,
        selectedPersonForQuickMenu = uiState.selectedPersonForQuickMenu,
        isQuickActionMenuExpanded = uiState.isQuickActionMenuExpanded,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },                 // 所見メモ検索
        onSectionSelect = { viewModel.setSelectedSection(it) },                 // 五十音カナ検索
        snackbarHostState = snackbarHostState,                                  //
        lazyListState = lazyListState,                                          //
        onUserClick = { person -> selectedPerson = person; showSheet = true },  // 選択された利用者
        onQuickMenuClick = { person -> viewModel.showQuickMenu(person) },
        onEmergencyContactClick = { person -> viewModel.loadEmergencyContacts(person.id) },
        onEmergencyContactManageClick = { person -> onNavigateToMedicalContacts(person.id) },
        onDismissQuickMenu = { viewModel.dismissQuickMenu() },
        onEditUser = { person -> onNavigateToEditPerson(person.id) },           // 利用者情報の編集
        onAddClick = { onNavigateToAddPerson() },                               // 新規利用者登録
        onEndUser = { person ->                                                 // 利用終了
            viewModel.logicalDeletePerson(person)
            scope.launch {
                val fullName = person.getMaskedName(uiState.isNameMaskingEnabled)
                val result = snackbarHostState.showSnackbar(
                    message = userEndedFormat.format(fullName), 
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) { 
                    viewModel.restorePerson(person)
                    lazyListState.animateScrollToItem(0) 
                }
            }
        },
        onNavigateToSettings = onNavigateToSettings
    )

    // 通知ダイアログの表示
    if (dialogMessage != null) {
        AppInfoDialog(
            title = dialogTitle,
            message = dialogMessage!!,
            onDismiss = {
                dialogMessage = null
                dialogTitle = null
            }
        )
    }

    // カテゴリ選択メニュー（下からスライド）
    if (showSheet && selectedPerson != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            //-- ui/components/main/MainComponents.kt
            CategorySelectionSheet(
                personName = selectedPerson!!.getMaskedName(uiState.isNameMaskingEnabled),
                onCategorySelect = { category -> 
                    showSheet = false
                    onNavigateToDetail(selectedPerson!!.id, category) 
                },
                onBatchInputSelect = {
                    showSheet = false
                    onNavigateToBatchInput(selectedPerson!!.id)
                }
            )
        }
    }

    // 緊急連絡先選択メニュー
    if (uiState.emergencyContactsForSheet != null && uiState.selectedPersonForQuickMenu != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearEmergencyContactState() },
            sheetState = contactSheetState
        ) {
            //-- (MainScreen.kt 内部または共通部品)
            EmergencyContactSelectionSheet(
                contacts = uiState.emergencyContactsForSheet!!,
                personName = uiState.selectedPersonForQuickMenu!!.getMaskedName(uiState.isNameMaskingEnabled),
                onContactClick = { contact ->
                    // ダイヤラー起動 (Activity 委譲は後ほど MainActivity 等で実装)
                    val tel = contact.phoneNumber
                    if (!tel.isNullOrBlank()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, "tel:$tel".toUri())
                        context.startActivity(intent)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.medical_msg_no_phone_number))
                        }
                    }
                    viewModel.clearEmergencyContactState()
                },
                onManageClick = {
                    onNavigateToMedicalContacts(uiState.selectedPersonForQuickMenu!!.id)
                    viewModel.clearEmergencyContactState()
                }
            )
        }
    }
}

/**
 * 緊急連絡先選択用シート
 */
@Composable
fun EmergencyContactSelectionSheet(
    contacts: List<EmergencyContact>,
    personName: String,
    onContactClick: (EmergencyContact) -> Unit,
    onManageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.medical_contacts_title_format, personName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        contacts.forEach { contact ->
            ListItem(
                headlineContent = { Text(contact.facilityName) },
                supportingContent = {
                    val formattedPhone = EmergencyContactMapping.formatPhoneNumber(contact.phoneNumber)
                    val details = listOfNotNull(contact.personName, formattedPhone).joinToString(" / ")
                    if (details.isNotBlank()) Text(details)
                },
                leadingContent = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                modifier = Modifier.clickable { onContactClick(contact) }
            )
            HorizontalDivider()
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onManageClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.medical_contacts_manage_label))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
