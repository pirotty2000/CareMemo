package jp.mydns.fujiwara.carememo.ui.screens.medication

/**
 * Screen : PersonMedicationScreenTablet
 *
 * 【画面名】：
 * 利用者服薬記録画面（タブレット版）
 *
 * 【役割】：
 * タブレットや横長画面（Expandedクラス）に最適化された服薬記録UIを提供する。
 *
 * 【主な機能】：
 * ・2カラムレイアウト：左側にカレンダー、右側に月間履歴テーブルを常時表示。
 * ・広い操作エリア：カレンダーからの素早い日付選択と、月間状況の俯瞰を両立。
 * ・アクション統合：大画面を活かした固定トップバーとナビゲーション。
 *
 * 【遷移】：
 * ← PersonMedicationScreen (親コンテナ)
 * → PersonMedicationScreenContent (共通コンテンツの呼び出し)
 *
 * 【使用するViewModel】：
 * なし（Stateless化済み。親から状態とラムダを受け取る）
 *
 * 【使用するComponents】：
 * ・screens/detail/medication/PersonMedicationScreenContent.kt
 * ・detail/common/CategorySelectorBar.kt
 * ・detail/common/PersonHeaderTitle.kt
 *
 * 【備考】：
 * 広い画面スペースを活かし、情報の視認性と操作効率を最大化するレイアウトを採用している。
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonMedicationScreenTablet(
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    isLoading: Boolean,
    selectedMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    personCategorySummary: PersonCategorySummary?,
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
                            defaultTitle = "服薬管理",
                            modifier = Modifier.testTag("PersonHeader")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("MedicationScreen_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(
                            onClick = onShowPdfSettings,
                            modifier = Modifier.testTag("MedicationScreen_PdfButton")
                        ) {
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
                .padding(horizontal = 16.dp)
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
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun PersonMedicationScreenTabletPreview() {
    CareMemoTheme {
        PersonMedicationScreenTablet(
            currentPerson = Person(
                lastName = "山田",
                firstName = "太郎",
                lastNameFurigana = "ヤマダ",
                firstNameFurigana = "タロウ",
                birthday = Instant.now()
            ),
            isNameMaskingEnabled = false,
            isLoading = false,
            selectedMonth = YearMonth.now(),
            recordsByDate = emptyMap(),
            personCategorySummary = null,
            onPreviousMonth = {},
            onNextMonth = {},
            onBack = {},
            onNavigateToCategory = {},
            onShowPdfSettings = {},
            onDayClick = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
