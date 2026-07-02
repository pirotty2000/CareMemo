package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonMedicationScreenTablet
 *
 * 【画面名】
 * 利用者服薬記録画面（タブレット版）
 *
 * 【役割】
 * タブレットや折りたたみデバイスなどの横長画面（Expandedクラス）に最適化された服薬記録UIを提供する。
 *
 * 【主な機能】
 * ・2ペインレイアウト：左側に履歴、右側に入力フォームや統計情報を配置し、大画面を有効活用。
 * ・固定ナビゲーション：画面遷移を抑えた効率的な操作感。
 * ・同時表示：リストと詳細を同時に閲覧できるため、過去の記録を参照しながらの入力が容易。
 *
 * 【遷移】
 * ← PersonMedicationScreen（呼び出し元）
 *
 * 【備考】
 * 広い画面スペースを活かし、情報の視認性と操作効率を最大化するレイアウトを採用している。
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.*
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonMedicationViewModel
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonMedicationScreenTablet(
    viewModel: PersonDetailViewModel,
    medicationViewModel: PersonMedicationViewModel,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    isLoading: Boolean,
    selectedMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    personCategorySummary: jp.mydns.fujiwara.carememo.data.PersonCategorySummary?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PersonHeaderTitle(
                            person = currentPerson,
                            isNameMaskingEnabled = isNameMaskingEnabled,
                            defaultTitle = "服薬管理"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = onShowPdfSettings) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力")
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = Category.MEDICATION,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = { category ->
                        if (category != Category.MEDICATION) {
                            onNavigateToCategory(category)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PersonMedicationScreenContent(
                isExpanded = true,
                selectedMonth = selectedMonth,
                isLoading = isLoading,
                recordsByDate = recordsByDate,
                isHistoryMode = false, // Tabletでは使用しない（両方表示するため）
                onHistoryModeChange = {},
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDayClick,
                viewModel = viewModel,
                medicationViewModel = medicationViewModel
            )
        }
    }
}
