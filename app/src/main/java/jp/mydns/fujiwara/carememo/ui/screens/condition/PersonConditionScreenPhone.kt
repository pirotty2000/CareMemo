package jp.mydns.fujiwara.carememo.ui.screens.condition

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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.ui.components.base.appTopAppBarColors
import jp.mydns.fujiwara.carememo.ui.components.common.CategorySelectorBar
import jp.mydns.fujiwara.carememo.ui.components.common.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.ui.theme.CareMemoTheme
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonConditionScreenPhone(
    personId: Int,
    currentPerson: Person?,
    isNameMaskingEnabled: Boolean,
    personCategorySummary: PersonCategorySummary?,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    conditionPhotoMap: Map<Int, Boolean>,
    photos: List<ConditionPhoto>,
    isProcessing: Boolean,
    isAnyDialogOpen: Boolean,
    defaultRecorderName: String,
    selectedId: Int,
    onSelectedIdChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onAddPhotoClick: () -> Unit,
    onNavigateToFullScreen: (Int, Int) -> Unit,
    onShowPdfSettings: () -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onSaveRecord: (ConditionAtVisit, (Int) -> Unit) -> Unit,
    onDeletePhoto: (ConditionPhoto) -> Unit,
    onMicClick: () -> Unit,
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
                    colors = appTopAppBarColors(),
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
                onSearchQueryChange = onSearchQueryChange,
                selectedId = selectedId,
                onSelectedIdChange = { id -> 
                    onSelectedIdChange(id ?: -1)
                },
                conditionPhotoMap = conditionPhotoMap,
                photos = photos,
                isProcessing = isProcessing,
                isAnyDialogOpen = isAnyDialogOpen,
                defaultRecorderName = defaultRecorderName,
                onDeleteRecord = onDeleteRecord,
                onSaveRecord = onSaveRecord,
                onDeletePhoto = onDeletePhoto,
                onAddPhotoClick = onAddPhotoClick,
                onNavigateToFullScreen = onNavigateToFullScreen,
                onMicClick = onMicClick
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun PersonConditionScreenPhonePreview() {
    CareMemoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PersonConditionScreenPhone(
                personId = 1,
                currentPerson = Person(
                    lastName = "山田",
                    firstName = "太郎",
                    lastNameFurigana = "ヤマダ",
                    firstNameFurigana = "タロウ",
                    birthday = Instant.now()
                ),
                isNameMaskingEnabled = false,
                personCategorySummary = null,
                records = listOf(
                    ConditionAtVisit(
                        id = 1,
                        personId = 1,
                        title = "サンプルタイトル",
                        condition = "サンプルの所見内容です。",
                        author = "記録者A",
                        recordTime = Instant.now()
                    )
                ),
                isLoading = false,
                searchQuery = "",
                onSearchQueryChange = {},
                conditionPhotoMap = emptyMap(),
                photos = emptyList(),
                isProcessing = false,
                isAnyDialogOpen = false,
                defaultRecorderName = "記録者",
                selectedId = -1,
                onSelectedIdChange = {},
                onBack = {},
                onNavigateToCategory = {},
                onAddPhotoClick = {},
                onNavigateToFullScreen = { _, _ -> },
                onShowPdfSettings = {},
                onDeleteRecord = {},
                onSaveRecord = { _, _ -> },
                onDeletePhoto = {},
                onMicClick = {},
                snackbarHostState = remember { SnackbarHostState() }
            )
        }
    }
}
