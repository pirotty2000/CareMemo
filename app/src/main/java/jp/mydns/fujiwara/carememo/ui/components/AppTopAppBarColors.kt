package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/**
 * アプリ共通の TopAppBar 配色設定
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)
