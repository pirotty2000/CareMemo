package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonConditionScreenTablet
 *
 * 【画面名】
 * 利用者所見記録画面（タブレット版）
 *
 * 【役割】
 * タブレットや大画面デバイスに最適化された、所見記録（カテゴリB）のUIを提供し、効率的な情報管理を実現する。
 *
 * 【主な機能】
 * ・2ペインレイアウト：左側に所見履歴リスト、右側に選択中の詳細内容と写真・入力フォームを表示。
 * ・マルチビュー操作：過去の記録を参照しながら、新しい所見の入力や写真の確認が同時に可能。
 * ・クイックアクセス：画面遷移なしでリストから詳細へ素早くアクセスできる。
 *
 * 【遷移】
 * ← PersonConditionScreen（呼び出し元）
 *
 * 【備考】
 * 広い画面を活用し、一度に多くの情報を提示することで、利用者の状態変化を俯瞰的に把握しやすくしている。
 */

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenTablet(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    personId: Int,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    conditionPhotoMap: Map<Int, Boolean>,
    selectedId: Int,
    onSelectedIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    snackbarHostState: SnackbarHostState,
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
                            defaultTitle = "所見記録"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { onSelectedIdChange(0) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                        }
                        IconButton(onClick = onShowPdfSettings) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力")
                        }
                    }
                )
                CategorySelectorBar(
                    currentCategory = Category.CONDITION_AT_VISIT,
                    personCategorySummary = personCategorySummary,
                    onCategoryClick = onNavigateToCategory
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PersonConditionScreenContent(
                isExpanded = true,
                personId = personId,
                records = records,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchQueryChange = { conditionViewModel.updateSearchQuery(it) },
                selectedId = selectedId,
                onSelectedIdChange = onSelectedIdChange,
                conditionPhotoMap = conditionPhotoMap,
                onDeleteRecord = onDeleteRecord,
                viewModel = viewModel,
                conditionViewModel = conditionViewModel,
                onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                onNavigateToFullScreen = onNavigateToFullScreen
            )
        }
    }
}
