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
 * 【遷移】：
 * ViewModel から発行される ViewEvent (PersonListViewEvent) に基づき、
 * Composable 側で NavHostController を操作して遷移を行う。
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.logic.feature.PersonListViewEvent
import jp.mydns.fujiwara.carememo.ui.components.base.AppInfoDialog
import jp.mydns.fujiwara.carememo.ui.components.main.CategorySelectionSheet
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.navigation.EditResult
import jp.mydns.fujiwara.carememo.ui.navigation.NavigationKeys
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonListViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * 利用者一覧画面のメインエントランス。
 * ViewModelとの接続、UI状態の監視、ダイアログやボトムシートの表示制御を行う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PersonListViewModel,
    navController: NavHostController
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

    // ナビゲーションの結果（SavedStateHandle）を監視
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val editResultFlow = remember(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle?.getStateFlow<String?>(NavigationKeys.PERSON_EDIT_RESULT, null)
    }
    val editResult by editResultFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    LaunchedEffect(editResult) {
        editResult?.let { result ->
            val messageRes = if (result == EditResult.ADDED.name) {
                R.string.main_msg_user_added
            } else {
                R.string.main_msg_user_updated
            }
            snackbarHostState.showSnackbar(context.getString(messageRes))
            // 通知を消費
            navBackStackEntry?.savedStateHandle?.remove<String>(NavigationKeys.PERSON_EDIT_RESULT)
        }
    }

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

    // ViewModelからの画面遷移イベントを監視 (Type-safe)
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is PersonListViewEvent.NavigateToDetail -> {
                    navController.navigate(event.category.toDestination(event.personId, uiState.searchQuery))
                }
                is PersonListViewEvent.NavigateToBatchInput -> {
                    navController.navigate(Destination.BatchInput(event.personId))
                }
                is PersonListViewEvent.NavigateToAddPerson -> {
                    navController.navigate(Destination.PersonEdit(null))
                }
                is PersonListViewEvent.NavigateToEditPerson -> {
                    navController.navigate(Destination.PersonEdit(event.personId))
                }
                is PersonListViewEvent.NavigateToSettings -> {
                    navController.navigate(Destination.Settings)
                }
                is PersonListViewEvent.NavigateToMedicalContacts -> {
                    navController.navigate(Destination.MedicalContacts(event.personId))
                }
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
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSectionSelect = { viewModel.setSelectedSection(it) },
        snackbarHostState = snackbarHostState,
        lazyListState = lazyListState,
        onUserClick = { person -> selectedPerson = person; showSheet = true },
        onQuickMenuClick = { person -> viewModel.showQuickMenu(person) },
        onEmergencyContactClick = { person -> viewModel.loadEmergencyContacts(person.id) },
        onEmergencyContactManageClick = { person -> viewModel.navigateToMedicalContacts(person.id) },
        onDismissQuickMenu = { viewModel.dismissQuickMenu() },
        onEditUser = { person -> viewModel.navigateToEditPerson(person.id) },
        onAddClick = { viewModel.navigateToAddPerson() },
        onEndUser = { person ->
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
        onNavigateToSettings = { viewModel.navigateToSettings() }
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
                    viewModel.navigateToDetail(selectedPerson!!.id, category)
                },
                onBatchInputSelect = {
                    showSheet = false
                    viewModel.navigateToBatchInput(selectedPerson!!.id)
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
                    viewModel.navigateToMedicalContacts(uiState.selectedPersonForQuickMenu!!.id)
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
    contacts: ImmutableList<EmergencyContact>,
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
