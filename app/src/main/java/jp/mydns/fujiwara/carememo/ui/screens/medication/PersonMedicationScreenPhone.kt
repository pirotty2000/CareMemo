package jp.mydns.fujiwara.carememo.ui.screens.medication

/**
 * Screen : PersonMedicationScreenPhone
 *
 * 【画面名】：
 * 利用者服薬記録画面（スマートフォン版）
 *
 * 【役割】：
 * スマートフォンなどの縦長画面（Compact/Mediumクラス）に最適化された服薬記録UIを提供する。
 *
 * 【主な機能】：
 * ・モバイル向けレイアウト：カレンダー表示と履歴表示をタブ切り替え。
 * ・月間ナビゲーション：前月・次月への移動と、現在の年月の表示。
 * ・アクション統合：トップバーからのPDF出力や、日付タップによる入力ダイアログ起動。
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
 * ・base/AppTopAppBarColors.kt
 *
 * 【備考】：
 * このコンポーネント自体は状態を持たず、UIの構造定義と親画面へのイベント伝達に特化している。
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
fun PersonMedicationScreenPhone(
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    isLoading: Boolean,
    selectedMonth: YearMonth,
    recordsByDate: Map<String, List<MedicationRecord>>,
    personCategorySummary: PersonCategorySummary?,
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
                            defaultTitle = "服薬管理",
                            modifier = Modifier.testTag("PersonHeader")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("Medication_BackButton")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    colors = appTopAppBarColors(),
                    actions = {
                        IconButton(
                            onClick = onShowPdfSettings,
                            modifier = Modifier.testTag("Medication_PdfButton")
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

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun PersonMedicationScreenPhonePreview() {
    CareMemoTheme {
        PersonMedicationScreenPhone(
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
            isHistoryMode = false,
            onHistoryModeChange = {},
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
