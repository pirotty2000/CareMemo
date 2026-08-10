package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary

/**
 * Component：CategorySelectorBar
 *
 * 【役割】
 * 利用者詳細画面において、記録カテゴリ（健康記録、所見メモ、服薬管理など）を切り替えるための水平ナビゲーションバーを提供します。
 *
 * 【主な機能】
 * ・カテゴリ一覧（Category.entries）の水平スクロール表示。
 * ・現在選択されているカテゴリの強調表示と、カテゴリ切り替え時の自動スクロール。
 * ・各カテゴリに記録データが存在するかどうかを示す視覚的フィードバック（ボーダーの強調）。
 *
 * 【想定する利用場所】
 * 各利用者詳細画面（PersonHealthScreen, PersonConditionScreen, PersonMedicationScreen 等）。
 *
 * 【このコンポーネントでは行わないこと】
 * 実際の画面遷移の実行（クリックイベントを親コンポーネントに通知するのみ）。
 *
 * @param currentCategory 現在選択されているカテゴリ
 * @param personCategorySummary カテゴリごとのデータ存在有無を示すサマリーデータ
 * @param onCategoryClick カテゴリがクリックされた際のコールバック
 * @param modifier 修飾子
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorBar(
    currentCategory: Category,
    personCategorySummary: PersonCategorySummary?,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryListState = rememberLazyListState()

    // 画面表示時またはカテゴリ変更時、選択されているアイテムが見える位置まで自動スクロール
    LaunchedEffect(currentCategory) {
        val index = Category.entries.indexOf(currentCategory)
        if (index >= 0) {
            categoryListState.animateScrollToItem(index)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("CategorySelectorBar"),
            state = categoryListState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(Category.entries) { _, category ->
            // カテゴリごとのデータ存在確認
            val hasData = when (category) {
                Category.HEIGHT_AND_WEIGHT -> personCategorySummary?.hasHeightWeight == true
                Category.BP_AND_PULSE -> personCategorySummary?.hasBpAndPulse == true
                Category.GLUCOSE_AND_HBA1C -> personCategorySummary?.hasGlucoseAndHbA1c == true
                Category.CONDITION_AT_VISIT -> personCategorySummary?.hasCondition == true
                Category.MEDICATION -> personCategorySummary?.hasMedication == true
            }

            FilterChip(
                selected = currentCategory == category,
                onClick = { onCategoryClick(category) },
                label = { Text(stringResource(category.displayNameRes)) },
                modifier = Modifier.testTag("CategoryChip_${category.name}"),
                leadingIcon = if (currentCategory == category) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentCategory == category,
                    // データがある場合はボーダーを強調し、入力漏れ防止や入力済み確認を補助する
                    borderColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = if (hasData) 1.5.dp else 1.0.dp,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
}
