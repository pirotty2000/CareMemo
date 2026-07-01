package jp.mydns.fujiwara.carememo.ui.screens.detail.sub

/**
 * Screen : ConditionPhotoPreviewScreen
 *
 * 【画面名】
 * 写真撮影・選択プレビュー画面
 *
 * 【役割】
 * カメラで撮影した直後、またはギャラリーから選択した直後の写真をプレビューし、
 * 所見メモへの保存を確定させる、あるいは再撮影・再選択を選択するための画面。
 *
 * 【主な機能】
 * ・即時プレビュー：取り込まれた画像の構図やピントの最終確認。
 * ・決定/キャンセル操作：写真の採用・不採用の選択。
 *
 * 【遷移】
 * ← PersonConditionScreen（写真取得後に自動遷移）
 *
 * 【備考】
 * 誤った写真を保存することを防ぐための確認ステップとして機能する。
 */

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.mydns.fujiwara.carememo.ui.components.PersonHeaderTitle
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import jp.mydns.fujiwara.carememo.viewmodel.PersonDetailViewModel
import jp.mydns.fujiwara.carememo.viewmodel.PersonConditionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionPhotoPreviewScreen(
    viewModel: PersonDetailViewModel,
    conditionViewModel: PersonConditionViewModel,
    uri: Uri,
    personId: Int,
    conditionId: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val isProcessing by conditionViewModel.isProcessing.collectAsState()
    val currentPerson by viewModel.currentPerson.collectAsState()
    val isNameMaskingEnabled by viewModel.isNameMaskingEnabled.collectAsState()

    // キャプションの初期値を現在の日時に設定
    var caption by remember { 
        mutableStateOf(DateTimeUtils.getCurrentPhotoCaption())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PersonHeaderTitle(
                        person = currentPerson,
                        isNameMaskingEnabled = isNameMaskingEnabled,
                        defaultTitle = "写真の確認"
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(enable = true)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build(),
                contentDescription = "プレビュー",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("キャプション") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isProcessing
            )

            if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("画像を保存用に最適化しています...")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            conditionViewModel.deleteTempFile(context, uri)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            conditionViewModel.processAndSavePhoto(context, uri, personId, conditionId, caption)
                            onSaved()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存する")
                    }
                }
            }
        }
    }
}
