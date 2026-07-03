package jp.mydns.fujiwara.carememo.ui.screens.detail

/**
 * Screen : PersonConditionScreenContent
 *
 * 【画面名】
 * 利用者所見記録画面（共通コンテンツ）
 *
 * 【役割】
 * 所見記録（カテゴリB）のコアとなるUI機能（テキスト入力、履歴表示、写真連携等）を一括して管理する。
 *
 * 【主な機能】
 * ・所見入力フォーム：タイトル、内容、記録者名の入力。
 * ・写真連携機能：撮影または選択された写真のサムネイル表示と管理。
 * ・履歴リスト：HistoryItemBコンポーネントを用いた時系列表示。
 * ・表示モード切替：閲覧モードと編集・新規登録モードの制御。
 *
 * 【備考】
 * Phone版とTablet版で共有されるロジックとUIコンポーネントを含み、一貫した入力規則を保証する。
 */

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.components.ConditionDetailPane
import jp.mydns.fujiwara.carememo.ui.components.ObservationList
import jp.mydns.fujiwara.carememo.ui.components.SearchBox
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel

@Composable
fun PersonConditionScreenContent(
    isExpanded: Boolean,
    personId: Int,
    records: List<Any>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedId: Int,
    onSelectedIdChange: (Int) -> Unit,
    conditionPhotoMap: Map<Int, Boolean>,
    onDeleteRecord: (HistoryRecord) -> Unit,
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    onNavigateToPhotoPreview: (Uri, Int, Int) -> Unit,
    onNavigateToFullScreen: (String, String?) -> Unit,
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else if (isExpanded) {
        // タブレット用レイアウト (2ペイン)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBox(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange
                )
                Box(modifier = Modifier.weight(1f)) {
                    ObservationList(
                        records = records,
                        selectedId = selectedId,
                        conditionPhotoMap = conditionPhotoMap,
                        onSelect = onSelectedIdChange,
                        onDelete = onDeleteRecord
                    )
                }
            }
            Box(modifier = Modifier.weight(2f)) {
                ConditionDetailPane(
                    viewModel = viewModel,
                    conditionViewModel = conditionViewModel,
                    personId = personId,
                    conditionId = selectedId,
                    onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                    onNavigateToFullScreen = onNavigateToFullScreen
                )
            }
        }
    } else {
        // スマホ用レイアウト (切り替え)
        if (selectedId != -1) {
            BackHandler { onSelectedIdChange(-1) }
            ConditionDetailPane(
                viewModel = viewModel,
                conditionViewModel = conditionViewModel,
                personId = personId,
                conditionId = selectedId,
                onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                onNavigateToFullScreen = onNavigateToFullScreen
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBox(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange
                )
                Box(modifier = Modifier.weight(1f)) {
                    ObservationList(
                        records = records,
                        selectedId = selectedId,
                        conditionPhotoMap = conditionPhotoMap,
                        onSelect = onSelectedIdChange,
                        onDelete = onDeleteRecord
                    )
                }
            }
        }
    }
}
