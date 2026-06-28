package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary

/**
 * 全詳細画面共通のカテゴリ選択バー
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

    // 選択されているカテゴリまで自動スクロール
    LaunchedEffect(currentCategory) {
        val index = Category.entries.indexOf(currentCategory)
        if (index >= 0) {
            categoryListState.animateScrollToItem(index)
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp),
        state = categoryListState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(Category.entries) { _, category ->
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
                leadingIcon = if (currentCategory == category) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentCategory == category,
                    borderColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = if (hasData) 1.5.dp else 1.0.dp,
                    selectedBorderColor = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
