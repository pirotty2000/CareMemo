package jp.mydns.fujiwara.carememo.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactEditViewModel
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState

/**
 * 緊急連絡先一覧画面 (SCR-M-003)
 * ViewModel との接続を担当する Stateful な Composable。
 */
@Composable
fun EmergencyContactListScreen(
    viewModel: EmergencyContactEditViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EmergencyContactListContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAddClick = {
            viewModel.startAdd()
            onNavigateToAdd()
        },
        onEditClick = { contact ->
            viewModel.startEdit(contact)
            onNavigateToEdit(contact.id)
        },
        onDeleteConfirm = { contact ->
            viewModel.deleteContact(contact)
        }
    )
}

/**
 * 緊急連絡先一覧のレイアウト本体 (Stateless)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactListContent(
    uiState: EmergencyContactUiState,
    onNavigateBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (EmergencyContact) -> Unit,
    onDeleteConfirm: (EmergencyContact) -> Unit
) {
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(stringResource(R.string.medical_contacts_manage_title_format, uiState.personName)) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = appTopAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.testTag("MedicalContactList_AddButton")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_add))
            }
        }
    ) { padding ->
        if (uiState.contacts.isEmpty()) {
            // 空の状態
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(stringResource(R.string.medical_contacts_empty))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("MedicalContactList"),
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(uiState.contacts, key = { it.id }) { contact ->
                        EmergencyContactItem(
                            contact = contact,
                            onEditClick = { onEditClick(contact) },
                            onDeleteClick = { contactToDelete = contact }
                        )
                        HorizontalDivider()
                    }
                }
                
                // 垂直スクロールインジケーター
                VerticalScrollIndicator(lazyListState = lazyListState)
            }
        }
    }

    // 削除確認ダイアログ
    if (contactToDelete != null) {
        AppDeleteConfirmDialog(
            title = stringResource(R.string.medical_contact_delete_confirm_title),
            message = stringResource(R.string.medical_contact_delete_confirm_msg, contactToDelete!!.facilityName),
            onDelete = {
                onDeleteConfirm(contactToDelete!!)
                contactToDelete = null
            },
            onDismiss = { contactToDelete = null }
        )
    }
}

@Composable
fun EmergencyContactItem(
    contact: EmergencyContact,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { 
            Text(contact.facilityName, fontWeight = FontWeight.Bold) 
        },
        supportingContent = {
            Column {
                contact.personName?.let { if (it.isNotBlank()) Text(it) }
                EmergencyContactMapping.formatPhoneNumber(contact.phoneNumber)?.let { Text(it) }
            }
        },
        leadingContent = {
            Icon(
                imageVector = EmergencyContactMapping.getIcon(contact.contactType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.ModeEdit, contentDescription = "操作メニュー")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.ModeEdit, contentDescription = null) },
                        onClick = { showMenu = false; onEditClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDeleteClick() }
                    )
                }
            }
        }
    )
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun EmergencyContactListContentPreview_Normal() {
    CareMemoTheme {
        EmergencyContactListContent(
            uiState = EmergencyContactUiState(
                personName = "愛 植○",
                contacts = listOf(
                    EmergencyContact(facilityName = "○○クリニック", personName = "○○先生", phoneNumber = "0311111111", contactType = "DOCTOR", personId = "1"),
                    EmergencyContact(facilityName = "長男の妻", personName = "○○さん", phoneNumber = "08011111111", contactType = "FAMILY", personId = "1", priority = 1)
                )
            ),
            onNavigateBack = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteConfirm = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmergencyContactListContentPreview_Empty() {
    CareMemoTheme {
        EmergencyContactListContent(
            uiState = EmergencyContactUiState(
                personName = "愛 植○",
                contacts = emptyList()
            ),
            onNavigateBack = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteConfirm = {}
        )
    }
}
