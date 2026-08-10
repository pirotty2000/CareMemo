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
import androidx.navigation.NavHostController
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.EmergencyContact
import jp.mydns.fujiwara.carememo.ui.components.base.AppDeleteConfirmDialog
import jp.mydns.fujiwara.carememo.ui.components.base.VerticalScrollIndicator
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.mapping.EmergencyContactMapping
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactEditViewModel
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactUiState
import jp.mydns.fujiwara.carememo.viewmodel.EmergencyContactViewEvent
import kotlinx.collections.immutable.persistentListOf

/**
 * 緊急連絡先一覧画面 (SCR-M-003)
 */
@Composable
fun EmergencyContactListScreen(
    viewModel: EmergencyContactEditViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is EmergencyContactViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
                else -> {}
            }
        }
    }

    EmergencyContactListContent(
        uiState = uiState,
        onNavigateBack = { navController.popBackStack() },
        onAddClick = {
            viewModel.startAdd()
            navController.navigate(Destination.MedicalContactEdit(uiState.personId, null))
        },
        onEditClick = { contact ->
            viewModel.startEdit(contact)
            navController.navigate(Destination.MedicalContactEdit(uiState.personId, contact.id))
        },
        onDeleteConfirm = { contact ->
            viewModel.deleteContact(contact)
        },
        modifier = modifier,
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
    onDeleteConfirm: (EmergencyContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }
    val lazyListState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
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
                VerticalScrollIndicator(lazyListState = lazyListState)
            }
        }
    }

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
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier,
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
                    Icon(
                        imageVector = Icons.Rounded.ModeEdit,
                        contentDescription = stringResource(R.string.main_desc_op_menu)
                    )
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

@Preview(showBackground = true)
@Composable
fun EmergencyContactListContentPreview_Normal() {
    CareMemoTheme {
        EmergencyContactListContent(
            uiState = EmergencyContactUiState(
                personName = "愛 植○",
                contacts = persistentListOf(
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
