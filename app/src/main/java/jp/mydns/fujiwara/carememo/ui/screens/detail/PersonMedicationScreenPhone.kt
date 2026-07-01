package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonMedicationScreenPhone
 *
 * 【画面名】
 * 利用者服薬記録画面（スマートフォン版）
 *
 * 【役割】
 * スマートフォンなどの縦長画面（Compact/Mediumクラス）に最適化された服薬記録UIを提供する。
 *
 * 【主な機能】
 * ・モバイル向けレイアウト：単一カラムでの履歴表示と、ボトムシート等を用いた入力UI。
 * ・履歴表示：カレンダーとリストの切り替え、月間ナビゲーション。
 * ・PDF出力ボタンの配置：トップバーへのアクション配置。
 *
 * 【遷移】
 * ← PersonMedicationScreen（呼び出し元）
 *
 * 【備考】
 * 画面幅が制限されているため、情報の密度を調整し、スクロールやシートによる操作を主眼に置いている。
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
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonMedicationScreenPhone(
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    isLoading: Boolean,
    selectedMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    personCategorySummary: jp.mydns.fujiwara.carememo.data.PersonCategorySummary?,
    isHistoryMode: Boolean,
    onHistoryModeChange: (Boolean) -> Unit,
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
                isExpanded = false,
                selectedMonth = selectedMonth,
                isLoading = isLoading,
                recordsByDate = recordsByDate,
                isHistoryMode = isHistoryMode,
                onHistoryModeChange = onHistoryModeChange,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onDayClick
            )
        }
    }
}
