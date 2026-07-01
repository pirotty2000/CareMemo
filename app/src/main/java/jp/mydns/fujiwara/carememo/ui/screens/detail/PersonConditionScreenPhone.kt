package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonConditionScreenPhone
 *
 * 【画面名】
 * 利用者所見記録画面（スマートフォン版）
 *
 * 【役割】
 * スマートフォンなどの縦長画面に最適化された、所見記録（カテゴリB）のUIを提供する。
 *
 * 【主な機能】
 * ・シングルカラムレイアウト：所見リストと、詳細入力・写真表示を順次切り替えて表示。
 * ・検索・フィルタ：リスト上部でのフリーワード検索機能。
 * ・モバイル向けUI：ボトムシートやフル画面ダイアログを活用した記録・編集操作。
 *
 * 【遷移】
 * ← PersonConditionScreen（呼び出し元）
 *
 * 【備考】
 * 画面が狭い環境でも、履歴の閲覧と新規情報の登録がスムーズに行えるよう、視認性の高いUIを構成している。
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
fun PersonConditionScreenPhone(
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
                        IconButton(onClick = {
                            if (selectedId != -1) {
                                onSelectedIdChange(-1)
                            } else {
                                onBack()
                            }
                        }) {
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
                        if (selectedId == -1) {
                            IconButton(onClick = onShowPdfSettings) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = "PDF出力")
                            }
                        }
                    }
                )
                if (selectedId == -1) {
                    CategorySelectorBar(
                        currentCategory = Category.CONDITION_AT_VISIT,
                        personCategorySummary = personCategorySummary,
                        onCategoryClick = onNavigateToCategory
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedId == -1) {
                FloatingActionButton(onClick = { onSelectedIdChange(0) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新規追加")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PersonConditionScreenContent(
                isExpanded = false,
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
